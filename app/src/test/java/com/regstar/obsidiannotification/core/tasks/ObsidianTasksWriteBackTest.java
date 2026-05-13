package com.regstar.obsidiannotification.core.tasks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDate;

public final class ObsidianTasksWriteBackTest {
    @Test
    public void complete_preservesRepeatIdDepsDue() {
        String line = "- [ ] \u041a\u0443\u043f\u0438\u0442\u044c \uD83D\uDD01 every week \uD83D\uDCC5 2026-05-01 \uD83C\uDD94 abc123 \u26D4 def456";
        String out = ObsidianTasksWriteBack.applyComplete(line, LocalDate.of(2026, 5, 13));
        assertTrue(out.contains("[x]"));
        assertTrue(out.contains("\uD83D\uDD01"));
        assertTrue(out.contains("\uD83D\uDCC5"));
        assertTrue(out.contains("\uD83C\uDD94"));
        assertTrue(out.contains("\u26D4"));
        assertTrue(out.contains("\u2705 2026-05-13"));
    }

    @Test
    public void complete_singleDoneEmoji() {
        String line = "- [x] A \u2705 2026-05-01";
        String out = ObsidianTasksWriteBack.applyComplete(line, LocalDate.of(2026, 5, 13));
        int first = out.indexOf("\u2705");
        int last = out.lastIndexOf("\u2705");
        assertEquals(first, last);
    }

    @Test
    public void rewriteDue_updatesCalendarEmojiOnly() {
        String line = "- [ ] A \uD83D\uDCC5 2026-05-01 \u23F0 2026-05-01 10:00";
        String out = ObsidianTasksWriteBack.rewriteObsidianDueDate(line, LocalDate.of(2026, 6, 1));
        assertTrue(out.contains("\uD83D\uDCC5 2026-06-01"));
        assertTrue(out.contains("\u23F0"));
    }
}
