package com.regstar.obsidiannotification.core.tasks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.time.LocalDate;

public final class TaskEditorInputValidatorTest {
    private static final LocalDate ANCHOR = LocalDate.of(2026, 5, 15);

    @Test
    public void firstFieldIssue_nullWhenAllOptionalEmpty() {
        assertNull(TaskEditorInputValidator.firstFieldIssue(
                ANCHOR,
                "",
                "",
                "",
                false,
                "",
                false,
                ""
        ));
    }

    @Test
    public void firstFieldIssue_invalidDue() {
        assertEquals(
                TaskEditorInputValidator.FieldIssue.INVALID_DUE,
                TaskEditorInputValidator.firstFieldIssue(
                        ANCHOR,
                        "not-a-date",
                        "",
                        "",
                        false,
                        "",
                        false,
                        ""
                ));
    }

    @Test
    public void firstFieldIssue_validDue_examples() {
        assertNull(TaskEditorInputValidator.firstFieldIssue(
                ANCHOR,
                "2026-04-20 19:00",
                "",
                "",
                false,
                "",
                false,
                ""
        ));
        assertNull(TaskEditorInputValidator.firstFieldIssue(
                ANCHOR,
                "19:00",
                "",
                "",
                false,
                "",
                false,
                ""
        ));
    }

    @Test
    public void firstFieldIssue_invalidRepeat() {
        assertEquals(
                TaskEditorInputValidator.FieldIssue.INVALID_REPEAT,
                TaskEditorInputValidator.firstFieldIssue(
                        ANCHOR,
                        "2026-05-15 10:00",
                        "%%%",
                        "",
                        false,
                        "",
                        false,
                        ""
                ));
    }

    @Test
    public void firstFieldIssue_validRepeatInterval() {
        assertNull(TaskEditorInputValidator.firstFieldIssue(
                ANCHOR,
                "2026-05-15 10:00",
                "1d",
                "",
                false,
                "",
                false,
                ""
        ));
    }

    @Test
    public void firstFieldIssue_invalidRepeatUntilDone() {
        assertEquals(
                TaskEditorInputValidator.FieldIssue.INVALID_REPEAT_UNTIL_DONE,
                TaskEditorInputValidator.firstFieldIssue(
                        ANCHOR,
                        "2026-05-15 10:00",
                        "",
                        "bogus",
                        true,
                        "",
                        false,
                        ""
                ));
    }

    @Test
    public void firstFieldIssue_invalidGrace() {
        assertEquals(
                TaskEditorInputValidator.FieldIssue.INVALID_GRACE,
                TaskEditorInputValidator.firstFieldIssue(
                        ANCHOR,
                        "2026-05-15 10:00",
                        "",
                        "",
                        false,
                        "bogus",
                        true,
                        ""
                ));
    }

    @Test
    public void firstFieldIssue_graceAllowsZero() {
        assertNull(TaskEditorInputValidator.firstFieldIssue(
                ANCHOR,
                "2026-05-15 10:00",
                "",
                "",
                false,
                "0m",
                true,
                ""
        ));
    }

    @Test
    public void firstFieldIssue_invalidSnooze() {
        assertEquals(
                TaskEditorInputValidator.FieldIssue.INVALID_SNOOZE,
                TaskEditorInputValidator.firstFieldIssue(
                        ANCHOR,
                        "2026-05-15 10:00",
                        "",
                        "",
                        false,
                        "",
                        false,
                        "bogus"
                ));
    }
}
