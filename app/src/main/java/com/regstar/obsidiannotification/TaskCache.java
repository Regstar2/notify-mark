package com.regstar.obsidiannotification;

import android.content.Context;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

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
        return encode(task.getTaskKey())
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
                + encode(joinTags(task.getTags()))
                + "|"
                + task.getPriority().name()
                + "|"
                + encode(task.getGroup());
    }

    private static ObsidianTask decodeTask(String encoded) {
        String[] parts = encoded.split("\\|", -1);
        if (parts.length != 8 && parts.length != 10 && parts.length != 11) {
            return null;
        }

        try {
            String taskKey = decode(parts[0]);
            String sourceName = decode(parts[1]);
            int lineNumber = Integer.parseInt(parts[2]);
            String title = decode(parts[3]);
            String rawLine = decode(parts[4]);
            String reminderAtText = decode(parts[5]);
            LocalDateTime reminderAt = reminderAtText.isEmpty()
                    ? null
                    : LocalDateTime.parse(reminderAtText);
            long repeatMillis = Long.parseLong(parts[6]);
            Duration repeatInterval = repeatMillis > 0 ? Duration.ofMillis(repeatMillis) : null;
            RepeatMode repeatMode = RepeatMode.fromName(parts[7]);
            List<String> tags = parts.length >= 10 ? splitTags(decode(parts[8])) : new ArrayList<>();
            TaskPriority priority = parts.length >= 10
                    ? TaskPriority.fromName(parts[9])
                    : TaskPriority.NONE;
            String group = parts.length >= 11
                    ? ObsidianTask.normalizeGroup(decode(parts[10]))
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
                    tags,
                    priority,
                    group
            );
        } catch (RuntimeException exception) {
            return null;
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
}
