package com.regstar.obsidiannotification.core.reminders;

import com.regstar.obsidiannotification.core.source.NoteStore;
import com.regstar.obsidiannotification.core.tasks.ObsidianTask;
import com.regstar.obsidiannotification.core.tasks.TaskEditResult;

import android.content.Context;

import java.io.IOException;
import java.time.ZoneId;

/**
 * Performs auto-skip using the same {@link NoteStore#markTaskSkipped} flow as manual skip.
 */
public final class AutoSkipExecutor {
    private AutoSkipExecutor() {
    }

    /**
     * Skips the task only if the live task still matches the expected head occurrence ({@code reminderAt} millis).
     *
     * @return {@code true} if skip applied or already resolved consistently with reminder teardown
     */
    public static boolean trySkipForOccurrence(
            Context context,
            String taskKey,
            long expectedReminderAtEpochMillis
    ) {
        if (taskKey == null || taskKey.trim().isEmpty()) {
            return false;
        }
        try {
            NoteStore.TaskDocumentMatch match = NoteStore.findTaskDocument(context, taskKey);
            if (match == null) {
                return false;
            }
            ObsidianTask task = match.getTask();
            if (task == null || task.getReminderAt() == null) {
                return false;
            }
            long actualMillis = task.getReminderAt()
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli();
            if (actualMillis != expectedReminderAtEpochMillis) {
                return false;
            }
            if (task.isCompleted() || task.isSkipped()) {
                return false;
            }

            TaskEditResult result = NoteStore.markTaskSkipped(context, taskKey);
            if (result.shouldStopReminder()) {
                ReminderScheduler.cancelReminder(context, taskKey);
            }
            return result.shouldStopReminder();
        } catch (IOException | RuntimeException ignored) {
            return false;
        }
    }
}
