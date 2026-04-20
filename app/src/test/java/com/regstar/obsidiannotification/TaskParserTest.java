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
    }

    @Test
    public void parse_keepsTaskWithoutReminder() {
        String markdown = "- [ ] Task without schedule\n";

        List<ObsidianTask> tasks = TaskParser.parse(markdown, LocalDate.of(2026, 4, 20));

        assertEquals(1, tasks.size());
        assertEquals("Task without schedule", tasks.get(0).getTitle());
        assertNull(tasks.get(0).getReminderAt());
        assertNull(tasks.get(0).getRepeatInterval());
    }
}
