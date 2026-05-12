package com.regstar.obsidiannotification.prefs;

import android.content.Context;

/**
 * Global auto-skip settings: optional automatic skip after overdue grace plus delay.
 */
public final class AutoSkipPreferences {
    private static final String PREFS_NAME = "obsidian_notification_auto_skip_preferences";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_DELAY_MINUTES = "delay_minutes";

    /** Default delay when user enables auto-skip (2 hours). */
    public static final int DEFAULT_DELAY_MINUTES = 120;

    /** Same upper bound as overdue grace in {@link ActionPreferences} (one week). */
    public static final int MIN_DELAY_MINUTES = 1;
    public static final int MAX_DELAY_MINUTES = 7 * 24 * 60;

    /** Quick preset values for UI (minutes). */
    public static final int[] DELAY_PRESETS_MINUTES = {
            15, 30, 60, 120, 360, 24 * 60
    };

    private AutoSkipPreferences() {
    }

    public static boolean isEnabled(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_ENABLED, false);
    }

    public static void setEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_ENABLED, enabled)
                .apply();
    }

    public static int getDelayMinutes(Context context) {
        int raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getInt(KEY_DELAY_MINUTES, DEFAULT_DELAY_MINUTES);
        return clampDelayMinutes(raw);
    }

    public static void setDelayMinutes(Context context, int minutes) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_DELAY_MINUTES, clampDelayMinutes(minutes))
                .apply();
    }

    public static int clampDelayMinutes(int minutes) {
        if (minutes < MIN_DELAY_MINUTES) {
            return MIN_DELAY_MINUTES;
        }
        if (minutes > MAX_DELAY_MINUTES) {
            return MAX_DELAY_MINUTES;
        }
        return minutes;
    }

    /**
     * @deprecated Use {@link #clampDelayMinutes(int)}; kept for tests that referenced the old preset-only clamp.
     */
    @Deprecated
    public static int clampToPreset(int minutes) {
        return clampDelayMinutes(minutes);
    }
}
