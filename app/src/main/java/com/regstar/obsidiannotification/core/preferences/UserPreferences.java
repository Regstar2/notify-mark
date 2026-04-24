package com.regstar.obsidiannotification.core.preferences;

import android.content.Context;

public final class UserPreferences {
    public static final String FILTER_ALL = "all";
    public static final String FILTER_ACTIVE = "active";
    public static final String FILTER_OVERDUE = "overdue";
    public static final String FILTER_COMPLETED = "completed";
    public static final String FILTER_SKIPPED = "skipped";
    public static final String GROUPING_SMART = "smart";
    public static final String GROUPING_GROUP = "group";
    public static final String GROUPING_TAG = "tag";
    public static final String GROUPING_FILE = "file";
    public static final String DATE_FORMAT_DMY = "dmy";
    public static final String DATE_FORMAT_YMD = "ymd";

    private static final String PREFS_NAME = "obsidian_notification_user_preferences";
    private static final String KEY_ACTIVE_ONLY = "active_only";
    private static final String KEY_TASK_FILTER = "task_filter";
    private static final String KEY_TASK_GROUP = "task_group";
    private static final String KEY_GROUPING_MODE = "grouping_mode";
    private static final String KEY_DATE_FORMAT = "date_format";
    private static final String KEY_SHOW_SOURCE_ON_MAIN = "show_source_on_main";
    private static final String KEY_PRIVATE_MARKER = "private_marker";
    private static final String DEFAULT_PRIVATE_MARKER = "private";

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
            return "Р В Р’В Р вЂ™Р’В°Р В Р’В Р РЋРІР‚СњР В Р Р‹Р Р†Р вЂљРЎв„ўР В Р’В Р РЋРІР‚ВР В Р’В Р В РІР‚В Р В Р’В Р В РІР‚В¦Р В Р Р‹Р Р†Р вЂљРІвЂћвЂ“Р В Р’В Р вЂ™Р’Вµ";
        }
        if (FILTER_OVERDUE.equals(filter)) {
            return "Р В Р’В Р РЋРІР‚вЂќР В Р Р‹Р В РІР‚С™Р В Р’В Р РЋРІР‚СћР В Р Р‹Р В РЎвЂњР В Р Р‹Р В РІР‚С™Р В Р’В Р РЋРІР‚СћР В Р Р‹Р Р†Р вЂљР Р‹Р В Р’В Р вЂ™Р’ВµР В Р’В Р В РІР‚В¦Р В Р’В Р В РІР‚В¦Р В Р Р‹Р Р†Р вЂљРІвЂћвЂ“Р В Р’В Р вЂ™Р’Вµ";
        }
        if (FILTER_COMPLETED.equals(filter)) {
            return "Р В Р’В Р вЂ™Р’В·Р В Р’В Р вЂ™Р’В°Р В Р’В Р В РІР‚В Р В Р’В Р вЂ™Р’ВµР В Р Р‹Р В РІР‚С™Р В Р Р‹Р Р†РІР‚С™Р’В¬Р В Р’В Р вЂ™Р’ВµР В Р’В Р В РІР‚В¦Р В Р’В Р В РІР‚В¦Р В Р Р‹Р Р†Р вЂљРІвЂћвЂ“Р В Р’В Р вЂ™Р’Вµ";
        }
        if (FILTER_SKIPPED.equals(filter)) {
            return "Р В Р’В Р РЋРІР‚вЂќР В Р Р‹Р В РІР‚С™Р В Р’В Р РЋРІР‚СћР В Р’В Р РЋРІР‚вЂќР В Р Р‹Р РЋРІР‚СљР В Р Р‹Р Р†Р вЂљР’В°Р В Р’В Р вЂ™Р’ВµР В Р’В Р В РІР‚В¦Р В Р’В Р В РІР‚В¦Р В Р Р‹Р Р†Р вЂљРІвЂћвЂ“Р В Р’В Р вЂ™Р’Вµ";
        }
        return "Р В Р’В Р В РІР‚В Р В Р Р‹Р В РЎвЂњР В Р’В Р вЂ™Р’Вµ";
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

    public static String getGroupingMode(Context context) {
        String mode = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_GROUPING_MODE, GROUPING_SMART);
        return isKnownGroupingMode(mode) ? mode : GROUPING_SMART;
    }

    public static void setGroupingMode(Context context, String mode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_GROUPING_MODE, isKnownGroupingMode(mode) ? mode : GROUPING_SMART)
                .putString(KEY_TASK_GROUP, "")
                .apply();
    }

    public static String getGroupingModeLabel(Context context) {
        String mode = getGroupingMode(context);
        if (GROUPING_GROUP.equals(mode)) {
            return "@group";
        }
        if (GROUPING_TAG.equals(mode)) {
            return "Р В Р Р‹Р Р†Р вЂљРЎв„ўР В Р’В Р вЂ™Р’ВµР В Р’В Р РЋРІР‚вЂњР В Р’В Р РЋРІР‚В";
        }
        if (GROUPING_FILE.equals(mode)) {
            return "Р В Р Р‹Р Р†Р вЂљРЎвЂєР В Р’В Р вЂ™Р’В°Р В Р’В Р Р†РІР‚С›РІР‚вЂњР В Р’В Р вЂ™Р’В»Р В Р Р‹Р Р†Р вЂљРІвЂћвЂ“";
        }
        return "Р В Р Р‹Р В РЎвЂњР В Р’В Р РЋР’ВР В Р’В Р вЂ™Р’ВµР В Р Р‹Р Р†РІР‚С™Р’В¬Р В Р’В Р вЂ™Р’В°Р В Р’В Р В РІР‚В¦Р В Р’В Р В РІР‚В¦Р В Р’В Р вЂ™Р’В°Р В Р Р‹Р В Р РЏ";
    }

    public static String getDateFormat(Context context) {
        String mode = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_DATE_FORMAT, DATE_FORMAT_DMY);
        return isKnownDateFormat(mode) ? mode : DATE_FORMAT_DMY;
    }

    public static void setDateFormat(Context context, String mode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_DATE_FORMAT, isKnownDateFormat(mode) ? mode : DATE_FORMAT_DMY)
                .apply();
    }

    public static String getDateFormatLabel(Context context) {
        return DATE_FORMAT_YMD.equals(getDateFormat(context))
                ? "\u0433\u043e\u0434-\u043c\u0435\u0441\u044f\u0446-\u0434\u0435\u043d\u044c"
                : "\u0434\u0435\u043d\u044c.\u043c\u0435\u0441\u044f\u0446.\u0433\u043e\u0434";
    }

    public static boolean shouldShowSourceOnMain(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_SHOW_SOURCE_ON_MAIN, false);
    }

    public static void setShowSourceOnMain(Context context, boolean show) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_SHOW_SOURCE_ON_MAIN, show)
                .apply();
    }

    public static String getPrivateMarker(Context context) {
        android.content.SharedPreferences preferences =
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (!preferences.contains(KEY_PRIVATE_MARKER)) {
            return DEFAULT_PRIVATE_MARKER;
        }
        return normalizePrivateMarker(preferences.getString(KEY_PRIVATE_MARKER, ""));
    }

    public static void setPrivateMarker(Context context, String marker) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_PRIVATE_MARKER, normalizePrivateMarker(marker))
                .putString(KEY_TASK_GROUP, "")
                .apply();
    }

    public static String normalizePrivateMarker(String marker) {
        if (marker == null) {
            return "";
        }
        String normalized = marker.trim();
        while (normalized.startsWith("#") || normalized.startsWith("@")) {
            normalized = normalized.substring(1).trim();
        }
        return normalized.replaceAll("\\s{2,}", " ");
    }

    private static boolean isKnownFilter(String filter) {
        return FILTER_ALL.equals(filter)
                || FILTER_ACTIVE.equals(filter)
                || FILTER_OVERDUE.equals(filter)
                || FILTER_COMPLETED.equals(filter)
                || FILTER_SKIPPED.equals(filter);
    }

    private static boolean isKnownGroupingMode(String mode) {
        return GROUPING_SMART.equals(mode)
                || GROUPING_GROUP.equals(mode)
                || GROUPING_TAG.equals(mode)
                || GROUPING_FILE.equals(mode);
    }

    private static boolean isKnownDateFormat(String mode) {
        return DATE_FORMAT_DMY.equals(mode) || DATE_FORMAT_YMD.equals(mode);
    }
}


