package com.regstar.obsidiannotification.core.tasks;

import com.regstar.obsidiannotification.prefs.UserPreferences;

import org.junit.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class TaskCalendarProjectionTest {
    @Test
    public void mergeTasksByDate_includesHistoricalSkippedOnOriginalDueDay() {
        LocalDateTime oldDue = LocalDateTime.of(2026, 5, 10, 9, 0);
        LocalDateTime newDue = LocalDateTime.of(2026, 5, 11, 9, 0);
        ObsidianTask head = new ObsidianTask(
                "k1",
                "Note.md",
                1,
                "Water plant",
                "- [ ] Water plant @due(2026-05-11 09:00) @repeat(1d) @id(series-a)",
                newDue,
                java.time.Duration.ofDays(1),
                RepeatMode.ALWAYS,
                false,
                false,
                "",
                0,
                0,
                Collections.emptyList(),
                TaskPriority.NONE,
                "home",
                null,
                null,
                null,
                null,
                null,
                RepeatRule.fromDuration(java.time.Duration.ofDays(1)),
                "series-a"
        );
        TaskOccurrenceRecord skipped = new TaskOccurrenceRecord(
                "series-a",
                oldDue,
                OccurrenceStatus.SKIPPED,
                LocalDateTime.of(2026, 5, 10, 10, 0),
                "Note.md",
                "Water plant",
                "home",
                Collections.singletonList("plants"),
                TaskPriority.NONE,
                1
        );

        Map<java.time.LocalDate, List<TaskVisibleOccurrence>> byDate = TaskCalendarProjection.mergeTasksByDate(
                Collections.singletonList(head),
                Collections.singletonList(skipped),
                false,
                ""
        );

        List<TaskVisibleOccurrence> day10 = byDate.get(java.time.LocalDate.of(2026, 5, 10));
        List<TaskVisibleOccurrence> day11 = byDate.get(java.time.LocalDate.of(2026, 5, 11));
        assertEquals(1, day10.size());
        assertTrue(day10.get(0).isHistorical());
        assertEquals(OccurrenceStatus.SKIPPED, day10.get(0).getHistoryRecord().getOccurrenceStatus());

        assertEquals(1, day11.size());
        assertTrue(!day11.get(0).isHistorical());
        assertEquals(newDue, day11.get(0).getMarkdownTask().getReminderAt());
    }

    @Test
    public void filterBySelectedBucket_matchesHistorySnapshots() {
        TaskOccurrenceRecord r = new TaskOccurrenceRecord(
                "s",
                LocalDateTime.of(2026, 5, 1, 8, 0),
                OccurrenceStatus.COMPLETED,
                LocalDateTime.of(2026, 5, 1, 9, 0),
                "Long name note.md",
                "T",
                "work",
                Collections.emptyList(),
                TaskPriority.NONE,
                1
        );
        TaskVisibleOccurrence e = TaskVisibleOccurrence.fromHistory(r);
        List<TaskVisibleOccurrence> filtered = TaskCalendarProjection.filterBySelectedBucket(
                Collections.singletonList(e),
                "work",
                UserPreferences.GROUPING_GROUP,
                s -> s.length() > 8 ? s.substring(0, 8) + "…" : s
        );
        assertEquals(1, filtered.size());
    }

    @Test
    public void mergeTasksByDate_dedupesDuplicateHistoryLines() {
        LocalDateTime due = LocalDateTime.of(2026, 5, 2, 12, 0);
        TaskOccurrenceRecord a = new TaskOccurrenceRecord(
                "sid",
                due,
                OccurrenceStatus.SKIPPED,
                LocalDateTime.of(2026, 5, 2, 13, 0),
                "N.md",
                "x",
                "g",
                Collections.emptyList(),
                TaskPriority.NONE,
                1
        );
        TaskOccurrenceRecord b = new TaskOccurrenceRecord(
                "sid",
                due,
                OccurrenceStatus.SKIPPED,
                LocalDateTime.of(2026, 5, 2, 13, 0),
                "N.md",
                "x",
                "g",
                Collections.emptyList(),
                TaskPriority.NONE,
                1
        );
        Map<java.time.LocalDate, List<TaskVisibleOccurrence>> map = TaskCalendarProjection.mergeTasksByDate(
                Collections.emptyList(),
                Arrays.asList(a, b),
                false,
                ""
        );
        assertEquals(1, map.get(due.toLocalDate()).size());
    }
}
