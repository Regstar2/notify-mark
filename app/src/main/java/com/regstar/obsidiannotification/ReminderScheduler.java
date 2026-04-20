package com.regstar.obsidiannotification;

import android.Manifest;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class ReminderScheduler {
    public static final String CHANNEL_ID = "task_reminders";
    public static final String ACTION_SHOW_REMINDER =
            "com.regstar.obsidiannotification.action.SHOW_REMINDER";
    public static final String EXTRA_NOTIFICATION_ID = "notification_id";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_LINE_NUMBER = "line_number";
    public static final String EXTRA_TRIGGER_AT_MILLIS = "trigger_at_millis";

    private static final String PREFS_NAME = "obsidian_notification_scheduled_reminders";
    private static final String KEY_SCHEDULED_IDS = "scheduled_ids";
    private static final String REMINDER_URI_PREFIX = "obsidiannotification://reminder/";

    private ReminderScheduler() {
    }

    public static void ensureNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Напоминания задач",
                NotificationManager.IMPORTANCE_DEFAULT
        );
        channel.setDescription("Локальные напоминания из markdown-задач Obsidian.");

        NotificationManager notificationManager =
                context.getSystemService(NotificationManager.class);
        if (notificationManager != null) {
            notificationManager.createNotificationChannel(channel);
        }
    }

    public static ReminderSchedule schedule(Context context, List<ObsidianTask> tasks) {
        cancelScheduled(context);

        if (!canPostNotifications(context)) {
            return new ReminderSchedule(false, 0, null);
        }

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return new ReminderSchedule(true, 0, null);
        }

        LocalDateTime now = LocalDateTime.now();
        ZoneId zoneId = ZoneId.systemDefault();
        Set<String> scheduledIds = new HashSet<>();
        ScheduledReminder nearest = null;

        for (ObsidianTask task : tasks) {
            ScheduledReminder reminder = buildScheduledReminder(task, now, zoneId);
            if (reminder == null) {
                continue;
            }

            PendingIntent pendingIntent = createReminderPendingIntent(context, reminder);
            alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    reminder.getTriggerAtMillis(),
                    pendingIntent
            );
            scheduledIds.add(String.valueOf(reminder.getNotificationId()));

            if (nearest == null || reminder.getTriggerAtMillis() < nearest.getTriggerAtMillis()) {
                nearest = reminder;
            }
        }

        getPreferences(context).edit().putStringSet(KEY_SCHEDULED_IDS, scheduledIds).apply();
        return new ReminderSchedule(true, scheduledIds.size(), nearest);
    }

    public static void cancelScheduled(Context context) {
        Set<String> scheduledIds = new HashSet<>(
                getPreferences(context).getStringSet(KEY_SCHEDULED_IDS, new HashSet<>())
        );
        if (scheduledIds.isEmpty()) {
            return;
        }

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        for (String rawId : scheduledIds) {
            int id;
            try {
                id = Integer.parseInt(rawId);
            } catch (NumberFormatException ignored) {
                continue;
            }

            PendingIntent pendingIntent = findReminderPendingIntent(context, id);
            if (pendingIntent != null) {
                if (alarmManager != null) {
                    alarmManager.cancel(pendingIntent);
                }
                pendingIntent.cancel();
            }
        }

        getPreferences(context).edit().remove(KEY_SCHEDULED_IDS).apply();
    }

    public static boolean canPostNotifications(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true;
        }

        return context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    static ScheduledReminder buildScheduledReminder(
            ObsidianTask task,
            LocalDateTime now,
            ZoneId zoneId
    ) {
        LocalDateTime triggerAt = findNextTriggerAt(task, now);
        if (triggerAt == null) {
            return null;
        }

        long triggerAtMillis = triggerAt.atZone(zoneId).toInstant().toEpochMilli();
        return new ScheduledReminder(
                notificationIdFor(task),
                task.getLineNumber(),
                task.getTitle(),
                triggerAt,
                triggerAtMillis
        );
    }

    private static LocalDateTime findNextTriggerAt(ObsidianTask task, LocalDateTime now) {
        LocalDateTime reminderAt = task.getReminderAt();
        if (reminderAt == null) {
            return null;
        }

        if (reminderAt.isAfter(now)) {
            return reminderAt;
        }

        Duration repeatInterval = task.getRepeatInterval();
        if (repeatInterval == null || repeatInterval.isZero() || repeatInterval.isNegative()) {
            return null;
        }

        long intervalMillis = repeatInterval.toMillis();
        if (intervalMillis <= 0) {
            return null;
        }

        long overdueMillis = Duration.between(reminderAt, now).toMillis();
        long intervalsToSkip = overdueMillis / intervalMillis + 1;
        return reminderAt.plus(repeatInterval.multipliedBy(intervalsToSkip));
    }

    private static PendingIntent createReminderPendingIntent(
            Context context,
            ScheduledReminder reminder
    ) {
        Intent intent = baseReminderIntent(context, reminder.getNotificationId());
        intent.putExtra(EXTRA_NOTIFICATION_ID, reminder.getNotificationId());
        intent.putExtra(EXTRA_TITLE, reminder.getTitle());
        intent.putExtra(EXTRA_LINE_NUMBER, reminder.getLineNumber());
        intent.putExtra(EXTRA_TRIGGER_AT_MILLIS, reminder.getTriggerAtMillis());

        return PendingIntent.getBroadcast(
                context,
                reminder.getNotificationId(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static PendingIntent findReminderPendingIntent(Context context, int notificationId) {
        return PendingIntent.getBroadcast(
                context,
                notificationId,
                baseReminderIntent(context, notificationId),
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static Intent baseReminderIntent(Context context, int notificationId) {
        return new Intent(context, ReminderReceiver.class)
                .setAction(ACTION_SHOW_REMINDER)
                .setData(Uri.parse(REMINDER_URI_PREFIX + notificationId));
    }

    private static int notificationIdFor(ObsidianTask task) {
        int hash = Objects.hash(task.getLineNumber(), task.getRawLine());
        if (hash == Integer.MIN_VALUE) {
            hash = 0;
        }

        int id = Math.abs(hash);
        return id == 0 ? task.getLineNumber() + 1 : id;
    }

    private static SharedPreferences getPreferences(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
