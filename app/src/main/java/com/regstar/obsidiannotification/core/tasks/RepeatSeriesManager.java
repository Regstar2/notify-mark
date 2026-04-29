package com.regstar.obsidiannotification.core.tasks;

import com.regstar.obsidiannotification.R;
import com.regstar.obsidiannotification.core.source.NoteStore;
import com.regstar.obsidiannotification.support.ErrorLog;

import android.content.Context;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * Handles repeat-series head advance: current head occurrence is persisted to local
 * history and markdown is moved forward to the next due head.
 */
public final class RepeatSeriesManager {
    private RepeatSeriesManager() {
    }

    public static TaskEditResult complete(Context context, String taskKey) {
        return advance(context, taskKey, OccurrenceStatus.COMPLETED);
    }

    public static TaskEditResult skip(Context context, String taskKey) {
        return advance(context, taskKey, OccurrenceStatus.SKIPPED);
    }

    public static TaskEditResult advance(
            Context context,
            String taskKey,
            OccurrenceStatus resolutionStatus
    ) {
        return advance(
                context,
                taskKey,
                resolutionStatus,
                TaskFormatSettings.load(context)
        );
    }

    public static TaskEditResult advance(
            Context context,
            String taskKey,
            OccurrenceStatus resolutionStatus,
            TaskFormatSettings formatSettings
    ) {
        if (taskKey == null || taskKey.trim().isEmpty()) {
            return TaskEditResult.notFound(context.getString(R.string.runtime_task_key_empty));
        }

        try {
            NoteStore.TaskDocumentMatch match = NoteStore.findTaskDocument(context, taskKey);
            if (match == null || match.getTask() == null) {
                return TaskEditResult.notFound(context.getString(R.string.runtime_task_not_found_or_changed));
            }

            ObsidianTask task = match.getTask();
            if (!task.hasRepeatSchedule() || task.getReminderAt() == null) {
                return TaskEditResult.notFound(context.getString(R.string.runtime_repeat_series_not_found));
            }

            NoteStore.TaskBlockSnapshot snapshot = NoteStore.captureTaskBlockSnapshot(context, taskKey);
            if (snapshot == null) {
                return TaskEditResult.conflict(context.getString(R.string.runtime_task_block_not_found));
            }

            LocalDateTime nextDue = task.getRepeatRule().nextDueAfter(task.getReminderAt());
            if (nextDue == null) {
                return TaskEditResult.conflict(context.getString(R.string.runtime_next_due_calc_error));
            }

            String seriesId = task.hasStableSeriesId() ? task.getSeriesId() : ObsidianTask.newSeriesId();
            String updatedBlock = advanceBlock(task, snapshot.getDeletedBlock(), nextDue, seriesId, formatSettings);
            TaskEditResult replaceResult = NoteStore.replaceTaskBlock(context, taskKey, updatedBlock);
            if (replaceResult.isFailure()) {
                return replaceResult;
            }

            OccurrenceHistoryStore.append(
                    context,
                    new TaskOccurrenceRecord(
                            seriesId,
                            task.getReminderAt(),
                            resolutionStatus,
                            LocalDateTime.now(),
                            task.getSourceName(),
                            task.getTitle(),
                            task.getGroup(),
                            task.getTags(),
                            task.getPriority(),
                            task.getLineNumber()
                    )
            );
            OccurrenceHistoryStore.removePendingExternalCompletion(context, seriesId);
            return replaceResult;
        } catch (IOException | RuntimeException exception) {
            ErrorLog.record(context, context.getString(R.string.runtime_repeat_series_advance_error), exception);
            return TaskEditResult.writeFailed(exception.getMessage());
        }
    }

    static String advanceBlock(
            ObsidianTask task,
            String rawBlock,
            LocalDateTime nextDue,
            String seriesId,
            TaskFormatSettings formatSettings
    ) {
        String[] lines = rawBlock == null ? new String[0] : rawBlock.split("\\R", -1);
        if (lines.length == 0) {
            return TaskMarkdownWriter.rewriteSeriesHeadLine(
                    task,
                    nextDue,
                    formatSettings == null ? TaskFormatSettings.defaults() : formatSettings,
                    seriesId
            );
        }

        lines[0] = TaskMarkdownWriter.rewriteSeriesHeadLine(
                task,
                nextDue,
                formatSettings == null ? TaskFormatSettings.defaults() : formatSettings,
                seriesId
        );
        for (int i = 1; i < lines.length; i++) {
            lines[i] = TaskMarkdownWriter.resetOccurrenceLine(lines[i]);
        }

        StringBuilder builder = new StringBuilder();
        for (String line : lines) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(line);
        }
        return builder.toString();
    }
}
