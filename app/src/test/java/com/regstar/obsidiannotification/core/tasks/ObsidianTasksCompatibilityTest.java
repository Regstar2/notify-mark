package com.regstar.obsidiannotification.core.tasks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Parser, write-back, and reminder integration for Obsidian Tasks emoji metadata (v0.10.0).
 */
public final class ObsidianTasksCompatibilityTest {
    private static TaskFormatSettings auto() {
        return TaskFormatSettings.defaults();
    }

    private static TaskFormatSettings nativeOnly() {
        return TaskFormatSettings.fromValues(
                TaskFormatSettings.DEFAULT_DUE_KEYWORD,
                TaskFormatSettings.DEFAULT_REPEAT_KEYWORD,
                TaskFormatSettings.DEFAULT_REPEAT_UNTIL_DONE_KEYWORD,
                TaskFormatSettings.DEFAULT_TAG_KEYWORD,
                TaskFormatSettings.DEFAULT_PRIORITY_KEYWORD,
                TaskFormatSettings.DEFAULT_GROUP_KEYWORD,
                TaskFormatCompatibilityMode.NATIVE,
                9 * 60
        );
    }

    @Test
    public void parse_obsidianDue_setsReminderWithDefaultTime() {
        String md = "- [ ] Buy milk \uD83D\uDCC5 2026-05-20";
        TaskParseResult r = TaskParser.parseDocument(md, LocalDate.of(2026, 5, 1), "t.md", auto());
        ObsidianTask t = r.getTasks().get(0);
        assertEquals(LocalDateTime.of(2026, 5, 20, 9, 0), t.getReminderAt());
        assertEquals(TaskSyntaxStyle.OBSIDIAN_TASKS, t.getLineMetadata().getSyntaxStyle());
    }

    @Test
    public void parse_obsidianReminderExactTime() {
        String md = "- [ ] Call \u23F0 2026-05-20 14:30";
        TaskParseResult r = TaskParser.parseDocument(md, LocalDate.of(2026, 5, 1), "t.md", auto());
        assertEquals(LocalDateTime.of(2026, 5, 20, 14, 30), r.getTasks().get(0).getReminderAt());
    }

    @Test
    public void parse_reminderDatePlusDueTimeCombines() {
        String md = "- [ ] Mix \uD83D\uDCC5 2026-05-21 \u23F0 08:15";
        TaskParseResult r = TaskParser.parseDocument(md, LocalDate.of(2026, 5, 1), "t.md", auto());
        assertEquals(LocalDateTime.of(2026, 5, 21, 8, 15), r.getTasks().get(0).getReminderAt());
    }

    @Test
    public void parse_reminderWinsOverDueForDifferentTimes() {
        String md = "- [ ] X \u23F0 2026-05-10 12:00 \uD83D\uDCC5 2026-05-11";
        TaskParseResult r = TaskParser.parseDocument(md, LocalDate.of(2026, 5, 1), "t.md", auto());
        assertEquals(LocalDateTime.of(2026, 5, 10, 12, 0), r.getTasks().get(0).getReminderAt());
    }

    @Test
    public void parse_nativeExplicitDueWinsOverObsidian() {
        String md = "- [ ] X @due(2026-05-12 11:00) \uD83D\uDCC5 2026-05-13 \u23F0 2026-05-13 09:00";
        TaskParseResult r = TaskParser.parseDocument(md, LocalDate.of(2026, 5, 1), "t.md", auto());
        ObsidianTask t = r.getTasks().get(0);
        assertEquals(LocalDateTime.of(2026, 5, 12, 11, 0), t.getReminderAt());
        assertTrue(t.getLineMetadata().hasObsidianMetadataConflict());
        assertTrue(r.getErrors().stream().anyMatch(e ->
                e.getKind() == TaskParseError.Kind.OBSIDIAN_METADATA_CONFLICT));
    }

    @Test
    public void parse_nativeModeIgnoresObsidianDates() {
        String md = "- [ ] X \uD83D\uDCC5 2026-05-20";
        TaskParseResult r = TaskParser.parseDocument(md, LocalDate.of(2026, 5, 1), "t.md", nativeOnly());
        assertNull(r.getTasks().get(0).getReminderAt());
    }

    @Test
    public void parse_doneEmojiMarksCompleted() {
        String md = "- [ ] Done \u2705 2026-04-30";
        TaskParseResult r = TaskParser.parseDocument(md, LocalDate.of(2026, 5, 1), "t.md", auto());
        assertEquals(1, r.getTasks().size());
        assertTrue(r.getTasks().get(0).isCompleted());
        assertTrue(r.getActiveTasks().isEmpty());
    }

    @Test
    public void parse_orderedListCheckbox() {
        String md = "1. [ ] Ordered task \uD83D\uDCC5 2026-06-01";
        TaskParseResult r = TaskParser.parseDocument(md, LocalDate.of(2026, 5, 1), "t.md", auto());
        assertEquals(1, r.getTasks().size());
        assertEquals(1, r.getTasks().get(0).getLineNumber());
    }

    @Test
    public void parse_repeatEveryWeekMaps() {
        String md = "- [ ] Gym \uD83D\uDD01 every week \uD83D\uDCC5 2026-05-05";
        TaskParseResult r = TaskParser.parseDocument(md, LocalDate.of(2026, 5, 1), "t.md", auto());
        RepeatRule rule = r.getTasks().get(0).getRepeatRule();
        assertNotNull(rule);
        assertEquals(RepeatRule.Unit.WEEKS, rule.getUnit());
    }

    @Test
    public void parse_repeatEveryYearUnsupported() {
        String md = "- [ ] Tax \uD83D\uDD01 every year \uD83D\uDCC5 2026-04-15";
        TaskParseResult r = TaskParser.parseDocument(md, LocalDate.of(2026, 5, 1), "t.md", auto());
        assertNull(r.getTasks().get(0).getRepeatRule());
        assertTrue(r.getTasks().get(0).getLineMetadata().isRecurrenceUnsupported());
    }

    @Test
    public void writeBack_completeAddsDoneDate() {
        String line = "- [ ] A \uD83D\uDCC5 2026-05-01";
        String out = ObsidianTasksWriteBack.applyComplete(line, LocalDate.of(2026, 5, 13));
        assertTrue(out.contains("[x]"));
        assertTrue(out.contains("\u2705 2026-05-13"));
        assertTrue(out.contains("\uD83D\uDCC5 2026-05-01"));
    }

    @Test
    public void writeBack_secondCompleteDoesNotDuplicateDoneEmoji() {
        String line = "- [x] A \uD83D\uDCC5 2026-05-01 \u2705 2026-05-01";
        String out = ObsidianTasksWriteBack.applyComplete(line, LocalDate.of(2026, 5, 13));
        int first = out.indexOf("\u2705");
        int last = out.lastIndexOf("\u2705");
        assertEquals(first, last);
    }

    @Test
    public void parse_invalidObsidianDateDoesNotCrash() {
        String md = "- [ ] Bad \uD83D\uDCC5 2026-13-40";
        TaskParseResult r = TaskParser.parseDocument(md, LocalDate.of(2026, 5, 1), "t.md", auto());
        assertFalse(r.getTasks().isEmpty());
    }

    @Test
    public void authoring_dueDateOnly_insertsCalendarEmoji() {
        StringBuilder b = new StringBuilder("- [ ] T");
        assertTrue(ObsidianTasksLineAuthoring.tryAppendDueAndReminder(b, "2026-05-20", auto()));
        assertTrue(b.toString().contains("\uD83D\uDCC5 2026-05-20"));
        assertFalse(b.toString().contains("@due("));
    }

    @Test
    public void authoring_dueWithTime_insertsDueAndClockEmoji() {
        StringBuilder b = new StringBuilder("- [ ] T");
        assertTrue(ObsidianTasksLineAuthoring.tryAppendDueAndReminder(b, "2026-05-20 14:30", auto()));
        String s = b.toString();
        assertTrue(s.contains("\uD83D\uDCC5 2026-05-20"));
        assertTrue(s.contains("\u23F0 2026-05-20 14:30"));
    }

    @Test
    public void authoring_repeatWeeklyPhraseFromUiToken() {
        assertEquals("every week", ObsidianTasksLineAuthoring.repeatUiToObsidianPhrase("1w"));
    }

    @Test
    public void displayTitle_removesReminderAndDueValues() {
        String md = "- [ ] \u0412\u0441\u0442\u0440\u0435\u0447\u0430 \u23F0 2026-05-10 12:00 \uD83D\uDCC5 2026-05-11";
        TaskParseResult r = TaskParser.parseDocument(md, LocalDate.of(2026, 5, 1), "t.md", auto());
        assertEquals("\u0412\u0441\u0442\u0440\u0435\u0447\u0430", r.getTasks().get(0).getTitle());
    }

    @Test
    public void displayTitle_nativeDueWins_stripsObsidianFromTitle() {
        String md = "- [ ] \u0421\u043c\u0435\u0441\u044c @due(2026-05-12 11:00) \uD83D\uDCC5 2026-05-13 \u23F0 2026-05-13 09:00";
        TaskParseResult r = TaskParser.parseDocument(md, LocalDate.of(2026, 5, 1), "t.md", auto());
        assertEquals("\u0421\u043c\u0435\u0441\u044c", r.getTasks().get(0).getTitle());
        assertEquals(LocalDateTime.of(2026, 5, 12, 11, 0), r.getTasks().get(0).getReminderAt());
    }

    @Test
    public void displayTitle_removesIdDepDueSpans() {
        String md = "- [ ] \u0421\u0432\u044f\u0437\u043a\u0430 \uD83C\uDD94 abc123 \u26D4 def456,ghi789 \uD83D\uDCC5 2026-05-13";
        TaskParseResult r = TaskParser.parseDocument(md, LocalDate.of(2026, 5, 1), "t.md", auto());
        assertEquals("\u0421\u0432\u044f\u0437\u043a\u0430", r.getTasks().get(0).getTitle());
    }

    @Test
    public void displayTitle_preservesUnknownEmoji() {
        String md = "- [ ] \u0417\u0430\u0434\u0430\u0447\u0430 \uD83D\uDE80 \u23F0 2026-05-13 10:00";
        TaskParseResult r = TaskParser.parseDocument(md, LocalDate.of(2026, 5, 1), "t.md", auto());
        String title = r.getTasks().get(0).getTitle();
        assertTrue(title.contains("\uD83D\uDE80"));
        assertFalse(title.contains("2026-05-13"));
    }

    @Test
    public void displayTitle_preservesTagsWikiLinksBlockId() {
        String md = "- [ ] \u041f\u043e\u043a\u0443\u043f\u043a\u0438 #home #work/project [[Note]] [x](https://ex.com) \uD83D\uDCC5 2026-05-13 ^bid";
        TaskParseResult r = TaskParser.parseDocument(md, LocalDate.of(2026, 5, 1), "t.md", auto());
        String title = r.getTasks().get(0).getTitle();
        assertTrue(title.contains("#home"));
        assertTrue(title.contains("[[Note]]"));
        assertTrue(title.contains("^bid"));
    }

    @Test
    public void parse_parentAndSubtaskDisplayTitles() {
        String md = "- [ ] \u0420\u043e\u0434\u0438\u0442\u0435\u043b\u044c \uD83D\uDCC5 2026-05-25\n"
                + "  - [ ] \u041f\u043e\u0434\u0437\u0430\u0434\u0430\u0447\u0430 \u23F0 2026-05-25 08:00\n"
                + "  - [ ] \u0412\u0442\u043e\u0440\u0430\u044f \uD83D\uDCC5 2026-05-26";
        TaskParseResult r = TaskParser.parseDocument(md, LocalDate.of(2026, 5, 1), "t.md", auto());
        assertEquals("\u0420\u043e\u0434\u0438\u0442\u0435\u043b\u044c", r.getTasks().get(0).getTitle());
        assertEquals("\u041f\u043e\u0434\u0437\u0430\u0434\u0430\u0447\u0430", r.getTasks().get(0).getSubtasks().get(0).getTitle());
        assertEquals("\u0412\u0442\u043e\u0440\u0430\u044f", r.getTasks().get(0).getSubtasks().get(1).getTitle());
    }

    @Test
    public void parse_whenDone_doesNotMapToRepeatRule() {
        String md = "- [ ] T \uD83D\uDD01 every day when done \uD83D\uDCC5 2026-05-13";
        TaskParseResult r = TaskParser.parseDocument(md, LocalDate.of(2026, 5, 1), "t.md", auto());
        ObsidianTask t = r.getTasks().get(0);
        assertNull(t.getRepeatRule());
        assertTrue(t.getLineMetadata().isRecurrenceWhenDone());
        assertTrue(t.getLineMetadata().isRecurrenceUnsupported());
        assertTrue(r.getErrors().stream().anyMatch(e ->
                e.getKind() == TaskParseError.Kind.OBSIDIAN_RECURRENCE_UNSUPPORTED));
    }

    @Test
    public void parse_complexRecurrence_unsupportedNoRule() {
        String md = "- [ ] \u0421\u043b\u043e\u0436\u043d\u043e\u0435 NL \uD83D\uDD01 every 2 months on the last Friday \uD83D\uDCC5 2026-05-13";
        TaskParseResult r = TaskParser.parseDocument(md, LocalDate.of(2026, 5, 1), "t.md", auto());
        assertEquals("\u0421\u043b\u043e\u0436\u043d\u043e\u0435 NL", r.getTasks().get(0).getTitle());
        assertNull(r.getTasks().get(0).getRepeatRule());
        assertTrue(r.getTasks().get(0).getLineMetadata().isRecurrenceUnsupported());
    }

    @Test
    public void parse_fencedPlainBackticksIgnoresInnerTask() {
        String md = "```\n- [ ] Inner \uD83D\uDCC5 2026-06-01\n```\n- [ ] Outer \uD83D\uDCC5 2026-06-02\n";
        TaskParseResult r = TaskParser.parseDocument(md, LocalDate.of(2026, 5, 1), "t.md", TaskFormatSettings.defaults());
        assertEquals(1, r.getTasks().size());
        assertEquals("Outer", r.getTasks().get(0).getTitle());
    }

    @Test
    public void parse_longTitle_markdownLinksWikiBlockId_noInvalidObsidianDateError() {
        String md = "- [ ] \u041f\u043e\u043a\u0443\u043f\u043a\u0438 #home #work/project [[\u0417\u0430\u043c\u0435\u0442\u043a\u0430]] [\u0421\u0430\u0439\u0442](https://example.com) \uD83D\uDCC5 2026-05-13 ^blockid-demo\n";
        TaskParseResult r = TaskParser.parseDocument(md, LocalDate.of(2026, 5, 13), "t.md", auto());
        assertTrue(r.getErrors().stream().noneMatch(e ->
                e.getKind() == TaskParseError.Kind.OBSIDIAN_INVALID_DATE_OR_TIME));
        ObsidianTask t = r.getTasks().get(0);
        assertEquals(LocalDateTime.of(2026, 5, 13, 9, 0), t.getReminderAt());
        assertTrue(t.getTitle().contains("#home"));
        assertTrue(t.getTitle().contains("[[\u0417\u0430\u043c\u0435\u0442\u043a\u0430]]"));
        assertTrue(t.getTitle().contains("^blockid-demo"));
    }

    @Test
    public void displayTitle_onlyMetadataUsesFallback() {
        String md = "- [ ] \u23F0 2026-05-21";
        TaskParseResult r = TaskParser.parseDocument(md, LocalDate.of(2026, 5, 1), "t.md", auto());
        assertEquals("\u0417\u0430\u0434\u0430\u0447\u0430 \u0431\u0435\u0437 \u043d\u0430\u0437\u0432\u0430\u043d\u0438\u044f", r.getTasks().get(0).getTitle());
    }
}
