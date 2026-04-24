package com.regstar.obsidiannotification.core.model;

import java.time.LocalDateTime;
import java.time.Duration;

/**
 * Effective task state shared by list, calendar, notifications and bulk actions.
 *
 * <p>{@link #OVERDUE} is derived from due time plus grace period. Completed and skipped tasks
 * always override overdue because they are explicit persisted user actions.</p>
 */
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
        Duration safeGracePeriod = task.getOverdueGracePeriod() != null
                ? task.getOverdueGracePeriod()
                : (overdueGracePeriod == null ? Duration.ZERO : overdueGracePeriod);
        LocalDateTime overdueAt = task.getReminderAt() == null
                ? null
                : task.getReminderAt().plus(safeGracePeriod);
        if (overdueAt != null && now != null && now.isAfter(overdueAt)) {
            return OVERDUE;
        }
        return WAITING;
    }
}


