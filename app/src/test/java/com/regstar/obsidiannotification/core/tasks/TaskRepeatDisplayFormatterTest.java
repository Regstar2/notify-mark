package com.regstar.obsidiannotification.core.tasks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.Duration;
import java.time.LocalDate;

public final class TaskRepeatDisplayFormatterTest {
    private static TaskFormatSettings auto() {
        return TaskFormatSettings.defaults();
    }

    private static ObsidianTask parse(String md) {
        return TaskParser.parseDocument(md, LocalDate.of(2026, 5, 1), "t.md", auto()).getTasks().get(0);
    }

    @Test
    public void short_hours() {
        assertEquals("\u043a\u0430\u0436\u0434\u044b\u0439 \u0447\u0430\u0441", TaskRepeatDisplayFormatter.formatShortMappedRule(RepeatRule.interval(1, RepeatRule.Unit.HOURS)));
        assertEquals("\u043a\u0430\u0436\u0434\u044b\u0435 2 \u0447", TaskRepeatDisplayFormatter.formatShortMappedRule(RepeatRule.interval(2, RepeatRule.Unit.HOURS)));
        assertEquals("\u043a\u0430\u0436\u0434\u044b\u0435 12 \u0447", TaskRepeatDisplayFormatter.formatShortMappedRule(RepeatRule.interval(12, RepeatRule.Unit.HOURS)));
    }

    @Test
    public void short_daysAndWeeksAndMonths() {
        assertEquals("\u0435\u0436\u0435\u0434\u043d\u0435\u0432\u043d\u043e", TaskRepeatDisplayFormatter.formatShortMappedRule(RepeatRule.interval(1, RepeatRule.Unit.DAYS)));
        assertEquals("\u043a\u0430\u0436\u0434\u044b\u0435 2 \u0434\u043d.", TaskRepeatDisplayFormatter.formatShortMappedRule(RepeatRule.interval(2, RepeatRule.Unit.DAYS)));
        assertEquals("\u0435\u0436\u0435\u043d\u0435\u043b\u044c\u043d\u043e", TaskRepeatDisplayFormatter.formatShortMappedRule(RepeatRule.interval(1, RepeatRule.Unit.WEEKS)));
        assertEquals("\u043a\u0430\u0436\u0434\u044b\u0435 3 \u043d\u0435\u0434.", TaskRepeatDisplayFormatter.formatShortMappedRule(RepeatRule.interval(3, RepeatRule.Unit.WEEKS)));
        assertEquals("\u0435\u0436\u0435\u043c\u0435\u0441\u044f\u0447\u043d\u043e", TaskRepeatDisplayFormatter.formatShortMappedRule(RepeatRule.interval(1, RepeatRule.Unit.MONTHS)));
        assertEquals("\u043a\u0430\u0436\u0434\u044b\u0435 2 \u043c\u0435\u0441.", TaskRepeatDisplayFormatter.formatShortMappedRule(RepeatRule.interval(2, RepeatRule.Unit.MONTHS)));
    }

    @Test
    public void short_untilDonePhrases() {
        assertEquals("\u0434\u043e \u0432\u044b\u043f\u043e\u043b\u043d\u0435\u043d\u0438\u044f 5 \u043c\u0438\u043d.", TaskRepeatDisplayFormatter.formatShortUntilDonePhrase(Duration.ofMinutes(5)));
        assertEquals("\u0434\u043e \u0432\u044b\u043f\u043e\u043b\u043d\u0435\u043d\u0438\u044f 1 \u0447", TaskRepeatDisplayFormatter.formatShortUntilDonePhrase(Duration.ofHours(1)));
        assertEquals("\u0434\u043e \u0432\u044b\u043f\u043e\u043b\u043d\u0435\u043d\u0438\u044f 12 \u0447", TaskRepeatDisplayFormatter.formatShortUntilDonePhrase(Duration.ofHours(12)));
    }

    @Test
    public void short_weekdaySelector() {
        assertEquals("\u043f\u043e \u0431\u0443\u0434\u043d\u044f\u043c", TaskRepeatDisplayFormatter.formatShortMappedRule(
                RepeatRule.weekly(1, RepeatRule.parseDaysValue("weekdays"))));
        assertEquals("\u043f\u043e \u0432\u044b\u0445\u043e\u0434\u043d\u044b\u043c", TaskRepeatDisplayFormatter.formatShortMappedRule(
                RepeatRule.weekly(1, RepeatRule.parseDaysValue("weekends"))));
    }

    @Test
    public void short_weeklyMondayAndThreeDays() {
        assertEquals("\u0435\u0436\u0435\u043d\u0435\u043b\u044c\u043d\u043e \u00b7 \u043f\u043d", TaskRepeatDisplayFormatter.formatShortMappedRule(
                RepeatRule.weekly(1, RepeatRule.parseDaysValue("mon"))));
        assertEquals("\u0435\u0436\u0435\u043d\u0435\u043b\u044c\u043d\u043e \u00b7 \u043f\u043d, \u0441\u0440, \u043f\u0442", TaskRepeatDisplayFormatter.formatShortMappedRule(
                RepeatRule.weekly(1, RepeatRule.parseDaysValue("mon,wed,fri"))));
    }

    @Test
    public void short_weeklyManySelectedDays() {
        String line = TaskRepeatDisplayFormatter.formatShortMappedRule(
                RepeatRule.weekly(1, RepeatRule.parseDaysValue("mon,tue,wed,thu")));
        assertTrue(line.contains("\u0432\u044b\u0431\u0440\u0430\u043d\u043d\u044b\u0435 \u0434\u043d\u0438"));
        assertFalse(line.contains("@days"));
        assertFalse(line.contains("\u043d\u0435\u0441\u043a\u043e\u043b\u044c\u043a\u043e \u0434\u043d\u0435\u0439"));
    }

    @Test
    public void short_card_native12hAndUntilDone() {
        String md = "- [ ] T @due(2026-05-01 08:00) @repeat(12h) @repeatUntilDone(5m)\n";
        String line = TaskRepeatDisplayFormatter.formatShortCardLine(parse(md));
        assertEquals("\u043a\u0430\u0436\u0434\u044b\u0435 12 \u0447 \u00b7 \u0434\u043e \u0432\u044b\u043f\u043e\u043b\u043d\u0435\u043d\u0438\u044f 5 \u043c\u0438\u043d.", line);
        assertFalse(line.contains("\uD83D\uDD01"));
    }

    @Test
    public void short_card_dailyAndUntilDone() {
        String md = "- [ ] T @due(2026-05-01 08:00) @repeat(1d) @repeatUntilDone(2m)\n";
        assertEquals("\u0435\u0436\u0435\u0434\u043d\u0435\u0432\u043d\u043e \u00b7 \u0434\u043e \u0432\u044b\u043f\u043e\u043b\u043d\u0435\u043d\u0438\u044f 2 \u043c\u0438\u043d.",
                TaskRepeatDisplayFormatter.formatShortCardLine(parse(md)));
    }

    @Test
    public void short_card_weeklyDaysAndUntilDone() {
        String md = "- [ ] T @due(2026-05-01 08:00) @repeat(1w) @days(mon,wed,fri) @repeatUntilDone(5m)\n";
        assertEquals("\u0435\u0436\u0435\u043d\u0435\u043b\u044c\u043d\u043e \u00b7 \u043f\u043d, \u0441\u0440, \u043f\u0442 \u00b7 \u0434\u043e \u0432\u044b\u043f\u043e\u043b\u043d\u0435\u043d\u0438\u044f 5 \u043c\u0438\u043d.",
                TaskRepeatDisplayFormatter.formatShortCardLine(parse(md)));
    }

    @Test
    public void mapped_weekdays_shortCard() {
        String md = "- [ ] B \uD83D\uDD01 every weekday \uD83D\uDCC5 2026-05-13";
        assertEquals("\u043f\u043e \u0431\u0443\u0434\u043d\u044f\u043c", TaskRepeatDisplayFormatter.formatShortCardLine(parse(md)));
    }

    @Test
    public void mapped_monday_shortCore() {
        String md = "- [ ] B \uD83D\uDD01 every week on Monday \uD83D\uDCC5 2026-05-13";
        assertEquals("\u0435\u0436\u0435\u043d\u0435\u043b\u044c\u043d\u043e \u00b7 \u043f\u043d", TaskRepeatDisplayFormatter.formatShortRepeatCore(parse(md)));
    }

    @Test
    public void whenDone_shortVsFull() {
        String md = "- [ ] B \uD83D\uDD01 every day when done \uD83D\uDCC5 2026-05-13";
        ObsidianTask t = parse(md);
        assertEquals("\u043f\u043e\u0441\u043b\u0435 \u0432\u044b\u043f\u043e\u043b\u043d\u0435\u043d\u0438\u044f \u043d\u0435 \u043f\u043e\u0434\u0434\u0435\u0440\u0436\u0430\u043d", TaskRepeatDisplayFormatter.formatShortRepeatCore(t));
        String full = TaskRepeatDisplayFormatter.formatFullPrimaryRepeat(t);
        assertTrue(full.startsWith("\u26A0"));
        assertTrue(full.contains("every day when done"));
        assertFalse(full.contains("1d"));
    }

    @Test
    public void unsupportedYear_shortVsFull() {
        String md = "- [ ] B \uD83D\uDD01 every year \uD83D\uDCC5 2026-05-13";
        ObsidianTask t = parse(md);
        assertEquals("\u043f\u043e\u0432\u0442\u043e\u0440 \u043d\u0435 \u043f\u043e\u0434\u0434\u0435\u0440\u0436\u0430\u043d", TaskRepeatDisplayFormatter.formatShortRepeatCore(t));
        String full = TaskRepeatDisplayFormatter.formatFullPrimaryRepeat(t);
        assertTrue(full.startsWith("\u26A0"));
        assertTrue(full.contains("every year"));
    }

    @Test
    public void full_unsupportedComplexPhrase() {
        String md = "- [ ] B \uD83D\uDD01 every 2 months on the last Friday \uD83D\uDCC5 2026-05-13";
        String full = TaskRepeatDisplayFormatter.formatFullPrimaryRepeat(parse(md));
        assertTrue(full.contains("every 2 months on the last Friday"));
    }

    @Test
    public void formatPrimaryRepeat_matchesFull() {
        String md = "- [ ] B \uD83D\uDD01 every year \uD83D\uDCC5 2026-05-13";
        assertEquals(
                TaskRepeatDisplayFormatter.formatFullPrimaryRepeat(parse(md)),
                TaskRepeatDisplayFormatter.formatPrimaryRepeat(parse(md)));
    }
}
