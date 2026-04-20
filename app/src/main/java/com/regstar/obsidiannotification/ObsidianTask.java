package com.regstar.obsidiannotification;

import java.time.Duration;
import java.time.LocalDateTime;

public final class ObsidianTask {
    private final String taskKey;
    private final int lineNumber;
    private final String title;
    private final String rawLine;
    private final LocalDateTime reminderAt;
    private final Duration repeatInterval;
    private final RepeatMode repeatMode;

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
                createTaskKey(lineNumber, title, reminderAt, repeatInterval, repeatMode),
                lineNumber,
                title,
                rawLine,
                reminderAt,
                repeatInterval,
                repeatMode
        );
    }

    public ObsidianTask(
            String taskKey,
            int lineNumber,
            String title,
            String rawLine,
            LocalDateTime reminderAt,
            Duration repeatInterval,
            RepeatMode repeatMode
    ) {
        this.taskKey = taskKey;
        this.lineNumber = lineNumber;
        this.title = title;
        this.rawLine = rawLine;
        this.reminderAt = reminderAt;
        this.repeatInterval = repeatInterval;
        this.repeatMode = repeatMode == null ? RepeatMode.NONE : repeatMode;
    }

    public String getTaskKey() {
        return taskKey;
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

    public long getRepeatIntervalMillis() {
        return repeatInterval == null ? 0L : repeatInterval.toMillis();
    }

    public static String createTaskKey(
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
        return lineNumber + "|" + normalizedTitle + "|" + reminderPart + "|" + repeatPart + "|" + modePart;
    }

    private static RepeatMode defaultRepeatMode(Duration repeatInterval) {
        return repeatInterval == null ? RepeatMode.NONE : RepeatMode.ALWAYS;
    }
}
