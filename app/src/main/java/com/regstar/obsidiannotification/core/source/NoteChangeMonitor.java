package com.regstar.obsidiannotification.core.source;

import com.regstar.obsidiannotification.core.reminders.*;
import com.regstar.obsidiannotification.core.tasks.*;
import com.regstar.obsidiannotification.prefs.*;
import com.regstar.obsidiannotification.support.ErrorLog;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

/**
 * Periodically re-reads the active markdown source and reconciles alarms with
 * the current task list.
 *
 * <p>The monitor also guards against partially synced or temporarily empty
 * files so transient external sync states do not immediately wipe local
 * reminders.</p>
 */
public final class NoteChangeMonitor {
    public static final String ACTION_SYNC_NOTE =
            "com.regstar.obsidiannotification.action.SYNC_NOTE";

    private static final String PREFS_NAME = "obsidian_notification_note_monitor";
    private static final String KEY_LAST_FINGERPRINT = "last_fingerprint";
    private static final String KEY_LAST_SYNC_AT = "last_sync_at";
    private static final String KEY_LAST_ERROR = "last_error";
    private static final String KEY_EMPTY_READ_COUNT = "empty_read_count";
    private static final long BACKGROUND_CHECK_INTERVAL_MS = 60_000L;
    private static final int REQUEST_SYNC_NOTE = 2001;
    private static final int MAX_EMPTY_READ_CHARACTERS = 0;
    private static final int MAX_PROTECTED_EMPTY_READS = 4;

    private NoteChangeMonitor() {
    }

    public static void ensureScheduled(Context context) {
        if (!TaskSourceManager.hasReadableSource(context)) {
            cancel(context);
            return;
        }

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }

        PendingIntent pendingIntent = createSyncPendingIntent(context, PendingIntent.FLAG_UPDATE_CURRENT);
        alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + BACKGROUND_CHECK_INTERVAL_MS,
                pendingIntent
        );
    }

    public static void cancel(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pendingIntent = createSyncPendingIntent(context, PendingIntent.FLAG_NO_CREATE);
        if (pendingIntent != null) {
            if (alarmManager != null) {
                alarmManager.cancel(pendingIntent);
            }
            pendingIntent.cancel();
        }
    }

    public static NoteSyncResult syncNow(Context context) {
        return syncNow(context, false);
    }

    public static NoteSyncResult syncNow(Context context, boolean forceReschedule) {
        try {
            NoteStore.TaskSnapshot snapshot = NoteStore.readTaskSnapshot(context);
            TaskParseResult parseResult = snapshot.getParseResult();
            if (isSuspiciousPartialRead(context, snapshot)) {
                String message = "РСЃС‚РѕС‡РЅРёРє РїСЂРѕС‡РёС‚Р°РЅ РєР°Рє РїСѓСЃС‚РѕР№. Р’РѕР·РјРѕР¶РЅРѕ, С„Р°Р№Р» РµС‰Рµ СЃРёРЅС…СЂРѕРЅРёР·РёСЂСѓРµС‚СЃСЏ.";
                recordFailedSync(context, message);
                boolean restored = restoreFromCache(context, message);
                return NoteSyncResult.failure(message, restored);
            }

            if (processExternalRepeatUpdates(context, parseResult)) {
                snapshot = NoteStore.readTaskSnapshot(context);
                parseResult = snapshot.getParseResult();
            }

            List<ObsidianTask> activeTasks = parseResult.getActiveTasks();
            TaskCache.saveActiveTasks(context, activeTasks);
            ReminderSchedule schedule = forceReschedule
                    ? ReminderScheduler.rescheduleAll(context, activeTasks)
                    : ReminderScheduler.schedule(context, activeTasks);
            String fingerprint = fingerprintOf(parseResult);
            String previousFingerprint = getLastFingerprint(context);
            recordSuccessfulSync(context, fingerprint);
            return NoteSyncResult.success(
                    previousFingerprint == null || !previousFingerprint.equals(fingerprint),
                    parseResult.getTasks().size(),
                    schedule
            );
        } catch (IOException | RuntimeException exception) {
            recordFailedSync(context, exception);
            boolean restored = restoreFromCache(context, exception.getMessage());
            return NoteSyncResult.failure(exception.getMessage(), restored);
        }
    }

    private static boolean processExternalRepeatUpdates(
            Context context,
            TaskParseResult parseResult
    ) {
        boolean changed = false;
        LocalDateTime now = LocalDateTime.now();
        for (ObsidianTask task : parseResult.getTasks()) {
            if (!task.hasRepeatSchedule()) {
                continue;
            }

            String seriesId = task.getSeriesId();
            if (!task.isCompleted() && !task.isSkipped()) {
                OccurrenceHistoryStore.removePendingExternalCompletion(context, seriesId);
                continue;
            }

            OccurrenceStatus status = task.isSkipped()
                    ? OccurrenceStatus.SKIPPED
                    : OccurrenceStatus.COMPLETED;
            OccurrenceHistoryStore.PendingExternalCompletion pending =
                    OccurrenceHistoryStore.getPendingExternalCompletion(context, seriesId);
            if (pending == null
                    || !task.getTaskKey().equals(pending.getTaskKey())
                    || !task.getRawLine().equals(pending.getRawLineSnapshot())
                    || task.getReminderAt() == null
                    || !task.getReminderAt().equals(pending.getOccurrenceDueAt())
                    || pending.getCandidateStatus() != status) {
                OccurrenceHistoryStore.putPendingExternalCompletion(
                        context,
                        new OccurrenceHistoryStore.PendingExternalCompletion(
                                seriesId,
                                task.getTaskKey(),
                                task.getRawLine(),
                                status,
                                task.getReminderAt(),
                                now
                        )
                );
                continue;
            }

            if (pending.getDetectedAt().plus(OccurrenceHistoryStore.stabilizationWindow()).isAfter(now)) {
                continue;
            }

            TaskEditResult advanceResult = RepeatSeriesManager.advance(context, task.getTaskKey(), status);
            if (advanceResult.isUpdated()) {
                OccurrenceHistoryStore.removePendingExternalCompletion(context, seriesId);
                changed = true;
            } else if (advanceResult.isFailure()) {
                ErrorLog.record(
                        context,
                        "РќРµ СѓРґР°Р»РѕСЃСЊ РѕР±СЂР°Р±РѕС‚Р°С‚СЊ РІРЅРµС€РЅРµРµ Р·Р°РІРµСЂС€РµРЅРёРµ repeat-Р·Р°РґР°С‡Рё: "
                                + advanceResult.getMessage()
                );
            }
        }
        return changed;
    }

    public static boolean restoreFromCache(Context context, String reason) {
        if (!TaskSourceManager.hasReadableSource(context)) {
            return false;
        }

        List<ObsidianTask> cachedTasks = TaskCache.loadActiveTasks(context);
        if (cachedTasks.isEmpty()) {
            return false;
        }

        try {
            ReminderScheduler.rescheduleAll(context, cachedTasks);
        } catch (RuntimeException exception) {
            ErrorLog.record(context, "РќРµ СѓРґР°Р»РѕСЃСЊ РІРѕСЃСЃС‚Р°РЅРѕРІРёС‚СЊ РЅР°РїРѕРјРёРЅР°РЅРёСЏ РёР· РєСЌС€Р°", exception);
            return false;
        }
        ErrorLog.record(
                context,
                "Р’РѕСЃСЃС‚Р°РЅРѕРІР»РµРЅС‹ РЅР°РїРѕРјРёРЅР°РЅРёСЏ РёР· Р»РѕРєР°Р»СЊРЅРѕРіРѕ РєСЌС€Р°: "
                        + cachedTasks.size()
                        + ". РџСЂРёС‡РёРЅР°: "
                        + safeMessage(reason)
        );
        return true;
    }

    public static String getLastFingerprint(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_LAST_FINGERPRINT, null);
    }

    public static void recordSuccessfulSync(Context context, String fingerprint) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_LAST_FINGERPRINT, fingerprint)
                .putString(KEY_LAST_SYNC_AT, LocalDateTime.now().toString())
                .remove(KEY_EMPTY_READ_COUNT)
                .remove(KEY_LAST_ERROR)
                .apply();
    }

    public static String fingerprintOf(String markdown) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(markdown.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException exception) {
            return String.valueOf(markdown.hashCode());
        }
    }

    public static String fingerprintOf(TaskParseResult parseResult) {
        StringBuilder builder = new StringBuilder();
        for (ObsidianTask task : parseResult.getTasks()) {
            builder.append(task.getTaskKey())
                    .append('|')
                    .append(task.isCompleted())
                    .append('|')
                    .append(task.getRawLine())
                    .append('\n');
        }
        for (TaskParseError error : parseResult.getErrors()) {
            builder.append("error|").append(error.format()).append('\n');
        }
        return fingerprintOf(builder.toString());
    }

    private static void recordFailedSync(Context context, Exception exception) {
        recordFailedSync(context, exception.getMessage());
    }

    private static void recordFailedSync(Context context, String message) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_LAST_SYNC_AT, LocalDateTime.now().toString())
                .putString(KEY_LAST_ERROR, safeMessage(message))
                .apply();
        ErrorLog.record(context, "РћС€РёР±РєР° СЃРёРЅС…СЂРѕРЅРёР·Р°С†РёРё: " + safeMessage(message));
    }

    public static boolean isSuspiciousPartialRead(
            Context context,
            NoteStore.TaskSnapshot snapshot
    ) {
        boolean emptyReadWithCache = snapshot.getTotalCharacters() <= MAX_EMPTY_READ_CHARACTERS
                && snapshot.getParseResult().getTasks().isEmpty()
                && TaskCache.hasCachedTasks(context);
        if (!emptyReadWithCache) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .remove(KEY_EMPTY_READ_COUNT)
                    .apply();
            return false;
        }

        int emptyReadCount = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getInt(KEY_EMPTY_READ_COUNT, 0) + 1;
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_EMPTY_READ_COUNT, emptyReadCount)
                .apply();
        return emptyReadCount <= MAX_PROTECTED_EMPTY_READS;
    }

    private static String safeMessage(String message) {
        if (message == null || message.trim().isEmpty()) {
            return "Р±РµР· РїРѕРґСЂРѕР±РЅРѕСЃС‚РµР№";
        }
        return message;
    }

    private static PendingIntent createSyncPendingIntent(Context context, int flag) {
        Intent intent = new Intent(context, NoteSyncReceiver.class)
                .setAction(ACTION_SYNC_NOTE);
        return PendingIntent.getBroadcast(
                context,
                REQUEST_SYNC_NOTE,
                intent,
                flag | PendingIntent.FLAG_IMMUTABLE
        );
    }

    public static final class NoteSyncResult {
        private final boolean success;
        private final boolean changed;
        private final int taskCount;
        private final ReminderSchedule schedule;
        private final String errorMessage;
        private final boolean restoredFromCache;

        private NoteSyncResult(
                boolean success,
                boolean changed,
                int taskCount,
                ReminderSchedule schedule,
                String errorMessage,
                boolean restoredFromCache
        ) {
            this.success = success;
            this.changed = changed;
            this.taskCount = taskCount;
            this.schedule = schedule;
            this.errorMessage = errorMessage;
            this.restoredFromCache = restoredFromCache;
        }

        public static NoteSyncResult success(
                boolean changed,
                int taskCount,
                ReminderSchedule schedule
        ) {
            return new NoteSyncResult(true, changed, taskCount, schedule, null, false);
        }

        public static NoteSyncResult failure(String errorMessage) {
            return failure(errorMessage, false);
        }

        public static NoteSyncResult failure(String errorMessage, boolean restoredFromCache) {
            return new NoteSyncResult(false, false, 0, null, errorMessage, restoredFromCache);
        }

        public boolean isSuccess() {
            return success;
        }

        public boolean isChanged() {
            return changed;
        }

        public int getTaskCount() {
            return taskCount;
        }

        public ReminderSchedule getSchedule() {
            return schedule;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public boolean isRestoredFromCache() {
            return restoredFromCache;
        }
    }
}
