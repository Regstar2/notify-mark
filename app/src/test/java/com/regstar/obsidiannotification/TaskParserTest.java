package com.regstar.obsidiannotification;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class TaskParserTest {
    @Test
    public void parse_returnsOnlyActiveTasks() {
        String markdown = ""
                + "- [ ] Active task @2026-04-20 14:30 every 15m\n"
                + "- [x] Done task @2026-04-20 15:00 every 1h\n"
                + "Plain text\n"
                + "* [ ] Second task @20.04.2026 19:00 каждые 10м\n";

        List<ObsidianTask> tasks = TaskParser.parse(markdown, LocalDate.of(2026, 4, 20));

        assertEquals(2, tasks.size());
        assertEquals("Active task", tasks.get(0).getTitle());
        assertEquals(1, tasks.get(0).getLineNumber());
        assertEquals(LocalDateTime.of(2026, 4, 20, 14, 30), tasks.get(0).getReminderAt());
        assertEquals(Duration.ofMinutes(15), tasks.get(0).getRepeatInterval());
        assertEquals(RepeatMode.ALWAYS, tasks.get(0).getRepeatMode());
        assertEquals("Second task", tasks.get(1).getTitle());
        assertEquals(4, tasks.get(1).getLineNumber());
        assertEquals(Duration.ofMinutes(10), tasks.get(1).getRepeatInterval());
    }

    @Test
    public void parse_usesDefaultDateForTimeOnlyReminder() {
        String markdown = "- [ ] Morning check @09:30 repeat 1h\n";

        List<ObsidianTask> tasks = TaskParser.parse(markdown, LocalDate.of(2026, 4, 20));

        assertEquals(1, tasks.size());
        assertEquals(LocalDateTime.of(2026, 4, 20, 9, 30), tasks.get(0).getReminderAt());
        assertEquals(Duration.ofHours(1), tasks.get(0).getRepeatInterval());
        assertEquals(RepeatMode.ALWAYS, tasks.get(0).getRepeatMode());
    }

    @Test
    public void parse_supportsRepeatFunctionModes() {
        String markdown = ""
                + "- [ ] Hydrate @09:30 @repeat(2h)\n"
                + "- [ ] Inbox zero @10:00 @repeatUntilDone(15m)\n";

        List<ObsidianTask> tasks = TaskParser.parse(markdown, LocalDate.of(2026, 4, 20));

        assertEquals(2, tasks.size());
        assertEquals("Hydrate", tasks.get(0).getTitle());
        assertEquals(Duration.ofHours(2), tasks.get(0).getRepeatInterval());
        assertEquals(RepeatMode.ALWAYS, tasks.get(0).getRepeatMode());
        assertEquals("Inbox zero", tasks.get(1).getTitle());
        assertEquals(Duration.ofMinutes(15), tasks.get(1).getRepeatInterval());
        assertEquals(RepeatMode.UNTIL_DONE, tasks.get(1).getRepeatMode());
    }

    @Test
    public void parseDocument_returnsCompletedTasksAndStatuses() {
        String markdown = ""
                + "- [ ] Waiting task @2026-04-20 12:30\n"
                + "- [x] Done task @2026-04-20 12:30\n";

        TaskParseResult result = TaskParser.parseDocument(
                markdown,
                LocalDate.of(2026, 4, 20),
                "tasks.md"
        );

        assertEquals(2, result.getTasks().size());
        assertEquals(1, result.getActiveTasks().size());
        assertEquals("tasks.md", result.getTasks().get(0).getSourceName());
        assertEquals(TaskStatus.WAITING, result.getTasks().get(0)
                .getStatus(LocalDateTime.of(2026, 4, 20, 12, 0)));
        assertEquals(TaskStatus.COMPLETED, result.getTasks().get(1)
                .getStatus(LocalDateTime.of(2026, 4, 20, 13, 0)));
    }

    @Test
    public void parseDocument_reportsReadableParseErrors() {
        String markdown = "- [ ] Broken task @tomorrow @repeat(bad)\n";

        TaskParseResult result = TaskParser.parseDocument(
                markdown,
                LocalDate.of(2026, 4, 20),
                "tasks.md"
        );

        assertEquals(1, result.getTasks().size());
        assertEquals(2, result.getErrors().size());
        assertEquals("tasks.md", result.getErrors().get(0).getSourceName());
    }

    @Test
    public void parse_keepsTaskWithoutReminder() {
        String markdown = "- [ ] Task without schedule\n";

        List<ObsidianTask> tasks = TaskParser.parse(markdown, LocalDate.of(2026, 4, 20));

        assertEquals(1, tasks.size());
        assertEquals("Task without schedule", tasks.get(0).getTitle());
        assertNull(tasks.get(0).getReminderAt());
        assertNull(tasks.get(0).getRepeatInterval());
        assertEquals(RepeatMode.NONE, tasks.get(0).getRepeatMode());
    }
}
