package com.regstar.obsidiannotification;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import java.io.IOException;
import java.time.Duration;

public final class ReminderActionReceiver extends BroadcastReceiver {
    public static final String ACTION_MARK_DONE =
            "com.regstar.obsidiannotification.action.MARK_DONE";
    public static final String ACTION_SNOOZE =
            "com.regstar.obsidiannotification.action.SNOOZE";
    public static final String ACTION_SKIP =
            "com.regstar.obsidiannotification.action.SKIP";
    public static final String ACTION_OPEN_NOTE =
            "com.regstar.obsidiannotification.action.OPEN_NOTE";

    public static final String EXTRA_DISPLAY_NOTIFICATION_ID = "display_notification_id";

    private static final String ACTION_URI_PREFIX = "obsidiannotification://action/";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }

        String action = intent.getAction();
        if (ACTION_MARK_DONE.equals(action)) {
            markDone(context, intent);
            return;
        }
        if (ACTION_SNOOZE.equals(action)) {
            snooze(context, intent);
            return;
        }
        if (ACTION_SKIP.equals(action)) {
            skip(context, intent);
            return;
        }
        if (ACTION_OPEN_NOTE.equals(action)) {
            openNote(context, intent);
        }
    }

    public static PendingIntent createActionPendingIntent(
            Context context,
            String action,
            String taskKey,
            int notificationId,
            int displayNotificationId,
            int lineNumber,
            String title,
            long repeatIntervalMillis,
            RepeatMode repeatMode
    ) {
        Intent intent = new Intent(context, ReminderActionReceiver.class)
                .setAction(action)
                .setData(Uri.parse(ACTION_URI_PREFIX + action + "/" + displayNotificationId));
        intent.putExtra(ReminderScheduler.EXTRA_TASK_KEY, taskKey);
        intent.putExtra(ReminderScheduler.EXTRA_NOTIFICATION_ID, notificationId);
        intent.putExtra(EXTRA_DISPLAY_NOTIFICATION_ID, displayNotificationId);
        intent.putExtra(ReminderScheduler.EXTRA_LINE_NUMBER, lineNumber);
        intent.putExtra(ReminderScheduler.EXTRA_TITLE, title);
        intent.putExtra(ReminderScheduler.EXTRA_REPEAT_INTERVAL_MILLIS, repeatIntervalMillis);
        RepeatMode safeRepeatMode = repeatMode == null ? RepeatMode.NONE : repeatMode;
        intent.putExtra(ReminderScheduler.EXTRA_REPEAT_MODE, safeRepeatMode.name());

        return PendingIntent.getBroadcast(
                context,
                displayNotificationId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private void markDone(Context context, Intent intent) {
        String taskKey = intent.getStringExtra(ReminderScheduler.EXTRA_TASK_KEY);
        TaskEditResult result = NoteStore.markTaskDone(context, taskKey);
        if (result.shouldStopReminder()) {
            ReminderScheduler.cancelReminder(context, taskKey);
            cancelNotification(context, intent);
            NoteChangeMonitor.syncNow(context, true);
            return;
        }

        ErrorLog.record(context, "Не удалось отметить задачу выполненной: " + result.getMessage());
    }

    private void snooze(Context context, Intent intent) {
        String taskKey = intent.getStringExtra(ReminderScheduler.EXTRA_TASK_KEY);
        int notificationId = intent.getIntExtra(
                ReminderScheduler.EXTRA_NOTIFICATION_ID,
                (int) System.currentTimeMillis()
        );
        int lineNumber = intent.getIntExtra(ReminderScheduler.EXTRA_LINE_NUMBER, -1);
        String title = intent.getStringExtra(ReminderScheduler.EXTRA_TITLE);
        long repeatIntervalMillis = intent.getLongExtra(
                ReminderScheduler.EXTRA_REPEAT_INTERVAL_MILLIS,
                0L
        );
        RepeatMode repeatMode = RepeatMode.fromName(
                intent.getStringExtra(ReminderScheduler.EXTRA_REPEAT_MODE)
        );

        int snoozeMinutes = ActionPreferences.getSnoozeMinutes(context);
        ReminderScheduler.scheduleSnooze(
                context,
                taskKey,
                notificationId,
                lineNumber,
                title,
                Duration.ofMinutes(snoozeMinutes),
                repeatIntervalMillis,
                repeatMode
        );
        if (ActionPreferences.shouldRecordSnoozeCount(context)) {
            TaskEditResult result = NoteStore.incrementSnoozeCount(context, taskKey);
            if (result.isFailure()) {
                ErrorLog.record(context, "Не удалось записать счетчик отложений: " + result.getMessage());
            }
        }
        cancelNotification(context, intent);
    }

    private void skip(Context context, Intent intent) {
        String taskKey = intent.getStringExtra(ReminderScheduler.EXTRA_TASK_KEY);
        TaskEditResult result = NoteStore.markTaskSkipped(context, taskKey);
        if (result.shouldStopReminder()) {
            ReminderScheduler.cancelReminder(context, taskKey);
            cancelNotification(context, intent);
            NoteChangeMonitor.syncNow(context, true);
            return;
        }

        cancelNotification(context, intent);
        ErrorLog.record(context, "Не удалось пропустить уведомление: " + result.getMessage());
    }

    private void openNote(Context context, Intent intent) {
        String taskKey = intent.getStringExtra(ReminderScheduler.EXTRA_TASK_KEY);
        Uri uri = null;
        try {
            NoteStore.TaskDocumentMatch match = NoteStore.findTaskDocument(context, taskKey);
            if (match != null) {
                uri = match.getUri();
            }
        } catch (IOException | RuntimeException exception) {
            ErrorLog.record(context, "Не удалось найти заметку для открытия", exception);
        }

        if (uri == null) {
            uri = NoteStore.getSavedSourceUri(context);
        }
        if (uri == null) {
            openApp(context);
            return;
        }
        if ("file".equalsIgnoreCase(uri.getScheme())) {
            openApp(context);
            return;
        }

        Intent openNoteIntent = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "text/markdown")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        try {
            context.startActivity(openNoteIntent);
        } catch (RuntimeException exception) {
            ErrorLog.record(context, "Не удалось открыть заметку напрямую", exception);
            openApp(context);
        }
    }

    private void openApp(Context context) {
        Intent openAppIntent = new Intent(context, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        context.startActivity(openAppIntent);
    }

    private void cancelNotification(Context context, Intent intent) {
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) {
            return;
        }

        int notificationId = intent.getIntExtra(ReminderScheduler.EXTRA_NOTIFICATION_ID, -1);
        int displayNotificationId = intent.getIntExtra(EXTRA_DISPLAY_NOTIFICATION_ID, -1);
        if (displayNotificationId >= 0) {
            notificationManager.cancel(displayNotificationId);
        }
        if (notificationId >= 0 && notificationId != displayNotificationId) {
            notificationManager.cancel(notificationId);
        }
    }
}
