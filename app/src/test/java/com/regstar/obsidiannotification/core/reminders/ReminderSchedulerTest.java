package com.regstar.obsidiannotification.core.reminders;

import com.regstar.obsidiannotification.core.source.*;
import com.regstar.obsidiannotification.core.tasks.*;
import com.regstar.obsidiannotification.prefs.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
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
    public void buildScheduledReminder_doesNotAutoAdvanceRepeatSeriesWithoutNag() {
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

        assertNull(reminder);
    }

    @Test
    public void buildScheduledReminder_keepsNagLoopForPastHeadOccurrence() {
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
                "work",
                null,
                null,
                null,
                Duration.ofMinutes(5),
                Duration.ofMinutes(5),
                null,
                ""
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
    public void buildScheduledReminder_keepsNagLoopForOneShotWithResolvedRepeatUntilDone() {
        ObsidianTask rawTask = new ObsidianTask(
                "tasks.md|1|Nag one-shot|2026-04-20T11:30||NONE",
                "tasks.md",
                1,
                "Nag one-shot",
                "- [ ] Nag one-shot @due(2026-04-20 11:30)",
                LocalDateTime.of(2026, 4, 20, 11, 30),
                null,
                RepeatMode.NONE,
                false,
                java.util.Collections.emptyList(),
                TaskPriority.NONE,
                "work",
                null,
                null,
                null,
                null,
                null,
                null,
                ""
        );
        ObsidianTask task = TaskDefaultsResolver.resolve(
                rawTask,
                Duration.ofMinutes(5),
                Duration.ofMinutes(0)
        );

        ScheduledReminder reminder = ReminderScheduler.buildScheduledReminder(
                task,
                LocalDateTime.of(2026, 4, 20, 12, 1),
                UTC
        );

        assertEquals(LocalDateTime.of(2026, 4, 20, 12, 5), reminder.getTriggerAt());
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

    @Test
    public void occurrenceNotificationId_staysStableForSameOccurrence() {
        ObsidianTask task = new ObsidianTask(
                7,
                "Stable occurrence",
                "- [ ] Stable occurrence @2026-04-20 11:30",
                LocalDateTime.of(2026, 4, 20, 11, 30),
                Duration.ofMinutes(5),
                RepeatMode.UNTIL_DONE
        );

        assertEquals(
                ReminderScheduler.occurrenceNotificationIdFor(task),
                ReminderScheduler.occurrenceNotificationIdFor(task)
        );
    }

    @Test
    public void occurrenceNotificationId_changesForNewOccurrence() {
        ObsidianTask firstOccurrence = new ObsidianTask(
                7,
                "Same task",
                "- [ ] Same task @2026-04-20 09:00",
                LocalDateTime.of(2026, 4, 20, 9, 0),
                Duration.ofMinutes(5),
                RepeatMode.UNTIL_DONE
        );
        ObsidianTask nextOccurrence = new ObsidianTask(
                7,
                "Same task",
                "- [ ] Same task @2026-04-20 09:05",
                LocalDateTime.of(2026, 4, 20, 9, 5),
                Duration.ofMinutes(5),
                RepeatMode.UNTIL_DONE
        );

        assertNotEquals(
                ReminderScheduler.occurrenceNotificationIdFor(firstOccurrence),
                ReminderScheduler.occurrenceNotificationIdFor(nextOccurrence)
        );
    }
}
