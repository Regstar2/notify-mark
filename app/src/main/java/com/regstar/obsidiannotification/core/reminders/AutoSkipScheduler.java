package com.regstar.obsidiannotification.core.reminders;

import com.regstar.obsidiannotification.core.source.NoteStore;
import com.regstar.obsidiannotification.core.tasks.AutoSkipCalculator;
import com.regstar.obsidiannotification.core.tasks.ObsidianTask;
import com.regstar.obsidiannotification.prefs.ActionPreferences;
import com.regstar.obsidiannotification.prefs.AutoSkipPreferences;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Plans auto-skip alarm clocks and applies due auto-skips before reminder scheduling.
 */
public final class AutoSkipScheduler {
    public static final String ACTION_AUTO_SKIP =
            "com.regstar.obsidiannotification.action.AUTO_SKIP";
    public static final String EXTRA_TASK_KEY = "auto_skip_task_key";
    public static final String EXTRA_EXPECTED_REMINDER_AT_MILLIS = "auto_skip_expected_reminder_ms";

    private static final String PREFS_NAME = "obsidian_notification_auto_skip_alarms";
    private static final String KEY_SCHEDULED_CODES = "scheduled_codes";
    private static final int REQUEST_CODE_BASE = 4_000_000;
    private static final int REQUEST_CODE_RANGE = 500_000;

    private AutoSkipScheduler() {
    }

    /**
     * Applies auto-skip for tasks whose deadline has passed; re-reads markdown between skips.
     */
    public static boolean processDueAutoSkips(Context context, LocalDateTime now, ZoneId zoneId) {
        if (!AutoSkipPreferences.isEnabled(context)) {
            return false;
        }
        Duration delay = Duration.ofMinutes(AutoSkipPreferences.getDelayMinutes(context));
        Duration defaultGrace = Duration.ofMinutes(ActionPreferences.getOverdueGraceMinutes(context));
        boolean any = false;
        for (int guard = 0; guard < 64; guard++) {
            List<ObsidianTask> activeTasks;
            try {
                activeTasks = NoteStore.readTaskSnapshot(context).getParseResult().getActiveTasks();
            } catch (IOException | RuntimeException exception) {
                break;
            }
            ObsidianTask next = findNextDueTask(activeTasks, now, zoneId, delay, defaultGrace);
            if (next == null) {
                break;
            }
            long expectedMillis = next.getReminderAt().atZone(zoneId).toInstant().toEpochMilli();
            if (AutoSkipExecutor.trySkipForOccurrence(context, next.getTaskKey(), expectedMillis)) {
                any = true;
            } else {
                break;
            }
        }
        return any;
    }

    private static ObsidianTask findNextDueTask(
            List<ObsidianTask> activeTasks,
            LocalDateTime now,
            ZoneId zoneId,
            Duration delay,
            Duration defaultGrace
    ) {
        if (activeTasks == null) {
            return null;
        }
        for (ObsidianTask task : activeTasks) {
            LocalDateTime autoAt = AutoSkipCalculator.computeAutoSkipAt(task, delay, defaultGrace);
            if (autoAt == null) {
                continue;
            }
            if (now.isBefore(autoAt)) {
                continue;
            }
            return task;
        }
        return null;
    }

    /**
     * Schedules alarm clocks for future auto-skip times; cancels alarms no longer needed.
     */
    public static void scheduleFutureAlarms(
            Context context,
            List<ObsidianTask> activeTasks,
            LocalDateTime now,
            ZoneId zoneId
    ) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> previousEncoded = new HashSet<>(prefs.getStringSet(KEY_SCHEDULED_CODES, new HashSet<>()));

        if (!AutoSkipPreferences.isEnabled(context)) {
            for (String raw : previousEncoded) {
                int code = parseRequestCode(raw);
                if (code >= 0) {
                    cancelAlarm(context, code);
                }
            }
            prefs.edit().remove(KEY_SCHEDULED_CODES).apply();
            return;
        }

        Duration delay = Duration.ofMinutes(AutoSkipPreferences.getDelayMinutes(context));
        Duration defaultGrace = Duration.ofMinutes(ActionPreferences.getOverdueGraceMinutes(context));

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }

        Map<Integer, String> desiredEncoded = new HashMap<>();
        if (activeTasks != null) {
            for (ObsidianTask task : activeTasks) {
                LocalDateTime autoAt = AutoSkipCalculator.computeAutoSkipAt(task, delay, defaultGrace);
                if (autoAt == null) {
                    continue;
                }
                if (!now.isBefore(autoAt)) {
                    continue;
                }
                long reminderMillis = task.getReminderAt().atZone(zoneId).toInstant().toEpochMilli();
                int requestCode = requestCode(task.getTaskKey(), reminderMillis);
                long triggerMillis = autoAt.atZone(zoneId).toInstant().toEpochMilli();
                String encoded = encodeScheduled(requestCode, task.getTaskKey(), reminderMillis);
                desiredEncoded.put(requestCode, encoded);

                PendingIntent pi = createPendingIntent(context, requestCode, task.getTaskKey(), reminderMillis);
                alarmManager.cancel(pi);

                if (ReminderScheduler.canScheduleExactAlarms(context)) {
                    alarmManager.setAlarmClock(
                            new AlarmManager.AlarmClockInfo(
                                    triggerMillis,
                                    createOpenAppPendingIntent(context, requestCode)
                            ),
                            pi
                    );
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerMillis, pi);
                }
            }
        }

        Set<Integer> desiredCodes = desiredEncoded.keySet();
        for (String raw : previousEncoded) {
            int code = parseRequestCode(raw);
            if (code >= 0 && !desiredCodes.contains(code)) {
                cancelAlarm(context, code);
            }
        }

        prefs.edit().putStringSet(KEY_SCHEDULED_CODES, new HashSet<>(desiredEncoded.values())).apply();
    }

    private static PendingIntent createOpenAppPendingIntent(Context context, int requestCode) {
        Intent intent = new Intent(context, com.regstar.obsidiannotification.ui.MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(
                context,
                requestCode + 17,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static void cancelAlarm(Context context, int requestCode) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }
        PendingIntent pi = PendingIntent.getBroadcast(
                context,
                requestCode,
                baseIntent(context),
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
        );
        if (pi != null) {
            alarmManager.cancel(pi);
            pi.cancel();
        }
    }

    static PendingIntent createPendingIntent(
            Context context,
            int requestCode,
            String taskKey,
            long expectedReminderMillis
    ) {
        Intent intent = baseIntent(context);
        intent.putExtra(EXTRA_TASK_KEY, taskKey);
        intent.putExtra(EXTRA_EXPECTED_REMINDER_AT_MILLIS, expectedReminderMillis);
        return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static Intent baseIntent(Context context) {
        return new Intent(context, AutoSkipReceiver.class).setAction(ACTION_AUTO_SKIP);
    }

    static int requestCode(String taskKey, long reminderAtEpochMillis) {
        int h = Objects.hash(taskKey, reminderAtEpochMillis);
        if (h == Integer.MIN_VALUE) {
            h = 0;
        }
        int mod = Math.floorMod(h, REQUEST_CODE_RANGE);
        return REQUEST_CODE_BASE + mod;
    }

    private static String encodeScheduled(int requestCode, String taskKey, long reminderMillis) {
        return requestCode + "|" + taskKey + "|" + reminderMillis;
    }

    private static int parseRequestCode(String encoded) {
        if (encoded == null || !encoded.contains("|")) {
            return -1;
        }
        try {
            return Integer.parseInt(encoded.substring(0, encoded.indexOf('|')));
        } catch (RuntimeException exception) {
            return -1;
        }
    }

    public static void cancelAllAlarms(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> previousEncoded = prefs.getStringSet(KEY_SCHEDULED_CODES, new HashSet<>());
        for (String raw : previousEncoded) {
            int code = parseRequestCode(raw);
            if (code >= 0) {
                cancelAlarm(context, code);
            }
        }
        prefs.edit().remove(KEY_SCHEDULED_CODES).apply();
    }
}
