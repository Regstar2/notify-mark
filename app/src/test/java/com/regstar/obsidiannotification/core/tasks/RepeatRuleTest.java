package com.regstar.obsidiannotification.core.tasks;

import com.regstar.obsidiannotification.core.source.*;
import com.regstar.obsidiannotification.core.tasks.*;
import com.regstar.obsidiannotification.prefs.*;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.EnumSet;

public final class RepeatRuleTest {
    @Test
    public void nextDueAfter_keepsCalendarAnchorForWeeklyDays() {
        RepeatRule rule = RepeatRule.weekly(
                1,
                EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
        );

        LocalDateTime next = rule.nextDueAfter(LocalDateTime.of(2026, 4, 20, 9, 0));

        assertEquals(LocalDateTime.of(2026, 4, 22, 9, 0), next);
    }

    @Test
    public void nextDueAfter_clampsMonthlyDayToLastDayOfMonth() {
        RepeatRule rule = RepeatRule.monthly(1, 31);

        LocalDateTime next = rule.nextDueAfter(LocalDateTime.of(2026, 1, 31, 20, 0));

        assertEquals(LocalDateTime.of(2026, 2, 28, 20, 0), next);
    }

    @Test
    public void nextDueAfter_supportsLastDaySelector() {
        RepeatRule rule = RepeatRule.monthlyLastDay(1);

        LocalDateTime next = rule.nextDueAfter(LocalDateTime.of(2026, 4, 30, 20, 0));

        assertEquals(LocalDateTime.of(2026, 5, 31, 20, 0), next);
    }
}
