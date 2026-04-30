package com.regstar.obsidiannotification.core.tasks;

import com.regstar.obsidiannotification.core.source.*;
import com.regstar.obsidiannotification.core.tasks.*;
import com.regstar.obsidiannotification.prefs.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

public final class TaskDefaultsResolverTest {
    @Test
    public void resolve_preservesSubtaskHierarchy() {
        String markdown = ""
                + "- [ ] Parent @due(2026-04-28 10:00)\n"
                + "  - [ ] Child @due(2026-04-28 11:00)\n";

        TaskParseResult parsed = TaskParser.parseDocument(
                markdown,
                LocalDate.of(2026, 4, 28),
                "tasks.md"
        );

        List<ObsidianTask> resolved = TaskDefaultsResolver.resolve(
                parsed.getTasks(),
                Duration.ofMinutes(5),
                Duration.ofMinutes(10)
        );

        assertEquals(2, resolved.size());

        ObsidianTask parent = resolved.get(0);
        ObsidianTask child = resolved.get(1);

        assertFalse(parent.isSubtask());
        assertEquals(1, parent.getSubtasks().size());
        assertNotNull(parent.getSubtasks().get(0));
        assertEquals(child.getTaskKey(), parent.getSubtasks().get(0).getTaskKey());
        assertEquals(parent.getTaskKey(), child.getParentTaskKey());
    }

    @Test
    public void resolve_doesNotEnableRepeatUntilDoneFromDefaultsAlone() {
        ObsidianTask task = new ObsidianTask(
                1,
                "Task",
                "- [ ] Task @due(2026-04-28 10:00)",
                java.time.LocalDateTime.of(2026, 4, 28, 10, 0),
                null
        );

        ObsidianTask resolved = TaskDefaultsResolver.resolve(
                task,
                Duration.ofMinutes(15),
                Duration.ofMinutes(10)
        );

        assertNull(resolved.getExplicitRepeatUntilDoneInterval());
        assertNull(resolved.getResolvedRepeatUntilDoneInterval());
    }
}
