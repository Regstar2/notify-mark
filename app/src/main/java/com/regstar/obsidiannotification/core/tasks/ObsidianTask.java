package com.regstar.obsidiannotification.core.tasks;

import com.regstar.obsidiannotification.core.reminders.*;
import com.regstar.obsidiannotification.core.source.*;
import com.regstar.obsidiannotification.prefs.*;
import com.regstar.obsidiannotification.support.ErrorLog;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Immutable task model produced by the markdown parser and consumed across UI,
 * storage, and reminder scheduling.
 *
 * <p>The model intentionally keeps source information such as line number,
 * raw markdown, and parent task references so changes can be written back to
 * the original document without inventing a second source of truth.</p>
 */
public final class ObsidianTask {
    public static final String DEFAULT_GROUP = "РћР±С‰РµРµ";

    private final String taskKey;
    private final String seriesId;
    private final String sourceName;
    private final int lineNumber;
    private final String title;
    private final String rawLine;
    private final LocalDateTime reminderAt;
    private final Duration repeatInterval;
    private final RepeatMode repeatMode;
    private final RepeatRule repeatRule;
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
    private final Duration explicitOverdueGracePeriod;
    private final Duration resolvedOverdueGracePeriod;
    private final Duration explicitRepeatUntilDoneInterval;
    private final Duration resolvedRepeatUntilDoneInterval;

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
                null,
                null,
                null,
                null,
                legacyRepeatRule(repeatInterval, repeatMode),
                ""
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
                null,
                null,
                null,
                null,
                legacyRepeatRule(repeatInterval, repeatMode),
                ""
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
            String group,
            Duration snoozeDuration,
            Duration explicitOverdueGracePeriod,
            Duration resolvedOverdueGracePeriod,
            Duration explicitRepeatUntilDoneInterval,
            Duration resolvedRepeatUntilDoneInterval,
            RepeatRule repeatRule,
            String seriesId
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
                snoozeDuration,
                explicitOverdueGracePeriod,
                resolvedOverdueGracePeriod,
                explicitRepeatUntilDoneInterval,
                resolvedRepeatUntilDoneInterval,
                repeatRule,
                seriesId
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
                null,
                null,
                null,
                null,
                legacyRepeatRule(repeatInterval, repeatMode),
                ""
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
                null,
                null,
                null,
                null,
                legacyRepeatRule(repeatInterval, repeatMode),
                ""
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
                null,
                null,
                null,
                null,
                legacyRepeatRule(repeatInterval, repeatMode),
                ""
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
                parentTaskKey,
                parentLineNumber,
                indentLevel,
                tags,
                priority,
                group,
                snoozeDuration,
                overdueGracePeriod,
                overdueGracePeriod,
                repeatMode == RepeatMode.UNTIL_DONE ? repeatInterval : null,
                repeatMode == RepeatMode.UNTIL_DONE ? repeatInterval : null,
                legacyRepeatRule(repeatInterval, repeatMode),
                ""
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
            Duration explicitOverdueGracePeriod,
            Duration resolvedOverdueGracePeriod,
            Duration explicitRepeatUntilDoneInterval,
            Duration resolvedRepeatUntilDoneInterval,
            RepeatRule repeatRule,
            String seriesId
    ) {
        this.taskKey = taskKey;
        this.seriesId = normalizeSeriesId(seriesId);
        this.sourceName = sourceName == null ? "" : sourceName;
        this.lineNumber = lineNumber;
        this.title = title;
        this.rawLine = rawLine;
        this.reminderAt = reminderAt;
        this.repeatInterval = repeatInterval;
        this.repeatMode = repeatMode == null ? RepeatMode.NONE : repeatMode;
        this.repeatRule = repeatRule;
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
        this.explicitOverdueGracePeriod = explicitOverdueGracePeriod;
        this.resolvedOverdueGracePeriod = resolvedOverdueGracePeriod;
        this.explicitRepeatUntilDoneInterval = explicitRepeatUntilDoneInterval;
        this.resolvedRepeatUntilDoneInterval = resolvedRepeatUntilDoneInterval;
    }

    public String getTaskKey() {
        return taskKey;
    }

    public String getSeriesId() {
        return seriesId.isEmpty() ? taskKey : seriesId;
    }

    public boolean hasStableSeriesId() {
        return !seriesId.isEmpty();
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
        if (repeatMode == RepeatMode.UNTIL_DONE) {
            return resolvedRepeatUntilDoneInterval != null
                    ? resolvedRepeatUntilDoneInterval
                    : explicitRepeatUntilDoneInterval;
        }
        if (repeatRule != null) {
            Duration simpleDuration = repeatRule.toSimpleDuration();
            if (simpleDuration != null) {
                return simpleDuration;
            }
        }
        return repeatInterval;
    }

    public RepeatMode getRepeatMode() {
        return repeatMode;
    }

    public RepeatRule getRepeatRule() {
        return repeatRule;
    }

    public boolean hasRepeatSchedule() {
        return repeatRule != null;
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
        return resolvedOverdueGracePeriod;
    }

    public Duration getExplicitOverdueGracePeriod() {
        return explicitOverdueGracePeriod;
    }

    public Duration getResolvedOverdueGracePeriod() {
        return resolvedOverdueGracePeriod;
    }

    public Duration getExplicitRepeatUntilDoneInterval() {
        return explicitRepeatUntilDoneInterval;
    }

    public Duration getResolvedRepeatUntilDoneInterval() {
        return resolvedRepeatUntilDoneInterval;
    }

    public boolean isRepeatUntilDoneExplicit() {
        return explicitRepeatUntilDoneInterval != null;
    }

    public boolean isOverdueGraceExplicit() {
        return explicitOverdueGracePeriod != null;
    }

    public TaskStatus getStatus(LocalDateTime now) {
        return TaskStatus.forTask(this, now);
    }

    public TaskStatus getStatus(LocalDateTime now, Duration overdueGracePeriod) {
        return TaskStatus.forTask(this, now, overdueGracePeriod);
    }

    public long getRepeatIntervalMillis() {
        Duration duration = getRepeatInterval();
        return duration == null ? 0L : duration.toMillis();
    }

    public long getResolvedRepeatUntilDoneIntervalMillis() {
        return resolvedRepeatUntilDoneInterval == null ? 0L : resolvedRepeatUntilDoneInterval.toMillis();
    }

    public ObsidianTask withResolvedDefaults(
            Duration resolvedRepeatUntilDone,
            Duration resolvedOverdueGrace
    ) {
        return new ObsidianTask(
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
                parentTaskKey,
                parentLineNumber,
                indentLevel,
                tags,
                priority,
                group,
                snoozeDuration,
                explicitOverdueGracePeriod,
                resolvedOverdueGrace,
                explicitRepeatUntilDoneInterval,
                resolvedRepeatUntilDone,
                repeatRule,
                seriesId
        );
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

    public static String newSeriesId() {
        return UUID.randomUUID().toString();
    }

    private static RepeatMode defaultRepeatMode(Duration repeatInterval) {
        return repeatInterval == null ? RepeatMode.NONE : RepeatMode.ALWAYS;
    }

    private static RepeatRule legacyRepeatRule(Duration repeatInterval, RepeatMode repeatMode) {
        if (repeatMode == RepeatMode.ALWAYS && repeatInterval != null && !repeatInterval.isNegative()) {
            return RepeatRule.fromDuration(repeatInterval);
        }
        return null;
    }

    private static String normalizeSeriesId(String value) {
        return value == null ? "" : value.trim();
    }
}
