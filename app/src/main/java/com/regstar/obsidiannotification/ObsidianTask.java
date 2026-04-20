package com.regstar.obsidiannotification;

import java.time.Duration;
import java.time.LocalDateTime;

public final class ObsidianTask {
    private final int lineNumber;
    private final String title;
    private final String rawLine;
    private final LocalDateTime reminderAt;
    private final Duration repeatInterval;

    public ObsidianTask(
            int lineNumber,
            String title,
            String rawLine,
            LocalDateTime reminderAt,
            Duration repeatInterval
    ) {
        this.lineNumber = lineNumber;
        this.title = title;
        this.rawLine = rawLine;
        this.reminderAt = reminderAt;
        this.repeatInterval = repeatInterval;
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
}
