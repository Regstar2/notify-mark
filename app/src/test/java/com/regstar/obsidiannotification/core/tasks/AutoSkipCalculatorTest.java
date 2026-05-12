package com.regstar.obsidiannotification.core.tasks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.regstar.obsidiannotification.prefs.AutoSkipPreferences;

import org.junit.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;

public final class AutoSkipCalculatorTest {
    private static final LocalDateTime DUE = LocalDateTime.of(2026, 5, 10, 18, 0);

    @Test
    public void computeAutoSkipAt_usesExplicitGraceAndDelay() {
        ObsidianTask task = new ObsidianTask(
                "k",
                "s",
                1,
                "t",
                "- [ ] x @due(2026-05-10 18:00) @grace(30m)",
                DUE,
                null,
                RepeatMode.NONE,
                false,
                false,
                "",
                0,
                0,
                Collections.emptyList(),
                TaskPriority.NONE,
                "g",
                null,
                Duration.ofMinutes(30),
                Duration.ofMinutes(30),
                null,
                null,
                null,
                ""
        );
        LocalDateTime at = AutoSkipCalculator.computeAutoSkipAt(
                task,
                Duration.ofHours(2),
                Duration.ZERO
        );
        assertEquals(LocalDateTime.of(2026, 5, 10, 20, 30), at);
    }

    @Test
    public void computeAutoSkipAt_usesDefaultGraceWhenTaskHasNone() {
        ObsidianTask task = new ObsidianTask(
                "k",
                "s",
                1,
                "t",
                "- [ ] x @due(2026-05-10 18:00)",
                DUE,
                null,
                RepeatMode.NONE,
                false,
                false,
                "",
                0,
                0,
                Collections.emptyList(),
                TaskPriority.NONE,
                "g",
                null,
                null,
                null,
                null,
                null,
                null,
                ""
        );
        LocalDateTime at = AutoSkipCalculator.computeAutoSkipAt(
                task,
                Duration.ofHours(2),
                Duration.ofMinutes(30)
        );
        assertEquals(LocalDateTime.of(2026, 5, 10, 20, 30), at);
    }

    @Test
    public void computeAutoSkipAt_nullWhenNoDue() {
        ObsidianTask task = new ObsidianTask(
                "k",
                "s",
                1,
                "t",
                "- [ ] x",
                null,
                null,
                RepeatMode.NONE,
                false,
                false,
                "",
                0,
                0,
                Collections.emptyList(),
                TaskPriority.NONE,
                "g",
                null,
                null,
                null,
                null,
                null,
                null,
                ""
        );
        assertNull(AutoSkipCalculator.computeAutoSkipAt(task, Duration.ofHours(1), Duration.ZERO));
    }

    @Test
    public void autoSkipPreferences_clampDelayMinutes() {
        assertEquals(AutoSkipPreferences.MAX_DELAY_MINUTES, AutoSkipPreferences.clampDelayMinutes(99999));
        assertEquals(15, AutoSkipPreferences.clampDelayMinutes(15));
        assertEquals(AutoSkipPreferences.MIN_DELAY_MINUTES, AutoSkipPreferences.clampDelayMinutes(0));
    }
}
