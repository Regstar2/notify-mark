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
}

