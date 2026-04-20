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
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ReminderScheduler {
    public static final String CHANNEL_ID = "task_reminders_aggressive";
    public static final String ACTION_SHOW_REMINDER =
            "com.regstar.obsidiannotification.action.SHOW_REMINDER";
    public static final String EXTRA_TASK_KEY = "task_key";
    public static final String EXTRA_NOTIFICATION_ID = "notification_id";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_LINE_NUMBER = "line_number";
    public static final String EXTRA_TRIGGER_AT_MILLIS = "trigger_at_millis";
    public static final String EXTRA_REPEAT_INTERVAL_MILLIS = "repeat_interval_millis";
    public static final String EXTRA_REPEAT_MODE = "repeat_mode";

    private static final String PREFS_NAME = "obsidian_notification_scheduled_reminders";
    private static final String KEY_SCHEDULED_REMINDERS = "scheduled_reminders";
    private static final String KEY_LEGACY_SCHEDULED_IDS = "scheduled_ids";
    private static final String REMINDER_URI_PREFIX = "obsidiannotification://reminder/";

    private ReminderScheduler() {
    }

    public static void ensureNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Настойчивые напоминания задач",
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.setDescription("Настойчивые локальные напоминания из markdown-задач Obsidian.");
        channel.enableLights(true);
        channel.enableVibration(true);
        channel.setVibrationPattern(new long[]{0L, 500L, 200L, 500L});
        channel.setLockscreenVisibility(android.app.Notification.VISIBILITY_PUBLIC);
        channel.setSound(
                Settings.System.DEFAULT_NOTIFICATION_URI,
                new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
        );

        NotificationManager notificationManager =
                context.getSystemService(NotificationManager.class);
        if (notificationManager != null) {
            notificationManager.createNotificationChannel(channel);
        }
    }

    public static ReminderSchedule schedule(Context context, List<ObsidianTask> tasks) {
        return schedule(context, tasks, LocalDateTime.now(), ZoneId.systemDefault());
    }

    static ReminderSchedule schedule(
            Context context,
            List<ObsidianTask> tasks,
            LocalDateTime now,
            ZoneId zoneId
    ) {
        cancelLegacyScheduled(context);

        Map<String, ScheduledState> existingState = loadScheduledState(context);
        if (!canPostNotifications(context)) {
            cancelScheduled(context, existingState);
            return new ReminderSchedule(false, 0, null);
        }

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return new ReminderSchedule(true, 0, null);
        }

        long nowMillis = now.atZone(zoneId).toInstant().toEpochMilli();
        Map<String, ScheduledReminder> desiredReminders = new HashMap<>();
        ScheduledReminder nearest = null;

        for (ObsidianTask task : tasks) {
            ScheduledReminder reminder = buildScheduledReminder(task, now, zoneId);
            if (reminder == null) {
                continue;
            }

            ScheduledState existing = existingState.get(task.getTaskKey());
            if (shouldKeepExistingReminder(task, reminder, existing, nowMillis)) {
                reminder = copyWithExistingTrigger(reminder, existing, zoneId);
            }

            desiredReminders.put(task.getTaskKey(), reminder);
            if (nearest == null || reminder.getTriggerAtMillis() < nearest.getTriggerAtMillis()) {
                nearest = reminder;
            }
        }

        for (ScheduledState existing : existingState.values()) {
            if (!desiredReminders.containsKey(existing.taskKey)) {
                cancelReminderAlarm(context, alarmManager, existing.notificationId);
            }
        }

        for (ScheduledReminder reminder : desiredReminders.values()) {
            ScheduledState existing = existingState.get(reminder.getTaskKey());
            if (existing == null || existing.triggerAtMillis != reminder.getTriggerAtMillis()) {
                setReminderAlarm(context, alarmManager, reminder);
            }
        }

        saveScheduledState(context, desiredReminders.values());
        return new ReminderSchedule(true, desiredReminders.size(), nearest);
    }

    public static void scheduleNextRepeat(Context context, ObsidianTask task) {
        Duration repeatInterval = task.getRepeatInterval();
        if (!isValidRepeat(repeatInterval) || !canPostNotifications(context)) {
            cancelReminder(context, task.getTaskKey());
            return;
        }

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }

        ZoneId zoneId = ZoneId.systemDefault();
        LocalDateTime nextTriggerAt = LocalDateTime.now().plus(repeatInterval);
        ScheduledReminder reminder = buildScheduledReminderAt(task, nextTriggerAt, zoneId);
        setReminderAlarm(context, alarmManager, reminder);
        putScheduledState(context, reminder);
    }

    public static void scheduleNextRepeat(
            Context context,
            String taskKey,
            int notificationId,
            int lineNumber,
            String title,
            long repeatIntervalMillis,
            RepeatMode repeatMode
    ) {
        if (repeatIntervalMillis <= 0 || !canPostNotifications(context)) {
            cancelReminder(context, taskKey);
            return;
        }

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }

        ZoneId zoneId = ZoneId.systemDefault();
        LocalDateTime nextTriggerAt = LocalDateTime.now().plus(Duration.ofMillis(repeatIntervalMillis));
        long nextTriggerAtMillis = nextTriggerAt.atZone(zoneId).toInstant().toEpochMilli();
        ScheduledReminder reminder = new ScheduledReminder(
                taskKey,
                notificationId,
                lineNumber,
                title,
                nextTriggerAt,
                nextTriggerAtMillis,
                repeatIntervalMillis,
                repeatMode
        );
        setReminderAlarm(context, alarmManager, reminder);
        putScheduledState(context, reminder);
    }

    public static void cancelReminder(Context context, String taskKey) {
        Map<String, ScheduledState> state = loadScheduledState(context);
        ScheduledState existing = state.remove(taskKey);
        if (existing == null) {
            return;
        }

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            cancelReminderAlarm(context, alarmManager, existing.notificationId);
        }
        saveScheduledStateEntries(context, state.values());
    }

    public static void cancelScheduled(Context context) {
        cancelScheduled(context, loadScheduledState(context));
        cancelLegacyScheduled(context);
    }

    public static boolean canPostNotifications(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true;
        }

        return context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean canScheduleExactAlarms(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return true;
        }

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        return alarmManager != null && alarmManager.canScheduleExactAlarms();
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

        return buildScheduledReminderAt(task, triggerAt, zoneId);
    }

    private static ScheduledReminder buildScheduledReminderAt(
            ObsidianTask task,
            LocalDateTime triggerAt,
            ZoneId zoneId
    ) {
        long triggerAtMillis = triggerAt.atZone(zoneId).toInstant().toEpochMilli();
        return new ScheduledReminder(
                task.getTaskKey(),
                notificationIdFor(task),
                task.getLineNumber(),
                task.getTitle(),
                triggerAt,
                triggerAtMillis,
                task.getRepeatIntervalMillis(),
                task.getRepeatMode()
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
        if (!isValidRepeat(repeatInterval)) {
            return null;
        }

        long intervalMillis = repeatInterval.toMillis();
        long overdueMillis = Duration.between(reminderAt, now).toMillis();
        long intervalsToSkip = overdueMillis / intervalMillis + 1;
        return reminderAt.plus(repeatInterval.multipliedBy(intervalsToSkip));
    }

    private static boolean isValidRepeat(Duration repeatInterval) {
        return repeatInterval != null
                && !repeatInterval.isZero()
                && !repeatInterval.isNegative()
                && repeatInterval.toMillis() > 0;
    }

    private static boolean shouldKeepExistingReminder(
            ObsidianTask task,
            ScheduledReminder reminder,
            ScheduledState existing,
            long nowMillis
    ) {
        return isValidRepeat(task.getRepeatInterval())
                && existing != null
                && existing.notificationId == reminder.getNotificationId()
                && existing.triggerAtMillis > nowMillis;
    }

    private static ScheduledReminder copyWithExistingTrigger(
            ScheduledReminder reminder,
            ScheduledState existing,
            ZoneId zoneId
    ) {
        LocalDateTime triggerAt = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(existing.triggerAtMillis),
                zoneId
        );
        return new ScheduledReminder(
                reminder.getTaskKey(),
                reminder.getNotificationId(),
                reminder.getLineNumber(),
                reminder.getTitle(),
                triggerAt,
                existing.triggerAtMillis,
                reminder.getRepeatIntervalMillis(),
                reminder.getRepeatMode()
        );
    }

    private static void setReminderAlarm(
            Context context,
            AlarmManager alarmManager,
            ScheduledReminder reminder
    ) {
        PendingIntent pendingIntent = createReminderPendingIntent(context, reminder);
        alarmManager.cancel(pendingIntent);
        if (canScheduleExactAlarms(context)) {
            alarmManager.setAlarmClock(
                    new AlarmManager.AlarmClockInfo(
                            reminder.getTriggerAtMillis(),
                            createOpenAppPendingIntent(context, reminder.getNotificationId())
                    ),
                    pendingIntent
            );
        } else {
            alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    reminder.getTriggerAtMillis(),
                    pendingIntent
            );
        }
    }

    private static void cancelScheduled(Context context, Map<String, ScheduledState> state) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            for (ScheduledState entry : state.values()) {
                cancelReminderAlarm(context, alarmManager, entry.notificationId);
            }
        }

        getPreferences(context).edit().remove(KEY_SCHEDULED_REMINDERS).apply();
    }

    private static void cancelReminderAlarm(
            Context context,
            AlarmManager alarmManager,
            int notificationId
    ) {
        PendingIntent pendingIntent = findReminderPendingIntent(context, notificationId);
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent);
            pendingIntent.cancel();
        }
    }

    private static PendingIntent createReminderPendingIntent(
            Context context,
            ScheduledReminder reminder
    ) {
        Intent intent = baseReminderIntent(context, reminder.getNotificationId());
        intent.putExtra(EXTRA_TASK_KEY, reminder.getTaskKey());
        intent.putExtra(EXTRA_NOTIFICATION_ID, reminder.getNotificationId());
        intent.putExtra(EXTRA_TITLE, reminder.getTitle());
        intent.putExtra(EXTRA_LINE_NUMBER, reminder.getLineNumber());
        intent.putExtra(EXTRA_TRIGGER_AT_MILLIS, reminder.getTriggerAtMillis());
        intent.putExtra(EXTRA_REPEAT_INTERVAL_MILLIS, reminder.getRepeatIntervalMillis());
        intent.putExtra(EXTRA_REPEAT_MODE, reminder.getRepeatMode().name());

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

    private static PendingIntent createOpenAppPendingIntent(Context context, int notificationId) {
        Intent intent = new Intent(context, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        return PendingIntent.getActivity(
                context,
                notificationId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static Intent baseReminderIntent(Context context, int notificationId) {
        return new Intent(context, ReminderReceiver.class)
                .setAction(ACTION_SHOW_REMINDER)
                .setData(Uri.parse(REMINDER_URI_PREFIX + notificationId));
    }

    private static int notificationIdFor(ObsidianTask task) {
        int hash = Objects.hash(task.getTaskKey());
        if (hash == Integer.MIN_VALUE) {
            hash = 0;
        }

        int id = Math.abs(hash);
        return id == 0 ? task.getLineNumber() + 1 : id;
    }

    private static void putScheduledState(Context context, ScheduledReminder reminder) {
        Map<String, ScheduledState> state = loadScheduledState(context);
        state.put(reminder.getTaskKey(), ScheduledState.from(reminder));
        saveScheduledStateEntries(context, state.values());
    }

    private static void saveScheduledState(
            Context context,
            Iterable<ScheduledReminder> reminders
    ) {
        Set<String> encodedState = new HashSet<>();
        for (ScheduledReminder reminder : reminders) {
            encodedState.add(ScheduledState.from(reminder).encode());
        }

        getPreferences(context)
                .edit()
                .putStringSet(KEY_SCHEDULED_REMINDERS, encodedState)
                .apply();
    }

    private static void saveScheduledStateEntries(
            Context context,
            Iterable<ScheduledState> entries
    ) {
        Set<String> encodedState = new HashSet<>();
        for (ScheduledState entry : entries) {
            encodedState.add(entry.encode());
        }

        getPreferences(context)
                .edit()
                .putStringSet(KEY_SCHEDULED_REMINDERS, encodedState)
                .apply();
    }

    private static Map<String, ScheduledState> loadScheduledState(Context context) {
        Set<String> encodedState = getPreferences(context)
                .getStringSet(KEY_SCHEDULED_REMINDERS, new HashSet<>());
        Map<String, ScheduledState> state = new HashMap<>();
        for (String encodedEntry : encodedState) {
            ScheduledState entry = ScheduledState.decode(encodedEntry);
            if (entry != null) {
                state.put(entry.taskKey, entry);
            }
        }
        return state;
    }

    private static void cancelLegacyScheduled(Context context) {
        Set<String> legacyIds = new HashSet<>(
                getPreferences(context).getStringSet(KEY_LEGACY_SCHEDULED_IDS, new HashSet<>())
        );
        if (legacyIds.isEmpty()) {
            return;
        }

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            for (String rawId : legacyIds) {
                try {
                    cancelReminderAlarm(context, alarmManager, Integer.parseInt(rawId));
                } catch (NumberFormatException ignored) {
                    // Ignore malformed legacy entries.
                }
            }
        }

        getPreferences(context).edit().remove(KEY_LEGACY_SCHEDULED_IDS).apply();
    }

    private static SharedPreferences getPreferences(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static final class ScheduledState {
        private final String taskKey;
        private final int notificationId;
        private final long triggerAtMillis;

        private ScheduledState(String taskKey, int notificationId, long triggerAtMillis) {
            this.taskKey = taskKey;
            this.notificationId = notificationId;
            this.triggerAtMillis = triggerAtMillis;
        }

        private static ScheduledState from(ScheduledReminder reminder) {
            return new ScheduledState(
                    reminder.getTaskKey(),
                    reminder.getNotificationId(),
                    reminder.getTriggerAtMillis()
            );
        }

        private String encode() {
            String encodedKey = Base64.getUrlEncoder().withoutPadding().encodeToString(
                    taskKey.getBytes(StandardCharsets.UTF_8)
            );
            return encodedKey + "|" + notificationId + "|" + triggerAtMillis;
        }

        private static ScheduledState decode(String encoded) {
            String[] parts = encoded.split("\\|", -1);
            if (parts.length != 3) {
                return null;
            }

            try {
                String taskKey = new String(
                        Base64.getUrlDecoder().decode(parts[0]),
                        StandardCharsets.UTF_8
                );
                int notificationId = Integer.parseInt(parts[1]);
                long triggerAtMillis = Long.parseLong(parts[2]);
                return new ScheduledState(taskKey, notificationId, triggerAtMillis);
            } catch (IllegalArgumentException exception) {
                return null;
            }
        }
    }
}
