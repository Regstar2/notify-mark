package com.regstar.obsidiannotification;

import java.time.LocalDateTime;

public enum TaskStatus {
    WAITING,
    OVERDUE,
    COMPLETED;

    public static TaskStatus forTask(ObsidianTask task, LocalDateTime now) {
        if (task.isCompleted()) {
            return COMPLETED;
        }
        if (task.getReminderAt() != null && !task.getReminderAt().isAfter(now)) {
            return OVERDUE;
        }
        return WAITING;
    }
}
