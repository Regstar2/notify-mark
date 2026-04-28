package com.regstar.obsidiannotification.core.tasks;

import com.regstar.obsidiannotification.core.reminders.*;
import com.regstar.obsidiannotification.core.source.*;
import com.regstar.obsidiannotification.prefs.*;
import com.regstar.obsidiannotification.support.ErrorLog;

import java.time.LocalDateTime;
import java.time.Duration;

/**
 * Visible state of a parsed task at a specific point in time.
 */
public enum TaskStatus {
    WAITING,
    OVERDUE,
    SKIPPED,
    COMPLETED;

    public static TaskStatus forTask(ObsidianTask task, LocalDateTime now) {
        return forTask(task, now, Duration.ZERO);
    }

    /**
     * Computes status using the task's own resolved grace period when present,
     * or the provided fallback when the task itself does not define one.
     */
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
