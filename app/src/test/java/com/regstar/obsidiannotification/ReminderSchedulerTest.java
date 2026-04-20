package com.regstar.obsidiannotification;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;

public final class ReminderSchedulerTest {
    private static final ZoneId UTC = ZoneId.of("UTC");

    @Test
    public void buildScheduledReminder_usesFutureReminder() {
        ObsidianTask task = new ObsidianTask(
                1,
                "Future task",
                "- [ ] Future task @2026-04-20 12:30",
                LocalDateTime.of(2026, 4, 20, 12, 30),
                null
        );

        ScheduledReminder reminder = ReminderScheduler.buildScheduledReminder(
                task,
                LocalDateTime.of(2026, 4, 20, 12, 0),
                UTC
        );

        assertEquals(LocalDateTime.of(2026, 4, 20, 12, 30), reminder.getTriggerAt());
        assertEquals("Future task", reminder.getTitle());
        assertEquals(RepeatMode.NONE, reminder.getRepeatMode());
    }

    @Test
    public void buildScheduledReminder_skipsPastOneShotReminder() {
        ObsidianTask task = new ObsidianTask(
                1,
                "Past task",
                "- [ ] Past task @2026-04-20 11:30",
                LocalDateTime.of(2026, 4, 20, 11, 30),
                null
        );

        ScheduledReminder reminder = ReminderScheduler.buildScheduledReminder(
                task,
                LocalDateTime.of(2026, 4, 20, 12, 0),
                UTC
        );

        assertNull(reminder);
    }

    @Test
    public void buildScheduledReminder_movesRepeatingReminderToNextFutureTime() {
        ObsidianTask task = new ObsidianTask(
                1,
                "Repeating task",
                "- [ ] Repeating task @2026-04-20 11:30 every 15m",
                LocalDateTime.of(2026, 4, 20, 11, 30),
                Duration.ofMinutes(15)
        );

        ScheduledReminder reminder = ReminderScheduler.buildScheduledReminder(
                task,
                LocalDateTime.of(2026, 4, 20, 12, 1),
                UTC
        );

        assertEquals(LocalDateTime.of(2026, 4, 20, 12, 15), reminder.getTriggerAt());
        assertEquals(RepeatMode.ALWAYS, reminder.getRepeatMode());
    }
}
