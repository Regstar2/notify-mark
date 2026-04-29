package com.regstar.obsidiannotification.core.source;

import com.regstar.obsidiannotification.core.tasks.ObsidianTask;
import com.regstar.obsidiannotification.core.tasks.TaskParseError;
import com.regstar.obsidiannotification.core.tasks.TaskParseResult;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

public final class NoteChangeMonitorFingerprintTest {
    @Test
    public void fingerprint_doesNotDependOnLocale() {
        ObsidianTask task = new ObsidianTask(
                1,
                "Task",
                "- [ ] Task @2026-04-20 12:30",
                LocalDateTime.of(2026, 4, 20, 12, 30),
                null
        );
        TaskParseError error = new TaskParseError(
                "tasks.md",
                7,
                TaskParseError.Kind.DUE_FUNCTION_INVALID,
                "due"
        );
        TaskParseResult result = new TaskParseResult(List.of(task), List.of(error));

        Locale oldDefault = Locale.getDefault();
        try {
            Locale.setDefault(Locale.US);
            String us = NoteChangeMonitor.fingerprintOf(result);
            Locale.setDefault(new Locale("ru", "RU"));
            String ru = NoteChangeMonitor.fingerprintOf(result);
            assertEquals(us, ru);
        } finally {
            Locale.setDefault(oldDefault);
        }
    }
}

