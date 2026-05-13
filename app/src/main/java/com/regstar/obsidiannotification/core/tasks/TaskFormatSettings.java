package com.regstar.obsidiannotification.core.tasks;

import com.regstar.obsidiannotification.core.reminders.*;
import com.regstar.obsidiannotification.core.source.*;
import com.regstar.obsidiannotification.prefs.*;
import com.regstar.obsidiannotification.support.ErrorLog;

import android.content.Context;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class TaskFormatSettings {
    public static final String DEFAULT_DUE_KEYWORD = "due";
    public static final String DEFAULT_REPEAT_KEYWORD = "repeat";
    public static final String DEFAULT_REPEAT_UNTIL_DONE_KEYWORD = "repeatUntilDone";
    public static final String DEFAULT_TAG_KEYWORD = "tag";
    public static final String DEFAULT_PRIORITY_KEYWORD = "priority";
    public static final String DEFAULT_GROUP_KEYWORD = "group";

    private static final String PREFS_NAME = "obsidian_notification_task_format";
    private static final String KEY_DUE = "due_keyword";
    private static final String KEY_REPEAT = "repeat_keyword";
    private static final String KEY_REPEAT_UNTIL_DONE = "repeat_until_done_keyword";
    private static final String KEY_TAG = "tag_keyword";
    private static final String KEY_PRIORITY = "priority_keyword";
    private static final String KEY_GROUP = "group_keyword";
    private static final String KEY_COMPATIBILITY = "compatibility_mode";
    private static final String KEY_OBSIDIAN_DEFAULT_REMINDER_MINUTES = "obsidian_default_reminder_minutes";
    private static final int DEFAULT_OBSIDIAN_REMINDER_MINUTES = 9 * 60;

    private final String dueKeyword;
    private final String repeatKeyword;
    private final String repeatUntilDoneKeyword;
    private final String tagKeyword;
    private final String priorityKeyword;
    private final String groupKeyword;
    private final TaskFormatCompatibilityMode compatibilityMode;
    private final int obsidianDefaultReminderMinutes;

    private TaskFormatSettings(
            String dueKeyword,
            String repeatKeyword,
            String repeatUntilDoneKeyword,
            String tagKeyword,
            String priorityKeyword,
            String groupKeyword,
            TaskFormatCompatibilityMode compatibilityMode,
            int obsidianDefaultReminderMinutes
    ) {
        this.dueKeyword = dueKeyword;
        this.repeatKeyword = repeatKeyword;
        this.repeatUntilDoneKeyword = repeatUntilDoneKeyword;
        this.tagKeyword = tagKeyword;
        this.priorityKeyword = priorityKeyword;
        this.groupKeyword = groupKeyword;
        this.compatibilityMode = compatibilityMode == null
                ? TaskFormatCompatibilityMode.AUTO_MIXED
                : compatibilityMode;
        this.obsidianDefaultReminderMinutes = clampReminderMinutes(obsidianDefaultReminderMinutes);
    }

    public static TaskFormatSettings defaults() {
        return fromValues(
                DEFAULT_DUE_KEYWORD,
                DEFAULT_REPEAT_KEYWORD,
                DEFAULT_REPEAT_UNTIL_DONE_KEYWORD,
                DEFAULT_TAG_KEYWORD,
                DEFAULT_PRIORITY_KEYWORD,
                DEFAULT_GROUP_KEYWORD,
                TaskFormatCompatibilityMode.AUTO_MIXED,
                DEFAULT_OBSIDIAN_REMINDER_MINUTES
        );
    }

    public static TaskFormatSettings fromValues(
            String dueKeyword,
            String repeatKeyword,
            String repeatUntilDoneKeyword,
            String tagKeyword,
            String priorityKeyword,
            String groupKeyword
    ) {
        return fromValues(
                dueKeyword,
                repeatKeyword,
                repeatUntilDoneKeyword,
                tagKeyword,
                priorityKeyword,
                groupKeyword,
                TaskFormatCompatibilityMode.AUTO_MIXED,
                DEFAULT_OBSIDIAN_REMINDER_MINUTES
        );
    }

    public static TaskFormatSettings fromValues(
            String dueKeyword,
            String repeatKeyword,
            String repeatUntilDoneKeyword,
            String tagKeyword,
            String priorityKeyword,
            String groupKeyword,
            TaskFormatCompatibilityMode compatibilityMode,
            int obsidianDefaultReminderMinutes
    ) {
        return new TaskFormatSettings(
                normalizeKeyword(dueKeyword, DEFAULT_DUE_KEYWORD),
                normalizeKeyword(repeatKeyword, DEFAULT_REPEAT_KEYWORD),
                normalizeKeyword(repeatUntilDoneKeyword, DEFAULT_REPEAT_UNTIL_DONE_KEYWORD),
                normalizeKeyword(tagKeyword, DEFAULT_TAG_KEYWORD),
                normalizeKeyword(priorityKeyword, DEFAULT_PRIORITY_KEYWORD),
                normalizeKeyword(groupKeyword, DEFAULT_GROUP_KEYWORD),
                compatibilityMode,
                obsidianDefaultReminderMinutes
        );
    }

    public static TaskFormatSettings load(Context context) {
        return fromValues(
                get(context, KEY_DUE, DEFAULT_DUE_KEYWORD),
                get(context, KEY_REPEAT, DEFAULT_REPEAT_KEYWORD),
                get(context, KEY_REPEAT_UNTIL_DONE, DEFAULT_REPEAT_UNTIL_DONE_KEYWORD),
                get(context, KEY_TAG, DEFAULT_TAG_KEYWORD),
                get(context, KEY_PRIORITY, DEFAULT_PRIORITY_KEYWORD),
                get(context, KEY_GROUP, DEFAULT_GROUP_KEYWORD),
                parseCompatibilityMode(get(context, KEY_COMPATIBILITY, "auto")),
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .getInt(KEY_OBSIDIAN_DEFAULT_REMINDER_MINUTES, DEFAULT_OBSIDIAN_REMINDER_MINUTES)
        );
    }

    public static void save(Context context, TaskFormatSettings settings) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_DUE, settings.getDueKeyword())
                .putString(KEY_REPEAT, settings.getRepeatKeyword())
                .putString(KEY_REPEAT_UNTIL_DONE, settings.getRepeatUntilDoneKeyword())
                .putString(KEY_TAG, settings.getTagKeyword())
                .putString(KEY_PRIORITY, settings.getPriorityKeyword())
                .putString(KEY_GROUP, settings.getGroupKeyword())
                .putString(KEY_COMPATIBILITY, compatibilityToToken(settings.getCompatibilityMode()))
                .putInt(KEY_OBSIDIAN_DEFAULT_REMINDER_MINUTES, settings.getObsidianDefaultReminderMinutes())
                .apply();
    }

    public static void reset(Context context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply();
    }

    public String getDueKeyword() {
        return dueKeyword;
    }

    public String getRepeatKeyword() {
        return repeatKeyword;
    }

    public String getRepeatUntilDoneKeyword() {
        return repeatUntilDoneKeyword;
    }

    public String getTagKeyword() {
        return tagKeyword;
    }

    public String getPriorityKeyword() {
        return priorityKeyword;
    }

    public String getGroupKeyword() {
        return groupKeyword;
    }

    public TaskFormatCompatibilityMode getCompatibilityMode() {
        return compatibilityMode;
    }

    public LocalTime getObsidianDefaultReminderTime() {
        int m = obsidianDefaultReminderMinutes;
        return LocalTime.of(m / 60, m % 60);
    }

    public int getObsidianDefaultReminderMinutes() {
        return obsidianDefaultReminderMinutes;
    }

    public List<String> dueKeywords() {
        return keywords(dueKeyword, DEFAULT_DUE_KEYWORD);
    }

    public List<String> repeatKeywords() {
        return keywords(repeatKeyword, DEFAULT_REPEAT_KEYWORD, "r");
    }

    public List<String> repeatUntilDoneKeywords() {
        return keywords(
                repeatUntilDoneKeyword,
                DEFAULT_REPEAT_UNTIL_DONE_KEYWORD,
                "repeatUntildone",
                "repeat-until-done",
                "repeat_until_done",
                "untilDone",
                "rud"
        );
    }

    public List<String> tagKeywords() {
        return keywords(tagKeyword, DEFAULT_TAG_KEYWORD, "tags");
    }

    public List<String> priorityKeywords() {
        return keywords(priorityKeyword, DEFAULT_PRIORITY_KEYWORD, "prio", "p");
    }

    public List<String> groupKeywords() {
        return keywords(groupKeyword, DEFAULT_GROUP_KEYWORD, "grp", "category");
    }

    public boolean hasDuplicateKeywords() {
        Set<String> seen = new HashSet<>();
        List<String> allKeywords = new ArrayList<>();
        allKeywords.addAll(dueKeywords());
        allKeywords.addAll(repeatKeywords());
        allKeywords.addAll(repeatUntilDoneKeywords());
        allKeywords.addAll(tagKeywords());
        allKeywords.addAll(priorityKeywords());
        allKeywords.addAll(groupKeywords());
        for (String keyword : allKeywords) {
            String normalized = keyword.toLowerCase(java.util.Locale.ROOT);
            if (seen.contains(normalized)) {
                return true;
            }
            seen.add(normalized);
        }
        return false;
    }

    public TaskFormatSettings withCompatibilityMode(TaskFormatCompatibilityMode mode) {
        return fromValues(
                dueKeyword,
                repeatKeyword,
                repeatUntilDoneKeyword,
                tagKeyword,
                priorityKeyword,
                groupKeyword,
                mode == null ? compatibilityMode : mode,
                obsidianDefaultReminderMinutes
        );
    }

    public String formatForStatus() {
        return "@"
                + dueKeyword
                + "(...) @"
                + repeatKeyword
                + "(...) @"
                + repeatUntilDoneKeyword
                + "(...) @"
                + tagKeyword
                + "(...) @"
                + priorityKeyword
                + "(...) @"
                + groupKeyword
                + "(...)";
    }

    private static String get(Context context, String key, String fallback) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(key, fallback);
    }

    private static TaskFormatCompatibilityMode parseCompatibilityMode(String raw) {
        if (raw == null) {
            return TaskFormatCompatibilityMode.AUTO_MIXED;
        }
        String v = raw.trim().toLowerCase(Locale.ROOT);
        if ("native".equals(v)) {
            return TaskFormatCompatibilityMode.NATIVE;
        }
        if ("obsidian".equals(v) || "obsidian_tasks".equals(v)) {
            return TaskFormatCompatibilityMode.OBSIDIAN_TASKS;
        }
        return TaskFormatCompatibilityMode.AUTO_MIXED;
    }

    private static String compatibilityToToken(TaskFormatCompatibilityMode mode) {
        if (mode == TaskFormatCompatibilityMode.NATIVE) {
            return "native";
        }
        if (mode == TaskFormatCompatibilityMode.OBSIDIAN_TASKS) {
            return "obsidian";
        }
        return "auto";
    }

    private static int clampReminderMinutes(int minutes) {
        if (minutes < 0) {
            return DEFAULT_OBSIDIAN_REMINDER_MINUTES;
        }
        if (minutes >= 24 * 60) {
            return 23 * 60 + 59;
        }
        return minutes;
    }

    private static String normalizeKeyword(String value, String fallback) {
        if (value == null) {
            return fallback;
        }

        String normalized = value.trim();
        if (normalized.startsWith("@")) {
            normalized = normalized.substring(1).trim();
        }
        int functionStart = normalized.indexOf('(');
        if (functionStart >= 0) {
            normalized = normalized.substring(0, functionStart).trim();
        }
        return isValidKeyword(normalized) ? normalized : fallback;
    }

    private static boolean isValidKeyword(String value) {
        return value != null && value.matches("[\\p{L}\\p{N}_-]+");
    }

    private static List<String> keywords(String primary, String defaultKeyword, String... aliases) {
        List<String> result = new ArrayList<>();
        addKeyword(result, primary);
        addKeyword(result, defaultKeyword);
        for (String alias : aliases) {
            addKeyword(result, alias);
        }
        return result;
    }

    private static void addKeyword(List<String> keywords, String value) {
        if (value == null || value.trim().isEmpty()) {
            return;
        }
        for (String existing : keywords) {
            if (existing.equalsIgnoreCase(value)) {
                return;
            }
        }
        keywords.add(value);
    }
}
