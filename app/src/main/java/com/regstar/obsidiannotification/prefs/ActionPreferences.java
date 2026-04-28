package com.regstar.obsidiannotification.prefs;

import android.content.Context;

public final class ActionPreferences {
    private static final String PREFS_NAME = "obsidian_notification_action_preferences";
    private static final String KEY_SNOOZE_MINUTES = "snooze_minutes";
    private static final String KEY_RECORD_SNOOZE_COUNT = "record_snooze_count";
    private static final String KEY_OVERDUE_GRACE_MINUTES = "overdue_grace_minutes";
    private static final String KEY_REPEAT_UNTIL_DONE_MINUTES = "repeat_until_done_minutes";
    private static final int DEFAULT_SNOOZE_MINUTES = 10;
    private static final int DEFAULT_REPEAT_UNTIL_DONE_MINUTES = 15;
    private static final int DEFAULT_OVERDUE_GRACE_MINUTES = 0;
    private static final int MIN_SNOOZE_MINUTES = 1;
    private static final int MIN_REPEAT_UNTIL_DONE_MINUTES = 1;
    private static final int MAX_SNOOZE_MINUTES = 24 * 60;
    private static final int MAX_REPEAT_UNTIL_DONE_MINUTES = 24 * 60;
    private static final int MIN_OVERDUE_GRACE_MINUTES = 0;
    private static final int MAX_OVERDUE_GRACE_MINUTES = 7 * 24 * 60;

    private ActionPreferences() {
    }

    public static int getSnoozeMinutes(Context context) {
        return clampSnoozeMinutes(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getInt(KEY_SNOOZE_MINUTES, DEFAULT_SNOOZE_MINUTES));
    }

    public static void setSnoozeMinutes(Context context, int minutes) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_SNOOZE_MINUTES, clampSnoozeMinutes(minutes))
                .apply();
    }

    public static int getRepeatUntilDoneMinutes(Context context) {
        return clampRepeatUntilDoneMinutes(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getInt(KEY_REPEAT_UNTIL_DONE_MINUTES, DEFAULT_REPEAT_UNTIL_DONE_MINUTES));
    }

    public static void setRepeatUntilDoneMinutes(Context context, int minutes) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_REPEAT_UNTIL_DONE_MINUTES, clampRepeatUntilDoneMinutes(minutes))
                .apply();
    }

    public static boolean shouldRecordSnoozeCount(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_RECORD_SNOOZE_COUNT, false);
    }

    public static int getOverdueGraceMinutes(Context context) {
        return clampOverdueGraceMinutes(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getInt(KEY_OVERDUE_GRACE_MINUTES, DEFAULT_OVERDUE_GRACE_MINUTES));
    }

    public static void setOverdueGraceMinutes(Context context, int minutes) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_OVERDUE_GRACE_MINUTES, clampOverdueGraceMinutes(minutes))
                .apply();
    }

    public static void setRecordSnoozeCount(Context context, boolean enabled) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_RECORD_SNOOZE_COUNT, enabled)
                .apply();
    }

    public static int clampSnoozeMinutes(int minutes) {
        if (minutes < MIN_SNOOZE_MINUTES) {
            return MIN_SNOOZE_MINUTES;
        }
        if (minutes > MAX_SNOOZE_MINUTES) {
            return MAX_SNOOZE_MINUTES;
        }
        return minutes;
    }

    public static int clampRepeatUntilDoneMinutes(int minutes) {
        if (minutes < MIN_REPEAT_UNTIL_DONE_MINUTES) {
            return MIN_REPEAT_UNTIL_DONE_MINUTES;
        }
        if (minutes > MAX_REPEAT_UNTIL_DONE_MINUTES) {
            return MAX_REPEAT_UNTIL_DONE_MINUTES;
        }
        return minutes;
    }

    public static int clampOverdueGraceMinutes(int minutes) {
        if (minutes < MIN_OVERDUE_GRACE_MINUTES) {
            return MIN_OVERDUE_GRACE_MINUTES;
        }
        if (minutes > MAX_OVERDUE_GRACE_MINUTES) {
            return MAX_OVERDUE_GRACE_MINUTES;
        }
        return minutes;
    }
}
