package com.regstar.obsidiannotification.core.tasks;

import com.regstar.obsidiannotification.core.reminders.*;
import com.regstar.obsidiannotification.core.source.*;
import com.regstar.obsidiannotification.prefs.*;
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
        if (taskKey == null || taskKey.trim().isEmpty()) {
            return TaskEditResult.notFound("РєР»СЋС‡ Р·Р°РґР°С‡Рё РїСѓСЃС‚РѕР№");
        }

        try {
            NoteStore.TaskDocumentMatch match = NoteStore.findTaskDocument(context, taskKey);
            if (match == null || match.getTask() == null) {
                return TaskEditResult.notFound("Р·Р°РґР°С‡Р° РЅРµ РЅР°Р№РґРµРЅР° РёР»Рё СѓР¶Рµ РёР·РјРµРЅРёР»Р°СЃСЊ");
            }

            ObsidianTask task = match.getTask();
            if (!task.hasRepeatSchedule() || task.getReminderAt() == null) {
                return TaskEditResult.notFound("repeat-СЃРµСЂРёСЏ РґР»СЏ РїСЂРѕРґРІРёР¶РµРЅРёСЏ РЅРµ РЅР°Р№РґРµРЅР°");
            }

            NoteStore.TaskBlockSnapshot snapshot = NoteStore.captureTaskBlockSnapshot(context, taskKey);
            if (snapshot == null) {
                return TaskEditResult.conflict("Р±Р»РѕРє Р·Р°РґР°С‡Рё Р±РѕР»СЊС€Рµ РЅРµ РЅР°Р№РґРµРЅ");
            }

            LocalDateTime nextDue = task.getRepeatRule().nextDueAfter(task.getReminderAt());
            if (nextDue == null) {
                return TaskEditResult.conflict("РЅРµ СѓРґР°Р»РѕСЃСЊ РІС‹С‡РёСЃР»РёС‚СЊ СЃР»РµРґСѓСЋС‰РёР№ due");
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
            ErrorLog.record(context, "РќРµ СѓРґР°Р»РѕСЃСЊ РїСЂРѕРґРІРёРЅСѓС‚СЊ repeat-СЃРµСЂРёСЋ", exception);
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
