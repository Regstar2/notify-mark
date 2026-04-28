package com.regstar.obsidiannotification;

import android.content.Context;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TaskCache {
    private static final String PREFS_NAME = "obsidian_notification_task_cache";
    private static final String KEY_ACTIVE_TASKS = "active_tasks";
    private static final String KEY_SAVED_AT = "saved_at";
    private static final String KEY_TASK_COUNT = "task_count";

    private TaskCache() {
    }

    public static void saveActiveTasks(Context context, List<ObsidianTask> tasks) {
        StringBuilder builder = new StringBuilder();
        for (ObsidianTask task : tasks) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(encodeTask(task));
        }

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_ACTIVE_TASKS, builder.toString())
                .putString(KEY_SAVED_AT, LocalDateTime.now().toString())
                .putInt(KEY_TASK_COUNT, tasks.size())
                .apply();
    }

    public static List<ObsidianTask> loadActiveTasks(Context context) {
        String raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_ACTIVE_TASKS, "");
        List<ObsidianTask> tasks = new ArrayList<>();
        if (raw == null || raw.isEmpty()) {
            return tasks;
        }

        for (String line : raw.split("\\n")) {
            if (line.trim().isEmpty()) {
                continue;
            }
            ObsidianTask task = decodeTask(line);
            if (task != null) {
                tasks.add(task);
            }
        }
        rebuildSubtaskHierarchy(tasks);
        return tasks;
    }

    public static int getCachedTaskCount(Context context) {
        android.content.SharedPreferences preferences =
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (preferences.contains(KEY_TASK_COUNT)) {
            return preferences.getInt(KEY_TASK_COUNT, 0);
        }
        return loadActiveTasks(context).size();
    }

    public static String getSavedAt(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_SAVED_AT, null);
    }

    public static boolean hasCachedTasks(Context context) {
        return getCachedTaskCount(context) > 0;
    }

    private static String encodeTask(ObsidianTask task) {
        String reminderAt = task.getReminderAt() == null ? "" : task.getReminderAt().toString();
        String repeatRuleSpec = task.getRepeatRule() == null ? "" : task.getRepeatRule().formatForUi();
        return encode(task.getTaskKey())
                + "|"
                + encode(task.getSeriesId())
                + "|"
                + encode(task.getSourceName())
                + "|"
                + task.getLineNumber()
                + "|"
                + encode(task.getTitle())
                + "|"
                + encode(task.getRawLine())
                + "|"
                + encode(reminderAt)
                + "|"
                + task.getRepeatIntervalMillis()
                + "|"
                + task.getRepeatMode().name()
                + "|"
                + encode(repeatRuleSpec)
                + "|"
                + encode(durationValue(task.getExplicitRepeatUntilDoneInterval()))
                + "|"
                + encode(durationValue(task.getResolvedRepeatUntilDoneInterval()))
                + "|"
                + encode(durationValue(task.getExplicitOverdueGracePeriod()))
                + "|"
                + encode(durationValue(task.getResolvedOverdueGracePeriod()))
                + "|"
                + encode(durationValue(task.getSnoozeDuration()))
                + "|"
                + encode(task.getParentTaskKey())
                + "|"
                + task.getParentLineNumber()
                + "|"
                + task.getIndentLevel()
                + "|"
                + encode(joinTags(task.getTags()))
                + "|"
                + task.getPriority().name()
                + "|"
                + encode(task.getGroup());
    }

    private static ObsidianTask decodeTask(String encoded) {
        String[] parts = encoded.split("\\|", -1);
        if (parts.length != 8
                && parts.length != 10
                && parts.length != 11
                && parts.length != 18
                && parts.length != 21) {
            return null;
        }

        try {
            String taskKey = decode(parts[0]);
            boolean extended = parts.length == 18 || parts.length == 21;
            boolean hierarchyExtended = parts.length == 21;
            String seriesId = extended ? decode(parts[1]) : "";
            int offset = extended ? 1 : 0;
            String sourceName = decode(parts[1 + offset]);
            int lineNumber = Integer.parseInt(parts[2 + offset]);
            String title = decode(parts[3 + offset]);
            String rawLine = decode(parts[4 + offset]);
            String reminderAtText = decode(parts[5 + offset]);
            LocalDateTime reminderAt = reminderAtText.isEmpty()
                    ? null
                    : LocalDateTime.parse(reminderAtText);
            long repeatMillis = Long.parseLong(parts[6 + offset]);
            Duration repeatInterval = repeatMillis > 0 ? Duration.ofMillis(repeatMillis) : null;
            RepeatMode repeatMode = RepeatMode.fromName(parts[7 + offset]);
            RepeatRule repeatRule = extended
                    ? RepeatRule.parseStoredSpec(decode(parts[8 + offset]))
                    : null;
            Duration explicitRepeatUntilDone = extended ? parseDurationValue(parts[9 + offset]) : null;
            Duration resolvedRepeatUntilDone = extended ? parseDurationValue(parts[10 + offset]) : null;
            Duration explicitGrace = extended ? parseDurationValue(parts[11 + offset]) : null;
            Duration resolvedGrace = extended ? parseDurationValue(parts[12 + offset]) : null;
            Duration snoozeDuration = extended ? parseDurationValue(parts[13 + offset]) : null;
            String parentTaskKey = hierarchyExtended ? decode(parts[14 + offset]) : "";
            int parentLineNumber = hierarchyExtended ? Integer.parseInt(parts[15 + offset]) : 0;
            int indentLevel = hierarchyExtended ? Integer.parseInt(parts[16 + offset]) : 0;
            int tagsIndex = hierarchyExtended ? 17 + offset : (extended ? 14 + offset : 8);
            int priorityIndex = hierarchyExtended ? 18 + offset : (extended ? 15 + offset : 9);
            int groupIndex = hierarchyExtended ? 19 + offset : (extended ? 16 + offset : 10);
            List<String> tags = parts.length > tagsIndex
                    ? splitTags(decode(parts[tagsIndex]))
                    : new ArrayList<>();
            TaskPriority priority = parts.length > priorityIndex
                    ? TaskPriority.fromName(parts[priorityIndex])
                    : TaskPriority.NONE;
            String group = parts.length > groupIndex
                    ? ObsidianTask.normalizeGroup(decode(parts[groupIndex]))
                    : ObsidianTask.DEFAULT_GROUP;
            return new ObsidianTask(
                    taskKey,
                    sourceName,
                    lineNumber,
                    title,
                    rawLine,
                    reminderAt,
                    repeatInterval,
                    repeatMode,
                    false,
                    false,
                    parentTaskKey,
                    parentLineNumber,
                    indentLevel,
                    tags,
                    priority,
                    group,
                    snoozeDuration,
                    explicitGrace,
                    resolvedGrace,
                    explicitRepeatUntilDone,
                    resolvedRepeatUntilDone,
                    repeatRule,
                    seriesId
            );
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static void rebuildSubtaskHierarchy(List<ObsidianTask> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return;
        }
        Map<String, ObsidianTask> tasksByKey = new LinkedHashMap<>();
        for (ObsidianTask task : tasks) {
            tasksByKey.put(task.getTaskKey(), task);
        }
        for (ObsidianTask task : tasks) {
            if (!task.isSubtask()) {
                continue;
            }
            ObsidianTask parent = tasksByKey.get(task.getParentTaskKey());
            if (parent != null) {
                parent.addSubtask(task);
            }
        }
    }

    private static String joinTags(List<String> tags) {
        StringBuilder builder = new StringBuilder();
        for (String tag : tags) {
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(tag);
        }
        return builder.toString();
    }

    private static List<String> splitTags(String rawTags) {
        List<String> tags = new ArrayList<>();
        if (rawTags == null || rawTags.trim().isEmpty()) {
            return tags;
        }

        for (String tag : rawTags.split(",")) {
            if (!tag.trim().isEmpty()) {
                tags.add(tag.trim());
            }
        }
        return tags;
    }

    private static String encode(String value) {
        String safeValue = value == null ? "" : value;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                safeValue.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static String decode(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private static String durationValue(Duration duration) {
        return duration == null ? "" : String.valueOf(duration.toMillis());
    }

    private static Duration parseDurationValue(String encoded) {
        String value = decode(encoded);
        if (value.isEmpty()) {
            return null;
        }
        long millis = Long.parseLong(value);
        return millis <= 0L ? Duration.ZERO : Duration.ofMillis(millis);
    }
}
