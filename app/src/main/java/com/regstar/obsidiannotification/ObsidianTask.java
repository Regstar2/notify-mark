package com.regstar.obsidiannotification;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ObsidianTask {
    public static final String DEFAULT_GROUP = "Общее";

    private final String taskKey;
    private final String sourceName;
    private final int lineNumber;
    private final String title;
    private final String rawLine;
    private final LocalDateTime reminderAt;
    private final Duration repeatInterval;
    private final RepeatMode repeatMode;
    private final boolean completed;
    private final boolean skipped;
    private final String parentTaskKey;
    private final int parentLineNumber;
    private final int indentLevel;
    private final List<ObsidianTask> subtasks;
    private final List<String> tags;
    private final TaskPriority priority;
    private final String group;
    private final Duration snoozeDuration;
    private final Duration overdueGracePeriod;

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
                false,
                "",
                0,
                0,
                Collections.emptyList(),
                TaskPriority.NONE,
                DEFAULT_GROUP,
                null,
                null
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
                false,
                "",
                0,
                0,
                Collections.emptyList(),
                TaskPriority.NONE,
                DEFAULT_GROUP,
                null,
                null
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
                false,
                "",
                0,
                0,
                tags,
                priority,
                DEFAULT_GROUP,
                null,
                null
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
            TaskPriority priority,
            String group
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
                false,
                "",
                0,
                0,
                tags,
                priority,
                group,
                null,
                null
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
            boolean skipped,
            List<String> tags,
            TaskPriority priority,
            String group
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
                skipped,
                "",
                0,
                0,
                tags,
                priority,
                group,
                null,
                null
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
            boolean skipped,
            String parentTaskKey,
            int parentLineNumber,
            int indentLevel,
            List<String> tags,
            TaskPriority priority,
            String group,
            Duration snoozeDuration,
            Duration overdueGracePeriod
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
        this.skipped = skipped;
        this.parentTaskKey = parentTaskKey == null ? "" : parentTaskKey;
        this.parentLineNumber = Math.max(0, parentLineNumber);
        this.indentLevel = Math.max(0, indentLevel);
        this.subtasks = new ArrayList<>();
        this.tags = Collections.unmodifiableList(new ArrayList<>(tags == null
                ? Collections.emptyList()
                : tags));
        this.priority = priority == null ? TaskPriority.NONE : priority;
        this.group = normalizeGroup(group);
        this.snoozeDuration = snoozeDuration;
        this.overdueGracePeriod = overdueGracePeriod;
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

    public boolean isSkipped() {
        return skipped;
    }

    public String getParentTaskKey() {
        return parentTaskKey;
    }

    public int getParentLineNumber() {
        return parentLineNumber;
    }

    public int getIndentLevel() {
        return indentLevel;
    }

    public boolean isSubtask() {
        return !parentTaskKey.isEmpty();
    }

    public List<ObsidianTask> getSubtasks() {
        return Collections.unmodifiableList(subtasks);
    }

    void addSubtask(ObsidianTask subtask) {
        if (subtask != null) {
            subtasks.add(subtask);
        }
    }

    public List<String> getTags() {
        return tags;
    }

    public TaskPriority getPriority() {
        return priority;
    }

    public String getGroup() {
        return group;
    }

    public Duration getSnoozeDuration() {
        return snoozeDuration;
    }

    public Duration getOverdueGracePeriod() {
        return overdueGracePeriod;
    }

    public TaskStatus getStatus(LocalDateTime now) {
        return TaskStatus.forTask(this, now);
    }

    public TaskStatus getStatus(LocalDateTime now, Duration overdueGracePeriod) {
        return TaskStatus.forTask(this, now, overdueGracePeriod);
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

    public static String normalizeGroup(String value) {
        if (value == null) {
            return DEFAULT_GROUP;
        }
        String normalized = value.replaceAll("\\s{2,}", " ").trim();
        return normalized.isEmpty() ? DEFAULT_GROUP : normalized;
    }

    private static RepeatMode defaultRepeatMode(Duration repeatInterval) {
        return repeatInterval == null ? RepeatMode.NONE : RepeatMode.ALWAYS;
    }
}
