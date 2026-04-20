package com.regstar.obsidiannotification;

import android.content.Context;

public final class UserPreferences {
    private static final String PREFS_NAME = "obsidian_notification_user_preferences";
    private static final String KEY_ACTIVE_ONLY = "active_only";

    private UserPreferences() {
    }

    public static boolean isActiveOnly(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_ACTIVE_ONLY, true);
    }

    public static void setActiveOnly(Context context, boolean activeOnly) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_ACTIVE_ONLY, activeOnly)
                .apply();
    }
}
