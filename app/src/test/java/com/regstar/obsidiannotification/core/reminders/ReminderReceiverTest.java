package com.regstar.obsidiannotification.core.reminders;

import com.regstar.obsidiannotification.core.source.*;
import com.regstar.obsidiannotification.core.tasks.*;
import com.regstar.obsidiannotification.prefs.*;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.Duration;

public final class ReminderReceiverTest {
    @Test
    public void hasNagLoop_trueForExplicitUntilDoneMode() {
        assertTrue(ReminderReceiver.hasNagLoop(0L, RepeatMode.UNTIL_DONE));
    }

    @Test
    public void hasNagLoop_trueForRepeatSeriesWithResolvedNagInterval() {
        assertTrue(ReminderReceiver.hasNagLoop(300_000L, RepeatMode.ALWAYS));
    }

    @Test
    public void hasNagLoop_falseForOneShotWithoutNagInterval() {
        assertFalse(ReminderReceiver.hasNagLoop(0L, RepeatMode.NONE));
    }

    @Test
    public void shouldRepostNotification_trueForNagUpdates() {
        assertTrue(ReminderReceiver.shouldRepostNotification(300_000L, RepeatMode.ALWAYS));
    }

    @Test
    public void shouldUseActiveTaskNagInterval_falseForOneShotWithDefaultInterval() {
        ObsidianTask oneShotTask = new ObsidianTask(
                "tasks.md|1|One-shot|2026-04-20T11:30||NONE",
                "tasks.md",
                1,
                "One-shot",
                "- [ ] One-shot @due(2026-04-20 11:30)",
                java.time.LocalDateTime.of(2026, 4, 20, 11, 30),
                null,
                RepeatMode.NONE,
                false,
                java.util.Collections.emptyList(),
                TaskPriority.NONE,
                "default",
                null,
                null,
                null,
                Duration.ofMinutes(5),
                Duration.ofMinutes(5),
                null,
                ""
        );

        assertFalse(ReminderReceiver.shouldUseActiveTaskNagInterval(oneShotTask));
    }

    @Test
    public void shouldUseActiveTaskNagInterval_trueForUntilDoneWithResolvedInterval() {
        ObsidianTask untilDoneTask = new ObsidianTask(
                "tasks.md|1|Nag||300000|UNTIL_DONE",
                "tasks.md",
                1,
                "Nag",
                "- [ ] Nag @due(2026-04-20 11:30) @repeatUntilDone(5m)",
                java.time.LocalDateTime.of(2026, 4, 20, 11, 30),
                Duration.ofMinutes(5),
                RepeatMode.UNTIL_DONE,
                false,
                java.util.Collections.emptyList(),
                TaskPriority.NONE,
                "default",
                null,
                null,
                null,
                Duration.ofMinutes(5),
                Duration.ofMinutes(5),
                null,
                ""
        );

        assertTrue(ReminderReceiver.shouldUseActiveTaskNagInterval(untilDoneTask));
    }

    @Test
    public void shouldUseActiveTaskNagInterval_falseForNullTask() {
        assertFalse(ReminderReceiver.shouldUseActiveTaskNagInterval(null));
    }
}
