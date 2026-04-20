package com.regstar.obsidiannotification;

import java.time.LocalDateTime;

public final class ScheduledReminder {
    private final int notificationId;
    private final int lineNumber;
    private final String title;
    private final LocalDateTime triggerAt;
    private final long triggerAtMillis;

    public ScheduledReminder(
            int notificationId,
            int lineNumber,
            String title,
            LocalDateTime triggerAt,
            long triggerAtMillis
    ) {
        this.notificationId = notificationId;
        this.lineNumber = lineNumber;
        this.title = title;
        this.triggerAt = triggerAt;
        this.triggerAtMillis = triggerAtMillis;
    }

    public int getNotificationId() {
        return notificationId;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public String getTitle() {
        return title;
    }

    public LocalDateTime getTriggerAt() {
        return triggerAt;
    }

    public long getTriggerAtMillis() {
        return triggerAtMillis;
    }
}
