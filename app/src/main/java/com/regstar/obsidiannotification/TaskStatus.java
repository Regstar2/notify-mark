package com.regstar.obsidiannotification;

import java.time.LocalDateTime;
import java.time.Duration;

public enum TaskStatus {
    WAITING,
    OVERDUE,
    SKIPPED,
    COMPLETED;

    public static TaskStatus forTask(ObsidianTask task, LocalDateTime now) {
        return forTask(task, now, Duration.ZERO);
    }

    public static TaskStatus forTask(ObsidianTask task, LocalDateTime now, Duration overdueGracePeriod) {
        if (task.isCompleted()) {
            return COMPLETED;
        }
        if (task.isSkipped()) {
            return SKIPPED;
        }
        Duration safeGracePeriod = overdueGracePeriod == null ? Duration.ZERO : overdueGracePeriod;
        LocalDateTime overdueAt = task.getReminderAt() == null
                ? null
                : task.getReminderAt().plus(safeGracePeriod);
        if (overdueAt != null && now != null && now.isAfter(overdueAt)) {
            return OVERDUE;
        }
        return WAITING;
    }
}
