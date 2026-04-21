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
    public void buildScheduledReminder_keepsReminderAtNow() {
        ObsidianTask task = new ObsidianTask(
                1,
                "Now task",
                "- [ ] Now task @2026-04-20 12:00",
                LocalDateTime.of(2026, 4, 20, 12, 0),
                null
        );

        ScheduledReminder reminder = ReminderScheduler.buildScheduledReminder(
                task,
                LocalDateTime.of(2026, 4, 20, 12, 0),
                UTC
        );

        assertEquals(LocalDateTime.of(2026, 4, 20, 12, 0), reminder.getTriggerAt());
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

    @Test
    public void buildScheduledReminder_keepsUntilDoneModeAndGroup() {
        ObsidianTask task = new ObsidianTask(
                "tasks.md|1|Nag||300000|UNTIL_DONE",
                "tasks.md",
                1,
                "Nag task",
                "- [ ] Nag task @due(2026-04-20 11:30) @repeatUntilDone(5m) @group(work)",
                LocalDateTime.of(2026, 4, 20, 11, 30),
                Duration.ofMinutes(5),
                RepeatMode.UNTIL_DONE,
                false,
                java.util.Collections.emptyList(),
                TaskPriority.NONE,
                "work"
        );

        ScheduledReminder reminder = ReminderScheduler.buildScheduledReminder(
                task,
                LocalDateTime.of(2026, 4, 20, 12, 1),
                UTC
        );

        assertEquals(LocalDateTime.of(2026, 4, 20, 12, 5), reminder.getTriggerAt());
        assertEquals(RepeatMode.UNTIL_DONE, reminder.getRepeatMode());
        assertEquals("work", reminder.getGroup());
        assertEquals(Duration.ofMinutes(5).toMillis(), reminder.getRepeatIntervalMillis());
    }

    @Test
    public void buildScheduledReminder_skipsPastReminderWithZeroRepeat() {
        ObsidianTask task = new ObsidianTask(
                1,
                "Zero repeat task",
                "- [ ] Zero repeat task @2026-04-20 11:30",
                LocalDateTime.of(2026, 4, 20, 11, 30),
                Duration.ZERO,
                RepeatMode.ALWAYS
        );

        ScheduledReminder reminder = ReminderScheduler.buildScheduledReminder(
                task,
                LocalDateTime.of(2026, 4, 20, 12, 1),
                UTC
        );

        assertNull(reminder);
    }
}
