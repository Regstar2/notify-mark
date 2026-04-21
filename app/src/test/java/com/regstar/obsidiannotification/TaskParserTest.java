package com.regstar.obsidiannotification;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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
    public void parse_supportsUnifiedDueTagsAndPriority() {
        String markdown = "- [ ] Buy medicine @due(2026-04-20 19:00) "
                + "@repeat(15m) @tag(health pharmacy) #today @priority(high)\n";

        List<ObsidianTask> tasks = TaskParser.parse(markdown, LocalDate.of(2026, 4, 20));

        assertEquals(1, tasks.size());
        assertEquals("Buy medicine", tasks.get(0).getTitle());
        assertEquals(LocalDateTime.of(2026, 4, 20, 19, 0), tasks.get(0).getReminderAt());
        assertEquals(Duration.ofMinutes(15), tasks.get(0).getRepeatInterval());
        assertEquals(RepeatMode.ALWAYS, tasks.get(0).getRepeatMode());
        assertEquals(TaskPriority.HIGH, tasks.get(0).getPriority());
        assertEquals(3, tasks.get(0).getTags().size());
        assertEquals("health", tasks.get(0).getTags().get(0));
        assertEquals("pharmacy", tasks.get(0).getTags().get(1));
        assertEquals("today", tasks.get(0).getTags().get(2));
    }

    @Test
    public void parse_supportsNotificationsWithoutCheckboxes() {
        String markdown = ""
                + "Call doctor @due(2026-04-20 19:00) @repeatUntilDone(15m)\n"
                + "- Stand up @due(19:10) @repeat(5m)\n"
                + "Plain text without reminder\n";

        List<ObsidianTask> tasks = TaskParser.parse(markdown, LocalDate.of(2026, 4, 20));

        assertEquals(2, tasks.size());
        assertEquals("Call doctor", tasks.get(0).getTitle());
        assertEquals(LocalDateTime.of(2026, 4, 20, 19, 0), tasks.get(0).getReminderAt());
        assertEquals(Duration.ofMinutes(15), tasks.get(0).getRepeatInterval());
        assertEquals(RepeatMode.UNTIL_DONE, tasks.get(0).getRepeatMode());
        assertEquals("Stand up", tasks.get(1).getTitle());
        assertEquals(LocalDateTime.of(2026, 4, 20, 19, 10), tasks.get(1).getReminderAt());
    }

    @Test
    public void parse_supportsDateOnlyAndTimeOnlyDue() {
        String markdown = ""
                + "- [ ] Date only @due(2026-04-21)\n"
                + "- [ ] Time only @due(19:45)\n"
                + "- [ ] No repeat @due(2026-04-20 20:00)\n";

        List<ObsidianTask> tasks = TaskParser.parse(markdown, LocalDate.of(2026, 4, 20));

        assertEquals(3, tasks.size());
        assertEquals(LocalDateTime.of(2026, 4, 21, 9, 0), tasks.get(0).getReminderAt());
        assertEquals(LocalDateTime.of(2026, 4, 20, 19, 45), tasks.get(1).getReminderAt());
        assertEquals(LocalDateTime.of(2026, 4, 20, 20, 0), tasks.get(2).getReminderAt());
        assertNull(tasks.get(2).getRepeatInterval());
        assertEquals(RepeatMode.NONE, tasks.get(2).getRepeatMode());
    }

    @Test
    public void parse_ignoresBomAndRepeatWordInTitle() {
        String markdown = "\uFEFF- [ ] No repeat needed @due(2026-04-20 20:00)\n";

        TaskParseResult result = TaskParser.parseDocument(
                markdown,
                LocalDate.of(2026, 4, 20),
                "tasks.md"
        );

        assertEquals(1, result.getTasks().size());
        assertEquals("No repeat needed", result.getTasks().get(0).getTitle());
        assertEquals(0, result.getErrors().size());
    }

    @Test
    public void parse_ignoresSnoozedCounterMetadata() {
        String markdown = "- [ ] Snoozed task @due(2026-04-20 20:00) @snoozed(3)\n";

        TaskParseResult result = TaskParser.parseDocument(
                markdown,
                LocalDate.of(2026, 4, 20),
                "tasks.md"
        );

        assertEquals(1, result.getTasks().size());
        assertEquals("Snoozed task", result.getTasks().get(0).getTitle());
        assertEquals(0, result.getErrors().size());
    }

    @Test
    public void parse_ignoresTasksInsideFencedCodeBlocks() {
        String markdown = ""
                + "```markdown\n"
                + "- [ ] Example only @due(2026-04-20 20:00)\n"
                + "```\n"
                + "- [ ] Real task @due(2026-04-20 21:00)\n"
                + "~~~\n"
                + "Plain example @due(2026-04-20 22:00)\n"
                + "~~~\n";

        TaskParseResult result = TaskParser.parseDocument(
                markdown,
                LocalDate.of(2026, 4, 20),
                "tasks.md"
        );

        assertEquals(1, result.getTasks().size());
        assertEquals("Real task", result.getTasks().get(0).getTitle());
        assertEquals(0, result.getErrors().size());
    }

    @Test
    public void parse_reportsInvalidUnifiedFormat() {
        String markdown = "- [ ] Broken @due(2026-02-30 25:00) "
                + "@repeat(bad) @priority(nope)\n";

        TaskParseResult result = TaskParser.parseDocument(
                markdown,
                LocalDate.of(2026, 4, 20),
                "tasks.md"
        );

        assertEquals(1, result.getTasks().size());
        assertEquals(3, result.getErrors().size());
    }

    @Test
    public void parse_supportsCustomKeywords() {
        TaskFormatSettings settings = TaskFormatSettings.fromValues(
                "when",
                "again",
                "nag",
                "labels",
                "prio",
                "bucket"
        );
        String markdown = "- [ ] Custom @when(2026-04-20 19:00) "
                + "@nag(10m) @labels(home call) @prio(p1)\n";

        TaskParseResult result = TaskParser.parseDocument(
                markdown,
                LocalDate.of(2026, 4, 20),
                "tasks.md",
                settings
        );

        ObsidianTask task = result.getTasks().get(0);
        assertEquals("Custom", task.getTitle());
        assertEquals(LocalDateTime.of(2026, 4, 20, 19, 0), task.getReminderAt());
        assertEquals(Duration.ofMinutes(10), task.getRepeatInterval());
        assertEquals(RepeatMode.UNTIL_DONE, task.getRepeatMode());
        assertEquals(TaskPriority.URGENT, task.getPriority());
        assertEquals(2, task.getTags().size());
        assertEquals("home", task.getTags().get(0));
        assertEquals("call", task.getTags().get(1));
        assertEquals(0, result.getErrors().size());
    }

    @Test
    public void parse_supportsGroupFunction() {
        String markdown = "- [ ] Pay bills @due(2026-04-20 19:00) @group(home)\n";

        TaskParseResult result = TaskParser.parseDocument(
                markdown,
                LocalDate.of(2026, 4, 20),
                "tasks.md"
        );

        ObsidianTask task = result.getTasks().get(0);
        assertEquals("Pay bills", task.getTitle());
        assertEquals("home", task.getGroup());
    }

    @Test
    public void parse_deduplicatesTagsAndKeepsStableOrder() {
        String markdown = "- [ ] Tagged @due(2026-04-20 19:00) @tag(work, health work) #health #today\n";

        TaskParseResult result = TaskParser.parseDocument(
                markdown,
                LocalDate.of(2026, 4, 20),
                "tasks.md"
        );

        ObsidianTask task = result.getTasks().get(0);
        assertEquals(3, task.getTags().size());
        assertEquals("work", task.getTags().get(0));
        assertEquals("health", task.getTags().get(1));
        assertEquals("today", task.getTags().get(2));
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
    public void parseDocument_excludesSkippedTasksFromActiveTasks() {
        String markdown = ""
                + "- [ ] Waiting task @2026-04-20 12:30\n"
                + "- [ ] Skipped task @2026-04-20 13:30 @skipped\n";

        TaskParseResult result = TaskParser.parseDocument(
                markdown,
                LocalDate.of(2026, 4, 20),
                "tasks.md"
        );

        assertEquals(2, result.getTasks().size());
        assertEquals(1, result.getActiveTasks().size());
        assertEquals("Skipped task", result.getTasks().get(1).getTitle());
        assertTrue(result.getTasks().get(1).isSkipped());
        assertEquals(TaskStatus.SKIPPED, result.getTasks().get(1)
                .getStatus(LocalDateTime.of(2026, 4, 20, 14, 0)));
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
