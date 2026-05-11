package com.regstar.obsidiannotification.core.tasks;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDateTime;

public final class RepeatSeriesManagerFormatSettingsTest {
    @Test
    public void advanceBlock_usesActiveDueKeywordFromFormatSettings() {
        TaskFormatSettings settings = TaskFormatSettings.fromValues(
                "d",
                TaskFormatSettings.DEFAULT_REPEAT_KEYWORD,
                TaskFormatSettings.DEFAULT_REPEAT_UNTIL_DONE_KEYWORD,
                TaskFormatSettings.DEFAULT_TAG_KEYWORD,
                TaskFormatSettings.DEFAULT_PRIORITY_KEYWORD,
                TaskFormatSettings.DEFAULT_GROUP_KEYWORD
        );
        ObsidianTask task = new ObsidianTask(
                1,
                "T",
                "- [ ] T @d(2026-04-20 10:00) @repeat(1d)",
                LocalDateTime.of(2026, 4, 20, 10, 0),
                null
        );

        String updated = RepeatSeriesManager.advanceBlock(
                task,
                task.getRawLine(),
                LocalDateTime.of(2026, 4, 21, 9, 0),
                "series-1",
                settings
        );

        assertTrue(updated.contains("@d("));
        assertFalse(updated.contains("@due("));
    }

    @Test
    public void advanceBlock_clearsTransientMarkersAndPreservesRepeat() {
        TaskFormatSettings settings = TaskFormatSettings.defaults();
        ObsidianTask task = new ObsidianTask(
                1,
                "T",
                "- [x] T @due(2026-04-20 10:00) @repeat(1d) @skipped @snoozed(2) @id(series-z)",
                LocalDateTime.of(2026, 4, 20, 10, 0),
                java.time.Duration.ofDays(1)
        );

        String updated = RepeatSeriesManager.advanceBlock(
                task,
                task.getRawLine(),
                LocalDateTime.of(2026, 4, 21, 10, 0),
                "series-z",
                settings
        );

        assertFalse(updated.contains("[x]"));
        assertFalse(updated.toLowerCase().contains("@skipped"));
        assertFalse(updated.toLowerCase().contains("@snoozed"));
        assertTrue(updated.toLowerCase().contains("@repeat(1d)") || updated.toLowerCase().contains("@r(1d)"));
        assertTrue(updated.contains("series-z"));
    }

    @Test
    public void advanceBlock_writesNewSeriesIdWhenProvided() {
        TaskFormatSettings settings = TaskFormatSettings.defaults();
        ObsidianTask task = new ObsidianTask(
                1,
                "T",
                "- [ ] T @due(2026-04-20 10:00) @repeat(1d)",
                LocalDateTime.of(2026, 4, 20, 10, 0),
                java.time.Duration.ofDays(1)
        );

        String updated = RepeatSeriesManager.advanceBlock(
                task,
                task.getRawLine(),
                LocalDateTime.of(2026, 4, 21, 10, 0),
                "fresh-series-id",
                settings
        );

        assertTrue(updated.contains("fresh-series-id"));
    }
}

