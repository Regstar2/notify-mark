package com.regstar.obsidiannotification.core.preferences;

import com.regstar.obsidiannotification.R;

import android.app.Activity;
import android.app.UiModeManager;
import android.content.Context;
import android.os.Build;

public final class ThemePreferences {
    public static final String MODE_SYSTEM = "system";
    public static final String MODE_LIGHT = "light";
    public static final String MODE_DARK = "dark";

    private static final String PREFS_NAME = "obsidian_notification_theme_preferences";
    private static final String KEY_MODE = "theme_mode";

    private ThemePreferences() {
    }

    public static String getThemeMode(Context context) {
        String mode = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_MODE, MODE_SYSTEM);
        if (MODE_LIGHT.equals(mode) || MODE_DARK.equals(mode)) {
            return mode;
        }
        return MODE_SYSTEM;
    }

    public static void setThemeMode(Context context, String mode) {
        applySystemNightMode(context, normalize(mode));
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_MODE, normalize(mode))
                .apply();
    }

    public static void apply(Activity activity) {
        String mode = getThemeMode(activity);
        applySystemNightMode(activity, mode);
        if (MODE_LIGHT.equals(mode)) {
            activity.setTheme(R.style.AppThemeLight);
        } else if (MODE_DARK.equals(mode)) {
            activity.setTheme(R.style.AppThemeDark);
        }
    }

    public static void applySheet(Activity activity) {
        String mode = getThemeMode(activity);
        applySystemNightMode(activity, mode);
        if (MODE_LIGHT.equals(mode)) {
            activity.setTheme(R.style.TaskEditSheetThemeLight);
        } else if (MODE_DARK.equals(mode)) {
            activity.setTheme(R.style.TaskEditSheetThemeDark);
        } else {
            activity.setTheme(R.style.TaskEditSheetTheme);
        }
    }

    private static String normalize(String mode) {
        if (MODE_LIGHT.equals(mode) || MODE_DARK.equals(mode)) {
            return mode;
        }
        return MODE_SYSTEM;
    }

    private static void applySystemNightMode(Context context, String mode) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return;
        }

        UiModeManager uiModeManager = context.getSystemService(UiModeManager.class);
        if (uiModeManager == null) {
            return;
        }
        if (MODE_LIGHT.equals(mode)) {
            uiModeManager.setApplicationNightMode(UiModeManager.MODE_NIGHT_NO);
        } else if (MODE_DARK.equals(mode)) {
            uiModeManager.setApplicationNightMode(UiModeManager.MODE_NIGHT_YES);
        } else {
            uiModeManager.setApplicationNightMode(UiModeManager.MODE_NIGHT_AUTO);
        }
    }
}


