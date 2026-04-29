package com.regstar.obsidiannotification.core.source;

import com.regstar.obsidiannotification.R;
import com.regstar.obsidiannotification.core.reminders.ReminderSchedule;
import com.regstar.obsidiannotification.core.reminders.ReminderScheduler;
import com.regstar.obsidiannotification.core.tasks.ObsidianTask;
import com.regstar.obsidiannotification.core.tasks.OccurrenceHistoryStore;
import com.regstar.obsidiannotification.core.tasks.OccurrenceStatus;
import com.regstar.obsidiannotification.core.tasks.RepeatSeriesManager;
import com.regstar.obsidiannotification.core.tasks.TaskCache;
import com.regstar.obsidiannotification.core.tasks.TaskEditResult;
import com.regstar.obsidiannotification.core.tasks.TaskOccurrenceRecord;
import com.regstar.obsidiannotification.core.tasks.TaskParseError;
import com.regstar.obsidiannotification.core.tasks.TaskParseResult;
import com.regstar.obsidiannotification.core.tasks.TaskFormatSettings;
import com.regstar.obsidiannotification.support.ErrorLog;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

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
    private static final String KEY_SUSPICIOUS_READ_COUNT = "suspicious_read_count";
    private static final String KEY_LAST_DOCUMENT_COUNT = "last_document_count";
    private static final String KEY_LAST_TASK_COUNT = "last_task_count";
    private static final String KEY_LAST_TOTAL_CHARACTERS = "last_total_characters";
    private static final String KEY_LAST_SOURCE_STATE = "last_source_state";
    private static final long BACKGROUND_CHECK_INTERVAL_MS = 60_000L;
    private static final int REQUEST_SYNC_NOTE = 2001;
    private static final int MAX_EMPTY_READ_CHARACTERS = 0;
    private static final int MAX_PROTECTED_EMPTY_READS = 4;
    private static final Object MONITOR_STATE_LOCK = new Object();

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
                String message = context.getString(R.string.runtime_empty_source_maybe_syncing);
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
            String fingerprint = fingerprintOf(context, parseResult);
            String previousFingerprint = getLastFingerprint(context);
            recordSuccessfulSync(context, fingerprint, snapshot);
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
        TaskFormatSettings formatSettings = TaskFormatSettings.load(context);
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

            TaskEditResult advanceResult = RepeatSeriesManager.advance(
                    context,
                    task.getTaskKey(),
                    status,
                    formatSettings
            );
            if (advanceResult.isUpdated()) {
                OccurrenceHistoryStore.removePendingExternalCompletion(context, seriesId);
                changed = true;
            } else if (advanceResult.isFailure()) {
                ErrorLog.record(
                        context,
                        context.getString(
                                R.string.runtime_external_repeat_complete_error,
                                advanceResult.getMessage()
                        )
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
            ErrorLog.record(context, context.getString(R.string.runtime_restore_cache_error), exception);
            return false;
        }
        ErrorLog.record(
                context,
                context.getString(
                        R.string.runtime_restored_from_cache,
                        cachedTasks.size(),
                        safeMessage(context, reason)
                )
        );
        return true;
    }

    public static String getLastFingerprint(Context context) {
        synchronized (MONITOR_STATE_LOCK) {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .getString(KEY_LAST_FINGERPRINT, null);
        }
    }

    public static void recordSuccessfulSync(Context context, String fingerprint) {
        recordSuccessfulSync(context, fingerprint, null);
    }

    public static void recordSuccessfulSync(Context context, String fingerprint, NoteStore.TaskSnapshot snapshot) {
        int documentCount = snapshot == null ? 0 : snapshot.getDocumentCount();
        int totalCharacters = snapshot == null ? 0 : snapshot.getTotalCharacters();
        int taskCount = snapshot == null || snapshot.getParseResult() == null
                ? 0
                : snapshot.getParseResult().getTasks().size();
        String sourceState = sourceStateIdentity(context);
        synchronized (MONITOR_STATE_LOCK) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_LAST_FINGERPRINT, fingerprint)
                    .putString(KEY_LAST_SYNC_AT, LocalDateTime.now().toString())
                    .putInt(KEY_LAST_DOCUMENT_COUNT, documentCount)
                    .putInt(KEY_LAST_TASK_COUNT, taskCount)
                    .putInt(KEY_LAST_TOTAL_CHARACTERS, totalCharacters)
                    .putString(KEY_LAST_SOURCE_STATE, sourceState)
                    .remove(KEY_EMPTY_READ_COUNT)
                    .remove(KEY_SUSPICIOUS_READ_COUNT)
                    .remove(KEY_LAST_ERROR)
                    .apply();
        }
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

    public static String fingerprintOf(Context context, TaskParseResult parseResult) {
        // Context is ignored on purpose: fingerprint must be locale/resource independent.
        return fingerprintOf(parseResult);
    }

    public static String fingerprintOf(TaskParseResult parseResult) {
        if (parseResult == null) {
            return fingerprintOf("");
        }

        StringBuilder builder = new StringBuilder();
        for (ObsidianTask task : parseResult.getTasks()) {
            builder.append("task|")
                    .append(safe(task.getTaskKey())).append('|')
                    .append(task.isCompleted()).append('|')
                    .append(task.getLineNumber()).append('|')
                    .append(safe(task.getSourceName())).append('|')
                    .append(safe(task.getRawLine()))
                    .append('\n');
        }
        for (TaskParseError error : parseResult.getErrors()) {
            builder.append("error|")
                    .append(safe(error.getSourceName())).append('|')
                    .append(error.getLineNumber()).append('|')
                    .append(error.getKind() == null ? "" : error.getKind().name());
            Object[] args = error.getMessageArgs();
            if (args != null && args.length > 0) {
                for (Object arg : args) {
                    builder.append('|').append(arg == null ? "" : String.valueOf(arg));
                }
            }
            builder.append('\n');
        }
        return fingerprintOf(builder.toString());
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static void recordFailedSync(Context context, Exception exception) {
        recordFailedSync(context, exception.getMessage());
    }

    private static void recordFailedSync(Context context, String message) {
        synchronized (MONITOR_STATE_LOCK) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_LAST_SYNC_AT, LocalDateTime.now().toString())
                    .putString(KEY_LAST_ERROR, safeMessage(context, message))
                    .apply();
        }
        ErrorLog.record(context, context.getString(R.string.runtime_sync_error, safeMessage(context, message)));
    }

    public static boolean isSuspiciousPartialRead(
            Context context,
            NoteStore.TaskSnapshot snapshot
    ) {
        if (context == null || snapshot == null || snapshot.getParseResult() == null) {
            return false;
        }

        synchronized (MONITOR_STATE_LOCK) {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String currentSourceState = sourceStateIdentity(context);
            String lastSourceState = prefs.getString(KEY_LAST_SOURCE_STATE, null);
            if (shouldBypassPartialReadProtection(lastSourceState, currentSourceState)) {
                resetProtectionBaselineForSourceChange(prefs, currentSourceState);
                ErrorLog.record(
                        context,
                        "Accepted snapshot after source change: " + safeSourceState(currentSourceState)
                );
                return false;
            }

            boolean emptyReadWithCache = snapshot.getTotalCharacters() <= MAX_EMPTY_READ_CHARACTERS
                    && snapshot.getParseResult().getTasks().isEmpty()
                    && TaskCache.hasCachedTasks(context);
            if (emptyReadWithCache) {
                int emptyReadCount = prefs.getInt(KEY_EMPTY_READ_COUNT, 0) + 1;
                prefs.edit()
                        .putInt(KEY_EMPTY_READ_COUNT, emptyReadCount)
                        .putString(KEY_LAST_SOURCE_STATE, currentSourceState)
                        .apply();
                if (emptyReadCount <= MAX_PROTECTED_EMPTY_READS) {
                    ErrorLog.record(
                            context,
                            "Protected empty snapshot " + emptyReadCount + "/" + MAX_PROTECTED_EMPTY_READS
                    );
                    return true;
                }
                return false;
            }

            int lastDocCount = prefs.getInt(KEY_LAST_DOCUMENT_COUNT, 0);
            int lastTaskCount = prefs.getInt(KEY_LAST_TASK_COUNT, 0);
            int lastTotalChars = prefs.getInt(KEY_LAST_TOTAL_CHARACTERS, 0);
            int suspiciousCount = prefs.getInt(KEY_SUSPICIOUS_READ_COUNT, 0);
            boolean hasCache = TaskCache.hasCachedTasks(context);

            int currentDocCount = snapshot.getDocumentCount();
            int currentTaskCount = snapshot.getParseResult().getTasks().size();
            int currentTotalChars = snapshot.getTotalCharacters();

            boolean suspicious = isSuspiciousSnapshotDelta(
                    lastDocCount,
                    lastTaskCount,
                    lastTotalChars,
                    currentDocCount,
                    currentTaskCount,
                    currentTotalChars,
                    hasCache
            );

            if (!suspicious) {
                prefs.edit()
                        .remove(KEY_EMPTY_READ_COUNT)
                        .remove(KEY_SUSPICIOUS_READ_COUNT)
                        .putString(KEY_LAST_SOURCE_STATE, currentSourceState)
                        .apply();
                return false;
            }

            suspiciousCount++;
            prefs.edit()
                    .putInt(KEY_SUSPICIOUS_READ_COUNT, suspiciousCount)
                    .putString(KEY_LAST_SOURCE_STATE, currentSourceState)
                    .apply();
            ErrorLog.record(
                    context,
                    "Protected suspicious snapshot "
                            + suspiciousCount + "/" + MAX_PROTECTED_EMPTY_READS
                            + ": "
                            + suspiciousSnapshotReason(
                            lastDocCount,
                            lastTaskCount,
                            lastTotalChars,
                            currentDocCount,
                            currentTaskCount,
                            currentTotalChars
                    )
            );
            return suspiciousCount <= MAX_PROTECTED_EMPTY_READS;
        }
    }

    static boolean isSuspiciousSnapshotDelta(
            int lastDocCount,
            int lastTaskCount,
            int lastTotalChars,
            int currentDocCount,
            int currentTaskCount,
            int currentTotalChars,
            boolean hasCache
    ) {
        if (!hasCache) {
            return false;
        }
        if (lastDocCount <= 0 && lastTaskCount <= 0 && lastTotalChars <= 0) {
            return false;
        }

        // Document list partially returned (folder scan / SAF provider sync).
        if (lastDocCount > 0 && currentDocCount > 0 && currentDocCount < lastDocCount) {
            return true;
        }

        // Large downward deltas look like partial/truncated reads.
        if (lastTotalChars > 0 && currentTotalChars >= 0 && currentTotalChars < (lastTotalChars / 2)) {
            return true;
        }
        if (lastTaskCount > 0 && currentTaskCount >= 0 && currentTaskCount < Math.max(1, lastTaskCount / 2)) {
            return true;
        }

        return false;
    }

    static boolean shouldBypassPartialReadProtection(
            String previousSourceState,
            String currentSourceState
    ) {
        return previousSourceState != null
                && currentSourceState != null
                && !previousSourceState.equals(currentSourceState);
    }

    static String suspiciousSnapshotReason(
            int lastDocCount,
            int lastTaskCount,
            int lastTotalChars,
            int currentDocCount,
            int currentTaskCount,
            int currentTotalChars
    ) {
        if (lastDocCount > 0 && currentDocCount > 0 && currentDocCount < lastDocCount) {
            return "document count drop " + lastDocCount + " -> " + currentDocCount;
        }
        if (lastTotalChars > 0 && currentTotalChars >= 0 && currentTotalChars < (lastTotalChars / 2)) {
            return "character count drop " + lastTotalChars + " -> " + currentTotalChars;
        }
        if (lastTaskCount > 0 && currentTaskCount >= 0 && currentTaskCount < Math.max(1, lastTaskCount / 2)) {
            return "task count drop " + lastTaskCount + " -> " + currentTaskCount;
        }
        return "snapshot delta deemed suspicious";
    }

    private static String safeMessage(Context context, String message) {
        if (message == null || message.trim().isEmpty()) {
            return context.getString(R.string.runtime_no_details);
        }
        return message;
    }

    private static String sourceStateIdentity(Context context) {
        TaskStorageMode mode = TaskSourceManager.getStorageMode(context);
        if (mode == TaskStorageMode.INTERNAL_MARKDOWN_STORAGE) {
            return "internal";
        }

        List<NoteStore.NoteSource> sources = NoteStore.getStoredExternalSources(context);
        if (sources.isEmpty()) {
            return "external:empty";
        }

        StringBuilder builder = new StringBuilder("external");
        for (NoteStore.NoteSource source : sources) {
            if (source == null || source.getUri() == null) {
                continue;
            }
            builder.append('|')
                    .append(source.getType())
                    .append(':')
                    .append(source.getUri());
        }
        return builder.toString();
    }

    private static void resetProtectionBaselineForSourceChange(
            SharedPreferences prefs,
            String currentSourceState
    ) {
        prefs.edit()
                .putString(KEY_LAST_SOURCE_STATE, currentSourceState)
                .remove(KEY_EMPTY_READ_COUNT)
                .remove(KEY_SUSPICIOUS_READ_COUNT)
                .remove(KEY_LAST_DOCUMENT_COUNT)
                .remove(KEY_LAST_TASK_COUNT)
                .remove(KEY_LAST_TOTAL_CHARACTERS)
                .apply();
    }

    private static String safeSourceState(String sourceState) {
        if (sourceState == null || sourceState.trim().isEmpty()) {
            return "unknown";
        }
        return sourceState;
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
