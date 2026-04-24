package com.regstar.obsidiannotification.core.model;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;

public final class TaskStatusTest {
    @Test
    public void forTask_usesConfiguredOverdueGracePeriod() {
        ObsidianTask task = task(false, false);
        LocalDateTime due = LocalDateTime.of(2026, 4, 20, 12, 0);

        assertEquals(TaskStatus.WAITING,
                task.getStatus(due.plusMinutes(9), Duration.ofMinutes(10)));
        assertEquals(TaskStatus.OVERDUE,
                task.getStatus(due.plusMinutes(11), Duration.ofMinutes(10)));
    }

    @Test
    public void forTask_completedAndSkippedAreFinalBeforeOverdue() {
        LocalDateTime afterDue = LocalDateTime.of(2026, 4, 20, 13, 0);

        assertEquals(TaskStatus.COMPLETED,
                task(true, false).getStatus(afterDue, Duration.ZERO));
        assertEquals(TaskStatus.SKIPPED,
                task(false, true).getStatus(afterDue, Duration.ZERO));
    }

    private ObsidianTask task(boolean completed, boolean skipped) {
        LocalDateTime due = LocalDateTime.of(2026, 4, 20, 12, 0);
        return new ObsidianTask(
                "tasks.md|1|Task|2026-04-20T12:00||NONE",
                "tasks.md",
                1,
                "Task",
                "- [ ] Task @due(2026-04-20 12:00)",
                due,
                null,
                RepeatMode.NONE,
                completed,
                skipped,
                Collections.emptyList(),
                TaskPriority.NONE,
                ObsidianTask.DEFAULT_GROUP
        );
    }
}


