package com.regstar.obsidiannotification;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ObsidianTask {
    private final String taskKey;
    private final String sourceName;
    private final int lineNumber;
    private final String title;
    private final String rawLine;
    private final LocalDateTime reminderAt;
    private final Duration repeatInterval;
    private final RepeatMode repeatMode;
    private final boolean completed;
    private final List<String> tags;
    private final TaskPriority priority;

    public ObsidianTask(
            int lineNumber,
            String title,
            String rawLine,
            LocalDateTime reminderAt,
            Duration repeatInterval
    ) {
        this(lineNumber, title, rawLine, reminderAt, repeatInterval, defaultRepeatMode(repeatInterval));
    }

    public ObsidianTask(
            int lineNumber,
            String title,
            String rawLine,
            LocalDateTime reminderAt,
            Duration repeatInterval,
            RepeatMode repeatMode
    ) {
        this(
                createTaskKey("", lineNumber, title, reminderAt, repeatInterval, repeatMode),
                "",
                lineNumber,
                title,
                rawLine,
                reminderAt,
                repeatInterval,
                repeatMode,
                false,
                Collections.emptyList(),
                TaskPriority.NONE
        );
    }

    public ObsidianTask(
            String taskKey,
            String sourceName,
            int lineNumber,
            String title,
            String rawLine,
            LocalDateTime reminderAt,
            Duration repeatInterval,
            RepeatMode repeatMode,
            boolean completed
    ) {
        this(
                taskKey,
                sourceName,
                lineNumber,
                title,
                rawLine,
                reminderAt,
                repeatInterval,
                repeatMode,
                completed,
                Collections.emptyList(),
                TaskPriority.NONE
        );
    }

    public ObsidianTask(
            String taskKey,
            String sourceName,
            int lineNumber,
            String title,
            String rawLine,
            LocalDateTime reminderAt,
            Duration repeatInterval,
            RepeatMode repeatMode,
            boolean completed,
            List<String> tags,
            TaskPriority priority
    ) {
        this.taskKey = taskKey;
        this.sourceName = sourceName == null ? "" : sourceName;
        this.lineNumber = lineNumber;
        this.title = title;
        this.rawLine = rawLine;
        this.reminderAt = reminderAt;
        this.repeatInterval = repeatInterval;
        this.repeatMode = repeatMode == null ? RepeatMode.NONE : repeatMode;
        this.completed = completed;
        this.tags = Collections.unmodifiableList(new ArrayList<>(tags == null
                ? Collections.emptyList()
                : tags));
        this.priority = priority == null ? TaskPriority.NONE : priority;
    }

    public String getTaskKey() {
        return taskKey;
    }

    public String getSourceName() {
        return sourceName;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public String getTitle() {
        return title;
    }

    public String getRawLine() {
        return rawLine;
    }

    public LocalDateTime getReminderAt() {
        return reminderAt;
    }

    public Duration getRepeatInterval() {
        return repeatInterval;
    }

    public RepeatMode getRepeatMode() {
        return repeatMode;
    }

    public boolean isCompleted() {
        return completed;
    }

    public List<String> getTags() {
        return tags;
    }

    public TaskPriority getPriority() {
        return priority;
    }

    public TaskStatus getStatus(LocalDateTime now) {
        return TaskStatus.forTask(this, now);
    }

    public long getRepeatIntervalMillis() {
        return repeatInterval == null ? 0L : repeatInterval.toMillis();
    }

    public static String createTaskKey(
            String sourceName,
            int lineNumber,
            String title,
            LocalDateTime reminderAt,
            Duration repeatInterval,
            RepeatMode repeatMode
    ) {
        String normalizedTitle = title == null
                ? ""
                : title.replaceAll("\\s{2,}", " ").trim();
        String reminderPart = reminderAt == null ? "" : reminderAt.toString();
        String repeatPart = repeatInterval == null ? "" : String.valueOf(repeatInterval.toMillis());
        String modePart = repeatMode == null ? RepeatMode.NONE.name() : repeatMode.name();
        String sourcePart = sourceName == null ? "" : sourceName.trim();
        return sourcePart + "|" + lineNumber + "|" + normalizedTitle + "|"
                + reminderPart + "|" + repeatPart + "|" + modePart;
    }

    private static RepeatMode defaultRepeatMode(Duration repeatInterval) {
        return repeatInterval == null ? RepeatMode.NONE : RepeatMode.ALWAYS;
    }
}
