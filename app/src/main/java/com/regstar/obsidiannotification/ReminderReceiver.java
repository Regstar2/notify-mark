package com.regstar.obsidiannotification;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.io.IOException;

public final class ReminderReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        ReminderScheduler.ensureNotificationChannel(context);
        if (!ReminderScheduler.canPostNotifications(context)) {
            return;
        }

        String taskKey = intent.getStringExtra(ReminderScheduler.EXTRA_TASK_KEY);
        int notificationId = intent.getIntExtra(
                ReminderScheduler.EXTRA_NOTIFICATION_ID,
                (int) System.currentTimeMillis()
        );
        String title = intent.getStringExtra(ReminderScheduler.EXTRA_TITLE);
        int lineNumber = intent.getIntExtra(ReminderScheduler.EXTRA_LINE_NUMBER, -1);
        long triggerAtMillis = intent.getLongExtra(
                ReminderScheduler.EXTRA_TRIGGER_AT_MILLIS,
                System.currentTimeMillis()
        );
        long repeatIntervalMillis = intent.getLongExtra(
                ReminderScheduler.EXTRA_REPEAT_INTERVAL_MILLIS,
                0L
        );
        RepeatMode repeatMode = RepeatMode.fromName(
                intent.getStringExtra(ReminderScheduler.EXTRA_REPEAT_MODE)
        );

        ActiveTaskLookup activeTaskLookup = ActiveTaskLookup.unknown();
        if (repeatMode == RepeatMode.UNTIL_DONE) {
            activeTaskLookup = findActiveTask(context, taskKey);
            if (activeTaskLookup.isMissing()) {
                ReminderScheduler.cancelReminder(context, taskKey);
                return;
            }

            if (activeTaskLookup.isFound()) {
                ObsidianTask activeTask = activeTaskLookup.getTask();
                title = activeTask.getTitle();
                lineNumber = activeTask.getLineNumber();
                repeatIntervalMillis = activeTask.getRepeatIntervalMillis();
            }
        }

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) {
            return;
        }

        notificationManager.notify(
                notificationId,
                buildNotification(context, safeTitle(title), lineNumber, triggerAtMillis)
        );

        scheduleNextRepeat(
                context,
                taskKey,
                notificationId,
                lineNumber,
                safeTitle(title),
                repeatIntervalMillis,
                repeatMode,
                activeTaskLookup.getTask()
        );
    }

    @SuppressWarnings("deprecation")
    private Notification buildNotification(
            Context context,
            String title,
            int lineNumber,
            long triggerAtMillis
    ) {
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(context, ReminderScheduler.CHANNEL_ID)
                : new Notification.Builder(context);

        builder.setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Напоминание Obsidian")
                .setContentText(title)
                .setStyle(new Notification.BigTextStyle().bigText(title))
                .setContentIntent(createOpenAppIntent(context))
                .setAutoCancel(true)
                .setWhen(triggerAtMillis)
                .setShowWhen(true)
                .setPriority(Notification.PRIORITY_DEFAULT);

        if (lineNumber > 0) {
            builder.setSubText("Строка " + lineNumber);
        }

        return builder.build();
    }

    private PendingIntent createOpenAppIntent(Context context) {
        Intent openAppIntent = new Intent(context, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        return PendingIntent.getActivity(
                context,
                0,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private String safeTitle(String title) {
        if (title == null || title.trim().isEmpty()) {
            return "Задача без текста";
        }
        return title;
    }

    private ActiveTaskLookup findActiveTask(Context context, String taskKey) {
        if (taskKey == null || taskKey.isEmpty()) {
            return ActiveTaskLookup.unknown();
        }

        try {
            ObsidianTask task = NoteStore.findActiveTask(context, taskKey);
            return task == null ? ActiveTaskLookup.missing() : ActiveTaskLookup.found(task);
        } catch (IOException | SecurityException exception) {
            return ActiveTaskLookup.unknown();
        }
    }

    private void scheduleNextRepeat(
            Context context,
            String taskKey,
            int notificationId,
            int lineNumber,
            String title,
            long repeatIntervalMillis,
            RepeatMode repeatMode,
            ObsidianTask activeTask
    ) {
        if (repeatMode == RepeatMode.UNTIL_DONE) {
            if (activeTask != null) {
                ReminderScheduler.scheduleNextRepeat(context, activeTask);
            } else if (repeatIntervalMillis > 0) {
                ReminderScheduler.scheduleNextRepeat(
                        context,
                        taskKey,
                        notificationId,
                        lineNumber,
                        title,
                        repeatIntervalMillis,
                        repeatMode
                );
            }
            return;
        }

        if (repeatMode == RepeatMode.ALWAYS && repeatIntervalMillis > 0) {
            ReminderScheduler.scheduleNextRepeat(
                    context,
                    taskKey,
                    notificationId,
                    lineNumber,
                    title,
                    repeatIntervalMillis,
                    repeatMode
            );
        }
    }

    private static final class ActiveTaskLookup {
        private final ObsidianTask task;
        private final boolean checked;

        private ActiveTaskLookup(ObsidianTask task, boolean checked) {
            this.task = task;
            this.checked = checked;
        }

        private static ActiveTaskLookup found(ObsidianTask task) {
            return new ActiveTaskLookup(task, true);
        }

        private static ActiveTaskLookup missing() {
            return new ActiveTaskLookup(null, true);
        }

        private static ActiveTaskLookup unknown() {
            return new ActiveTaskLookup(null, false);
        }

        private boolean isFound() {
            return task != null;
        }

        private boolean isMissing() {
            return checked && task == null;
        }

        private ObsidianTask getTask() {
            return task;
        }
    }
}
