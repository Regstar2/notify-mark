package com.regstar.obsidiannotification;

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
        if (taskKey == null || taskKey.trim().isEmpty()) {
            return TaskEditResult.notFound("ключ задачи пустой");
        }

        try {
            NoteStore.TaskDocumentMatch match = NoteStore.findTaskDocument(context, taskKey);
            if (match == null || match.getTask() == null) {
                return TaskEditResult.notFound("задача не найдена или уже изменилась");
            }

            ObsidianTask task = match.getTask();
            if (!task.hasRepeatSchedule() || task.getReminderAt() == null) {
                return TaskEditResult.notFound("repeat-серия для продвижения не найдена");
            }

            NoteStore.TaskBlockSnapshot snapshot = NoteStore.captureTaskBlockSnapshot(context, taskKey);
            if (snapshot == null) {
                return TaskEditResult.conflict("блок задачи больше не найден");
            }

            LocalDateTime nextDue = task.getRepeatRule().nextDueAfter(task.getReminderAt());
            if (nextDue == null) {
                return TaskEditResult.conflict("не удалось вычислить следующий due");
            }

            String seriesId = task.hasStableSeriesId() ? task.getSeriesId() : ObsidianTask.newSeriesId();
            String updatedBlock = advanceBlock(task, snapshot.getDeletedBlock(), nextDue, seriesId);
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
            ErrorLog.record(context, "Не удалось продвинуть repeat-серию", exception);
            return TaskEditResult.writeFailed(exception.getMessage());
        }
    }

    private static String advanceBlock(
            ObsidianTask task,
            String rawBlock,
            LocalDateTime nextDue,
            String seriesId
    ) {
        String[] lines = rawBlock == null ? new String[0] : rawBlock.split("\\R", -1);
        if (lines.length == 0) {
            return TaskMarkdownWriter.rewriteSeriesHeadLine(
                    task,
                    nextDue,
                    TaskFormatSettings.defaults(),
                    seriesId
            );
        }

        lines[0] = TaskMarkdownWriter.rewriteSeriesHeadLine(
                task,
                nextDue,
                TaskFormatSettings.defaults(),
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
