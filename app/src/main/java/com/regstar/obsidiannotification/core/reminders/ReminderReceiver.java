package com.regstar.obsidiannotification.core.reminders;

import com.regstar.obsidiannotification.R;
import com.regstar.obsidiannotification.ui.MainActivity;

import com.regstar.obsidiannotification.core.source.*;
import com.regstar.obsidiannotification.core.tasks.*;
import com.regstar.obsidiannotification.prefs.*;
import com.regstar.obsidiannotification.support.ErrorLog;
import com.regstar.obsidiannotification.support.IoExecutor;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.io.IOException;
import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Broadcast receiver that turns scheduled alarms into visible Android
 * notifications.
 *
 * <p>For repeat-until-done nags this receiver intentionally reuses the same
 * notification identity for the current occurrence so the shade keeps one live
 * card instead of accumulating duplicates.</p>
 */
public final class ReminderReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }
        final PendingResult pendingResult = goAsync();
        final Context appContext = context == null ? null : context.getApplicationContext();
        IoExecutor.io().execute(() -> {
            try {
                handleReceive(appContext, intent);
            } catch (Exception exception) {
                if (appContext != null) {
                    ErrorLog.record(appContext, appContext.getString(R.string.runtime_show_notification_error), exception);
                }
            } finally {
                pendingResult.finish();
            }
        });
    }

    private void handleReceive(Context context, Intent intent) {
        if (context == null) {
            return;
        }
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
        String group = ObsidianTask.normalizeGroup(
                intent.getStringExtra(ReminderScheduler.EXTRA_GROUP)
        );

        ActiveTaskLookup activeTaskLookup = ActiveTaskLookup.unknown();
        if (hasNagLoop(repeatIntervalMillis, repeatMode)) {
            activeTaskLookup = findActiveTask(context, taskKey);
            if (activeTaskLookup.isMissing()) {
                ReminderScheduler.cancelReminder(context, taskKey);
                cancelDisplayedNotification(context, notificationId, repeatMode, triggerAtMillis);
                return;
            }

            if (activeTaskLookup.isFound()) {
                ObsidianTask activeTask = activeTaskLookup.getTask();
                title = activeTask.getTitle();
                lineNumber = activeTask.getLineNumber();
                repeatIntervalMillis = activeTask.getResolvedRepeatUntilDoneIntervalMillis();
                repeatMode = activeTask.getRepeatMode();
                group = activeTask.getGroup();
            }
        }

        String dueLabel = buildDueLabel(context, triggerAtMillis, activeTaskLookup.getTask());
        long notificationWhenMillis = buildNotificationWhenMillis(
                triggerAtMillis,
                activeTaskLookup.getTask(),
                repeatIntervalMillis,
                repeatMode
        );

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) {
            return;
        }

        long displayTimeMillis = System.currentTimeMillis();
        int displayNotificationId = notificationIdForDisplay(
                notificationId,
                displayTimeMillis,
                repeatMode,
                repeatIntervalMillis
        );

        if (shouldRepostNotification(repeatIntervalMillis, repeatMode)) {
            notificationManager.cancel(displayNotificationId);
        }

        notificationManager.notify(
                displayNotificationId,
                buildNotification(
                        context,
                        taskKey,
                        notificationId,
                        displayNotificationId,
                        safeTitle(context, title),
                        lineNumber,
                        notificationWhenMillis,
                        repeatIntervalMillis,
                        repeatMode,
                        group,
                        dueLabel
                )
        );

        scheduleNextRepeat(
                context,
                taskKey,
                notificationId,
                lineNumber,
                safeTitle(context, title),
                repeatIntervalMillis,
                repeatMode,
                activeTaskLookup.getTask()
        );
    }

    @SuppressWarnings("deprecation")
    private Notification buildNotification(
            Context context,
            String taskKey,
            int notificationId,
            int displayNotificationId,
            String title,
            int lineNumber,
            long notificationWhenMillis,
            long repeatIntervalMillis,
            RepeatMode repeatMode,
            String group,
            String dueLabel
    ) {
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(context, ReminderScheduler.CHANNEL_ID)
                : new Notification.Builder(context);

        String safeDueLabel = (dueLabel == null || dueLabel.trim().isEmpty())
                ? ""
                : dueLabel.trim();

        String expandedText = safeDueLabel.isEmpty() ? title : safeDueLabel;

        builder.setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(safeDueLabel)
                .setStyle(new Notification.BigTextStyle().bigText(expandedText))
                .setContentIntent(createOpenAppIntent(context))
                .setAutoCancel(true)
                .setWhen(notificationWhenMillis)
                .setShowWhen(true)
                .setOnlyAlertOnce(false)
                .setGroup("obsidian_notification_" + ObsidianTask.normalizeGroup(group))
                .setCategory(Notification.CATEGORY_REMINDER)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setDefaults(Notification.DEFAULT_SOUND
                        | Notification.DEFAULT_VIBRATE
                        | Notification.DEFAULT_LIGHTS)
                .setPriority(Notification.PRIORITY_MAX);

        if (taskKey != null && !taskKey.trim().isEmpty()) {
            builder.addAction(
                    R.drawable.ic_check,
                    "✓",
                    ReminderActionReceiver.createActionPendingIntent(
                            context,
                            ReminderActionReceiver.ACTION_MARK_DONE,
                            taskKey,
                            notificationId,
                            displayNotificationId,
                            lineNumber,
                            title,
                            repeatIntervalMillis,
                            repeatMode
                    )
            );
            builder.addAction(
                    R.drawable.ic_repeat,
                    snoozeActionLabel(context, taskKey),
                    ReminderActionReceiver.createActionPendingIntent(
                            context,
                            ReminderActionReceiver.ACTION_SNOOZE,
                            taskKey,
                            notificationId,
                            displayNotificationId,
                            lineNumber,
                            title,
                            repeatIntervalMillis,
                            repeatMode
                    )
            );
            builder.addAction(
                    R.drawable.ic_close,
                    context.getString(R.string.reminder_action_skip),
                    ReminderActionReceiver.createActionPendingIntent(
                            context,
                            ReminderActionReceiver.ACTION_SKIP,
                            taskKey,
                            notificationId,
                            displayNotificationId,
                            lineNumber,
                            title,
                            repeatIntervalMillis,
                            repeatMode
                    )
            );
        }

        return builder.build();
    }

    private String snoozeActionLabel(Context context, String taskKey) {
        Duration duration = resolveSnoozeDuration(context, taskKey);
        return context.getString(R.string.reminder_action_snooze, formatDurationToken(duration));
    }

    private Duration resolveSnoozeDuration(Context context, String taskKey) {
        if (taskKey != null && !taskKey.trim().isEmpty()) {
            try {
                NoteStore.TaskDocumentMatch match = NoteStore.findTaskDocument(context, taskKey);
                if (match != null
                        && match.getTask() != null
                        && match.getTask().getSnoozeDuration() != null
                        && !match.getTask().getSnoozeDuration().isNegative()
                        && !match.getTask().getSnoozeDuration().isZero()) {
                    return match.getTask().getSnoozeDuration();
                }
            } catch (IOException | RuntimeException exception) {
                ErrorLog.record(context, context.getString(R.string.reminder_snooze_label_error), exception);
            }
        }
        return Duration.ofMinutes(ActionPreferences.getSnoozeMinutes(context));
    }

    private String formatDurationToken(Duration duration) {
        Duration safeDuration = duration == null || duration.isZero() || duration.isNegative()
                ? Duration.ofMinutes(1)
                : duration;
        long minutes = safeDuration.toMinutes();
        if (minutes % (24L * 60L) == 0L) {
            return (minutes / (24L * 60L)) + "d";
        }
        if (minutes % 60L == 0L) {
            return (minutes / 60L) + "h";
        }
        return minutes + "m";
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

    private String safeTitle(Context context, String title) {
        if (title == null || title.trim().isEmpty()) {
            return context.getString(R.string.reminder_title_fallback);
        }
        return title;
    }

    private String buildDueLabel(Context context, long fallbackTriggerAtMillis, ObsidianTask activeTask) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");

        if (activeTask != null && activeTask.getReminderAt() != null) {
            return context.getString(
                    R.string.reminder_due_at,
                    activeTask.getReminderAt().toLocalTime().format(formatter)
            );
        }

        return context.getString(
                R.string.reminder_due_at,
                java.time.Instant.ofEpochMilli(fallbackTriggerAtMillis)
                        .atZone(ZoneId.systemDefault())
                        .toLocalTime()
                        .format(formatter)
        );
    }

    private long buildNotificationWhenMillis(
            long fallbackTriggerAtMillis,
            ObsidianTask activeTask,
            long repeatIntervalMillis,
            RepeatMode repeatMode
    ) {
        if (shouldRepostNotification(repeatIntervalMillis, repeatMode)) {
            return fallbackTriggerAtMillis;
        }

        if (activeTask != null && activeTask.getReminderAt() != null) {
            return activeTask.getReminderAt()
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli();
        }

        return fallbackTriggerAtMillis;
    }

    private int notificationIdForDisplay(
            int scheduledNotificationId,
            long triggerAtMillis,
            RepeatMode repeatMode,
            long repeatIntervalMillis
    ) {
        return scheduledNotificationId;
    }

    private void cancelDisplayedNotification(
            Context context,
            int notificationId,
            RepeatMode repeatMode,
            long triggerAtMillis
    ) {
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) {
            return;
        }

        int displayNotificationId = notificationIdForDisplay(
                notificationId,
                triggerAtMillis,
                repeatMode,
                0L
        );

        notificationManager.cancel(displayNotificationId);
        if (notificationId != displayNotificationId) {
            notificationManager.cancel(notificationId);
        }
    }

    private ActiveTaskLookup findActiveTask(Context context, String taskKey) {
        if (taskKey == null || taskKey.isEmpty()) {
            return ActiveTaskLookup.unknown();
        }

        try {
            NoteStore.TaskSnapshot snapshot = NoteStore.readTaskSnapshot(context);
            if (NoteChangeMonitor.isSuspiciousPartialRead(context, snapshot)) {
                return ActiveTaskLookup.unknown();
            }

            for (ObsidianTask task : snapshot.getParseResult().getActiveTasks()) {
                if (task.getTaskKey().equals(taskKey)) {
                    return ActiveTaskLookup.found(task);
                }
            }
            return ActiveTaskLookup.missing();
        } catch (IOException | RuntimeException exception) {
            ErrorLog.record(context, context.getString(R.string.reminder_repeat_lookup_error), exception);
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
        if (shouldUseActiveTaskNagInterval(activeTask)) {
            ReminderScheduler.scheduleNextRepeat(context, activeTask);
            return;
        }

        if (repeatIntervalMillis > 0) {
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

    static boolean hasNagLoop(long repeatIntervalMillis, RepeatMode repeatMode) {
        return repeatIntervalMillis > 0L || repeatMode == RepeatMode.UNTIL_DONE;
    }

    static boolean shouldRepostNotification(long repeatIntervalMillis, RepeatMode repeatMode) {
        return hasNagLoop(repeatIntervalMillis, repeatMode);
    }

    static boolean shouldUseActiveTaskNagInterval(ObsidianTask activeTask) {
        return activeTask != null
                && activeTask.getRepeatMode() == RepeatMode.UNTIL_DONE
                && activeTask.getResolvedRepeatUntilDoneInterval() != null
                && !activeTask.getResolvedRepeatUntilDoneInterval().isZero()
                && !activeTask.getResolvedRepeatUntilDoneInterval().isNegative();
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
