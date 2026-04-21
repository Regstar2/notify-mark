package com.regstar.obsidiannotification;

import java.time.LocalDateTime;

public final class ScheduledReminder {
    private final String taskKey;
    private final int notificationId;
    private final int lineNumber;
    private final String title;
    private final LocalDateTime triggerAt;
    private final long triggerAtMillis;
    private final long repeatIntervalMillis;
    private final RepeatMode repeatMode;
    private final String group;

    public ScheduledReminder(
            int notificationId,
            int lineNumber,
            String title,
            LocalDateTime triggerAt,
            long triggerAtMillis
    ) {
        this("", notificationId, lineNumber, title, triggerAt, triggerAtMillis, 0L, RepeatMode.NONE, ObsidianTask.DEFAULT_GROUP);
    }

    public ScheduledReminder(
            String taskKey,
            int notificationId,
            int lineNumber,
            String title,
            LocalDateTime triggerAt,
            long triggerAtMillis,
            long repeatIntervalMillis,
            RepeatMode repeatMode
    ) {
        this(
                taskKey,
                notificationId,
                lineNumber,
                title,
                triggerAt,
                triggerAtMillis,
                repeatIntervalMillis,
                repeatMode,
                ObsidianTask.DEFAULT_GROUP
        );
    }

    public ScheduledReminder(
            String taskKey,
            int notificationId,
            int lineNumber,
            String title,
            LocalDateTime triggerAt,
            long triggerAtMillis,
            long repeatIntervalMillis,
            RepeatMode repeatMode,
            String group
    ) {
        this.taskKey = taskKey;
        this.notificationId = notificationId;
        this.lineNumber = lineNumber;
        this.title = title;
        this.triggerAt = triggerAt;
        this.triggerAtMillis = triggerAtMillis;
        this.repeatIntervalMillis = repeatIntervalMillis;
        this.repeatMode = repeatMode == null ? RepeatMode.NONE : repeatMode;
        this.group = ObsidianTask.normalizeGroup(group);
    }

    public String getTaskKey() {
        return taskKey;
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

    public long getRepeatIntervalMillis() {
        return repeatIntervalMillis;
    }

    public RepeatMode getRepeatMode() {
        return repeatMode;
    }

    public String getGroup() {
        return group;
    }
}
