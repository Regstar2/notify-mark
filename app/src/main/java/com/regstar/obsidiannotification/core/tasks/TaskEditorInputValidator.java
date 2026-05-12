package com.regstar.obsidiannotification.core.tasks;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Lightweight editor-side checks that mirror parser acceptance for common tokens,
 * so users see targeted messages before generic parse errors.
 */
public final class TaskEditorInputValidator {
    public enum FieldIssue {
        INVALID_DUE,
        INVALID_REPEAT,
        INVALID_REPEAT_UNTIL_DONE,
        INVALID_GRACE,
        INVALID_SNOOZE
    }

    private TaskEditorInputValidator() {
    }

    /**
     * @param defaultDateForDue anchor date for time-only due parts (same idea as parsing)
     */
    public static FieldIssue firstFieldIssue(
            LocalDate defaultDateForDue,
            String dueCombined,
            String repeatToken,
            String repeatUntilDoneToken,
            boolean repeatUntilDoneEnabled,
            String graceToken,
            boolean graceEnabled,
            String snoozeToken
    ) {
        LocalDate anchor = defaultDateForDue != null ? defaultDateForDue : LocalDate.now();

        String due = dueCombined == null ? "" : dueCombined.trim();
        if (!due.isEmpty()) {
            LocalDateTime parsedDue = TaskParser.tryParseDueInput(due, anchor);
            if (parsedDue == null) {
                return FieldIssue.INVALID_DUE;
            }
        }

        String repeat = repeatToken == null ? "" : repeatToken.trim();
        if (!repeat.isEmpty()) {
            if (RepeatRule.parseStoredSpec(repeat) == null) {
                return FieldIssue.INVALID_REPEAT;
            }
        }

        if (repeatUntilDoneEnabled) {
            String rud = repeatUntilDoneToken == null ? "" : repeatUntilDoneToken.trim();
            if (!rud.isEmpty()) {
                Duration d = TaskParser.tryParseDurationToken(rud);
                if (d == null) {
                    return FieldIssue.INVALID_REPEAT_UNTIL_DONE;
                }
            }
        }

        if (graceEnabled) {
            String grace = graceToken == null ? "" : graceToken.trim();
            if (!grace.isEmpty()) {
                Duration d = TaskParser.tryParseDurationTokenAllowZero(grace);
                if (d == null) {
                    return FieldIssue.INVALID_GRACE;
                }
            }
        }

        String snooze = snoozeToken == null ? "" : snoozeToken.trim();
        if (!snooze.isEmpty()) {
            Duration d = TaskParser.tryParseDurationToken(snooze);
            if (d == null) {
                return FieldIssue.INVALID_SNOOZE;
            }
        }

        return null;
    }
}
