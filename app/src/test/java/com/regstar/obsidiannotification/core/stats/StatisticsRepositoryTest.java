package com.regstar.obsidiannotification.core.stats;

import com.regstar.obsidiannotification.core.tasks.ObsidianTask;
import com.regstar.obsidiannotification.core.tasks.OccurrenceStatus;
import com.regstar.obsidiannotification.core.tasks.TaskOccurrenceRecord;
import com.regstar.obsidiannotification.core.tasks.TaskPriority;
import com.regstar.obsidiannotification.core.tasks.RepeatMode;

import org.junit.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public final class StatisticsRepositoryTest {
    @Test
    public void buildReport_combinesSnapshotAndHistory() {
        LocalDateTime now = LocalDateTime.of(2026, 4, 30, 12, 0);
        List<ObsidianTask> tasks = Arrays.asList(
                task("work|1", "Inbox.md", 1, "Prepare report", now.plusHours(2), false, false, "work", Collections.singletonList("office"), false),
                task("work|2", "Inbox.md", 2, "Send invoice", now.minusHours(8), false, false, "work", Collections.singletonList("office"), false),
                task("health|1", "Daily.md", 3, "Stretching", now.minusHours(1), true, false, "health", Collections.singletonList("health"), false),
                task("sub|1", "Daily.md", 4, "Warm up", now.minusMinutes(30), false, false, "health", Collections.singletonList("health"), true)
        );
        List<TaskOccurrenceRecord> history = Arrays.asList(
                record("series-1", "Inbox.md", "Prepare report", "work", Collections.singletonList("office"), OccurrenceStatus.COMPLETED, now.minusDays(3), now.minusDays(3).plusMinutes(20)),
                record("series-2", "Inbox.md", "Send invoice", "work", Collections.singletonList("office"), OccurrenceStatus.SKIPPED, now.minusDays(2), now.minusDays(2).plusMinutes(15)),
                record("series-3", "Daily.md", "Stretching", "health", Collections.singletonList("health"), OccurrenceStatus.COMPLETED, now.minusDays(1), now.minusDays(1).plusMinutes(40))
        );

        StatisticsReport report = StatisticsRepository.buildReport(
                tasks,
                history,
                StatisticsFilters.defaults(),
                now,
                Duration.ZERO
        );

        assertEquals(4, report.getSummary().getSnapshotTotalCount());
        assertEquals(1, report.getSummary().getActiveNowCount());
        assertEquals(2, report.getSummary().getOverdueNowCount());
        assertEquals(1, report.getSummary().getCompletedNowCount());
        assertEquals(2, report.getSummary().getHistoricalCompletedCount());
        assertEquals(1, report.getSummary().getHistoricalSkippedCount());
        assertTrue(report.hasHistoricalData());
        assertFalse(report.getTimeline().isEmpty());
        assertEquals(1, report.getSubtaskSummary().getTotalCount());

        StatisticsBreakdownRow work = report.getGroupBreakdown().stream()
                .filter(row -> "work".equals(row.getKey()))
                .findFirst()
                .orElse(null);
        assertNotNull(work);
        assertEquals(1, work.getCurrentOverdueCount());
        assertEquals(1, work.getHistoricalCompletedCount());
        assertEquals(1, work.getHistoricalSkippedCount());
    }

    @Test
    public void buildReport_respectsGroupTagAndSourceFilters() {
        LocalDateTime now = LocalDateTime.of(2026, 4, 30, 12, 0);
        List<ObsidianTask> tasks = Arrays.asList(
                task("a", "Inbox.md", 1, "Prepare report", now.plusHours(1), false, false, "work", Collections.singletonList("office"), false),
                task("b", "Daily.md", 2, "Stretching", now.plusHours(1), false, false, "health", Collections.singletonList("health"), false)
        );
        List<TaskOccurrenceRecord> history = Arrays.asList(
                record("series-1", "Inbox.md", "Prepare report", "work", Collections.singletonList("office"), OccurrenceStatus.COMPLETED, now.minusDays(2), now.minusDays(2).plusMinutes(10)),
                record("series-2", "Daily.md", "Stretching", "health", Collections.singletonList("health"), OccurrenceStatus.SKIPPED, now.minusDays(2), now.minusDays(2).plusMinutes(10))
        );

        StatisticsFilters filters = new StatisticsFilters(
                StatisticsPeriod.LAST_30_DAYS,
                "work",
                "office",
                "Inbox.md"
        );
        StatisticsReport report = StatisticsRepository.buildReport(
                tasks,
                history,
                filters,
                now,
                Duration.ZERO
        );

        assertEquals(1, report.getSummary().getSnapshotTotalCount());
        assertEquals(1, report.getSummary().getHistoricalCompletedCount());
        assertEquals(0, report.getSummary().getHistoricalSkippedCount());
        assertEquals(2, report.getAvailableGroups().size());
        assertTrue(report.getAvailableGroups().contains("health"));
        assertTrue(report.getAvailableGroups().contains("work"));
    }

    private static ObsidianTask task(
            String taskKey,
            String sourceName,
            int lineNumber,
            String title,
            LocalDateTime reminderAt,
            boolean completed,
            boolean skipped,
            String group,
            List<String> tags,
            boolean subtask
    ) {
        return new ObsidianTask(
                taskKey,
                sourceName,
                lineNumber,
                title,
                "- [ ] " + title,
                reminderAt,
                null,
                RepeatMode.NONE,
                completed,
                skipped,
                subtask ? "parent" : "",
                subtask ? 1 : 0,
                subtask ? 1 : 0,
                tags,
                TaskPriority.NONE,
                group,
                null,
                null,
                null,
                null,
                null,
                null,
                "series-" + taskKey
        );
    }

    private static TaskOccurrenceRecord record(
            String seriesId,
            String sourceName,
            String title,
            String group,
            List<String> tags,
            OccurrenceStatus status,
            LocalDateTime dueAt,
            LocalDateTime resolvedAt
    ) {
        return new TaskOccurrenceRecord(
                seriesId,
                dueAt,
                status,
                resolvedAt,
                sourceName,
                title,
                group,
                tags,
                TaskPriority.NONE,
                1
        );
    }
}
