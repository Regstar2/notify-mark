package com.regstar.obsidiannotification;

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

public final class NoteChangeMonitor {
    public static final String ACTION_SYNC_NOTE =
            "com.regstar.obsidiannotification.action.SYNC_NOTE";

    private static final String PREFS_NAME = "obsidian_notification_note_monitor";
    private static final String KEY_LAST_FINGERPRINT = "last_fingerprint";
    private static final String KEY_LAST_SYNC_AT = "last_sync_at";
    private static final String KEY_LAST_ERROR = "last_error";
    private static final long BACKGROUND_CHECK_INTERVAL_MS = 60_000L;
    private static final int REQUEST_SYNC_NOTE = 2001;

    private NoteChangeMonitor() {
    }

    public static void ensureScheduled(Context context) {
        if (NoteStore.getSavedNoteUri(context) == null) {
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
        try {
            String markdown = NoteStore.readMarkdown(context, NoteStore.requireSavedNoteUri(context));
            List<ObsidianTask> tasks = TaskParser.parse(markdown);
            ReminderSchedule schedule = ReminderScheduler.schedule(context, tasks);
            String fingerprint = fingerprintOf(markdown);
            String previousFingerprint = getLastFingerprint(context);
            recordSuccessfulSync(context, fingerprint);
            return NoteSyncResult.success(
                    previousFingerprint == null || !previousFingerprint.equals(fingerprint),
                    tasks.size(),
                    schedule
            );
        } catch (IOException | SecurityException exception) {
            recordFailedSync(context, exception);
            return NoteSyncResult.failure(exception.getMessage());
        }
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

    private static void recordFailedSync(Context context, Exception exception) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_LAST_SYNC_AT, LocalDateTime.now().toString())
                .putString(KEY_LAST_ERROR, exception.getMessage())
                .apply();
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

        private NoteSyncResult(
                boolean success,
                boolean changed,
                int taskCount,
                ReminderSchedule schedule,
                String errorMessage
        ) {
            this.success = success;
            this.changed = changed;
            this.taskCount = taskCount;
            this.schedule = schedule;
            this.errorMessage = errorMessage;
        }

        public static NoteSyncResult success(
                boolean changed,
                int taskCount,
                ReminderSchedule schedule
        ) {
            return new NoteSyncResult(true, changed, taskCount, schedule, null);
        }

        public static NoteSyncResult failure(String errorMessage) {
            return new NoteSyncResult(false, false, 0, null, errorMessage);
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
    }
}
