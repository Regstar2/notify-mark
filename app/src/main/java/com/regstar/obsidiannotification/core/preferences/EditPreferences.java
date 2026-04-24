package com.regstar.obsidiannotification.core.preferences;

import android.content.Context;

public final class EditPreferences {
    public static final String MODE_UI = "ui";
    public static final String MODE_MARKDOWN = "markdown";

    private static final String PREFS_NAME = "obsidian_notification_edit_preferences";
    private static final String KEY_MODE = "edit_mode";

    private EditPreferences() {
    }

    public static String getEditMode(Context context) {
        String mode = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_MODE, MODE_UI);
        return MODE_MARKDOWN.equals(mode) ? MODE_MARKDOWN : MODE_UI;
    }

    public static void setEditMode(Context context, String mode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_MODE, MODE_MARKDOWN.equals(mode) ? MODE_MARKDOWN : MODE_UI)
                .apply();
    }
}


