package com.regstar.obsidiannotification;

import android.content.Context;

public final class UserPreferences {
    public static final String FILTER_ALL = "all";
    public static final String FILTER_ACTIVE = "active";
    public static final String FILTER_OVERDUE = "overdue";
    public static final String FILTER_COMPLETED = "completed";

    private static final String PREFS_NAME = "obsidian_notification_user_preferences";
    private static final String KEY_ACTIVE_ONLY = "active_only";
    private static final String KEY_TASK_FILTER = "task_filter";
    private static final String KEY_TASK_GROUP = "task_group";

    private UserPreferences() {
    }

    public static boolean isActiveOnly(Context context) {
        return FILTER_ACTIVE.equals(getTaskFilter(context));
    }

    public static void setActiveOnly(Context context, boolean activeOnly) {
        setTaskFilter(context, activeOnly ? FILTER_ACTIVE : FILTER_ALL);
    }

    public static String getTaskFilter(Context context) {
        String filter = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_TASK_FILTER, null);
        if (isKnownFilter(filter)) {
            return filter;
        }

        boolean legacyActiveOnly = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_ACTIVE_ONLY, true);
        return legacyActiveOnly ? FILTER_ACTIVE : FILTER_ALL;
    }

    public static void setTaskFilter(Context context, String filter) {
        String safeFilter = isKnownFilter(filter) ? filter : FILTER_ALL;
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_TASK_FILTER, safeFilter)
                .putBoolean(KEY_ACTIVE_ONLY, FILTER_ACTIVE.equals(safeFilter))
                .apply();
    }

    public static String getTaskFilterLabel(Context context) {
        String filter = getTaskFilter(context);
        if (FILTER_ACTIVE.equals(filter)) {
            return "активные";
        }
        if (FILTER_OVERDUE.equals(filter)) {
            return "просроченные";
        }
        if (FILTER_COMPLETED.equals(filter)) {
            return "завершенные";
        }
        return "все";
    }

    public static String getTaskGroup(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_TASK_GROUP, "");
    }

    public static void setTaskGroup(Context context, String group) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_TASK_GROUP, group == null ? "" : group.trim())
                .apply();
    }

    private static boolean isKnownFilter(String filter) {
        return FILTER_ALL.equals(filter)
                || FILTER_ACTIVE.equals(filter)
                || FILTER_OVERDUE.equals(filter)
                || FILTER_COMPLETED.equals(filter);
    }
}
