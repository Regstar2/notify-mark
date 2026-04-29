package com.regstar.obsidiannotification.prefs;

import android.content.Context;

public final class OnboardingPreferences {
    private static final String PREFS_NAME = "obsidian_notification_onboarding";
    private static final String KEY_COMPLETED = "completed";

    private OnboardingPreferences() {
    }

    public static boolean shouldShow(Context context) {
        return !context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_COMPLETED, false);
    }

    public static void markCompleted(Context context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_COMPLETED, true)
                .apply();
    }
}
