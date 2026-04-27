package com.regstar.obsidiannotification;

import android.content.Context;

public final class TaskSyntaxPreferences {
    public static final String TAG_FORMAT_HASH = "hash";
    public static final String TAG_FORMAT_FUNCTION = "function";
    public static final String REPEAT_SYNTAX_CANONICAL = "canonical";
    public static final String REPEAT_SYNTAX_COMPACT = "compact";

    private static final String PREFS_NAME = "obsidian_notification_task_syntax";
    private static final String KEY_TAG_FORMAT = "tag_format";
    private static final String KEY_REPEAT_SYNTAX = "repeat_syntax";

    private TaskSyntaxPreferences() {
    }

    public static String getTagFormat(Context context) {
        return normalizeTagFormat(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_TAG_FORMAT, TAG_FORMAT_HASH));
    }

    public static void setTagFormat(Context context, String value) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_TAG_FORMAT, normalizeTagFormat(value))
                .apply();
    }

    public static String getRepeatSyntax(Context context) {
        return normalizeRepeatSyntax(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_REPEAT_SYNTAX, REPEAT_SYNTAX_CANONICAL));
    }

    public static void setRepeatSyntax(Context context, String value) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_REPEAT_SYNTAX, normalizeRepeatSyntax(value))
                .apply();
    }

    public static boolean useCompactSyntax(Context context) {
        return REPEAT_SYNTAX_COMPACT.equals(getRepeatSyntax(context));
    }

    public static boolean useHashTags(Context context) {
        return TAG_FORMAT_HASH.equals(getTagFormat(context));
    }

    private static String normalizeTagFormat(String value) {
        return TAG_FORMAT_FUNCTION.equals(value) ? TAG_FORMAT_FUNCTION : TAG_FORMAT_HASH;
    }

    private static String normalizeRepeatSyntax(String value) {
        return REPEAT_SYNTAX_COMPACT.equals(value)
                ? REPEAT_SYNTAX_COMPACT
                : REPEAT_SYNTAX_CANONICAL;
    }
}
