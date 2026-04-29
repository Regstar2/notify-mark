package com.regstar.obsidiannotification.core.reminders;

import com.regstar.obsidiannotification.R;
import com.regstar.obsidiannotification.ui.MainActivity;

import com.regstar.obsidiannotification.core.source.NoteChangeMonitor;
import com.regstar.obsidiannotification.core.source.NoteStore;
import com.regstar.obsidiannotification.core.source.TaskSourceManager;
import com.regstar.obsidiannotification.core.tasks.RepeatMode;
import com.regstar.obsidiannotification.core.tasks.TaskEditResult;
import com.regstar.obsidiannotification.prefs.ActionPreferences;
import com.regstar.obsidiannotification.support.ErrorLog;
import com.regstar.obsidiannotification.support.IoExecutor;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import java.io.IOException;
import java.time.Duration;

/**
 * Handles notification action buttons and bridges them back to markdown
 * mutations plus reminder rescheduling.
 */
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
        final PendingResult pendingResult = goAsync();
        final Context appContext = context == null ? null : context.getApplicationContext();
        IoExecutor.io().execute(() -> {
            try {
                handleReceive(appContext, intent);
            } catch (Exception exception) {
                if (appContext != null) {
                    ErrorLog.record(
                            appContext,
                            appContext.getString(R.string.runtime_notification_action_receiver_error),
                            exception
                    );
                }
            } finally {
                pendingResult.finish();
            }
        });
    }

    private void handleReceive(Context context, Intent intent) {
        if (context == null || intent == null || intent.getAction() == null) {
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

        ErrorLog.record(context, context.getString(R.string.runtime_mark_done_error, result.getMessage()));
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

        Duration snoozeDuration = resolveSnoozeDuration(context, taskKey);
        ReminderScheduler.scheduleSnooze(
                context,
                taskKey,
                notificationId,
                lineNumber,
                title,
                snoozeDuration,
                repeatIntervalMillis,
                repeatMode
        );
        if (ActionPreferences.shouldRecordSnoozeCount(context)) {
            TaskEditResult result = NoteStore.incrementSnoozeCount(context, taskKey);
            if (result.isFailure()) {
                ErrorLog.record(
                        context,
                        context.getString(R.string.runtime_snooze_count_write_error, result.getMessage())
                );
            }
        }
        cancelNotification(context, intent);
    }

    private Duration resolveSnoozeDuration(Context context, String taskKey) {
        if (taskKey != null && !taskKey.trim().isEmpty()) {
            try {
                NoteStore.TaskDocumentMatch match = NoteStore.findTaskDocument(context, taskKey);
                if (match != null
                        && match.getTask() != null
                        && match.getTask().getSnoozeDuration() != null
                        && !match.getTask().getSnoozeDuration().isZero()) {
                    return match.getTask().getSnoozeDuration();
                }
            } catch (IOException | RuntimeException exception) {
                ErrorLog.record(context, context.getString(R.string.runtime_snooze_interval_error), exception);
            }
        }
        return Duration.ofMinutes(ActionPreferences.getSnoozeMinutes(context));
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
        ErrorLog.record(context, context.getString(R.string.runtime_skip_notification_error, result.getMessage()));
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
            ErrorLog.record(context, context.getString(R.string.runtime_open_note_lookup_error), exception);
        }

        if (uri == null) {
            uri = TaskSourceManager.getActiveSourceUri(context);
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
            ErrorLog.record(context, context.getString(R.string.runtime_open_note_direct_error), exception);
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
