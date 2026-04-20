package com.regstar.obsidiannotification;

import android.content.Context;

public final class ActionPreferences {
    private static final String PREFS_NAME = "obsidian_notification_action_preferences";
    private static final String KEY_SNOOZE_MINUTES = "snooze_minutes";
    private static final String KEY_RECORD_SNOOZE_COUNT = "record_snooze_count";
    private static final int DEFAULT_SNOOZE_MINUTES = 10;
    private static final int MIN_SNOOZE_MINUTES = 1;
    private static final int MAX_SNOOZE_MINUTES = 24 * 60;

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

    public static boolean shouldRecordSnoozeCount(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_RECORD_SNOOZE_COUNT, false);
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
}
