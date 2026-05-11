package com.regstar.obsidiannotification.core.tasks;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.function.UnaryOperator;

/**
 * One calendar-visible row: either the current markdown-backed task or a past
 * repeat occurrence from {@link OccurrenceHistoryStore}.
 */
public final class TaskVisibleOccurrence {
    private final ObsidianTask markdownTask;
    private final TaskOccurrenceRecord historyRecord;

    private TaskVisibleOccurrence(ObsidianTask markdownTask, TaskOccurrenceRecord historyRecord) {
        this.markdownTask = markdownTask;
        this.historyRecord = historyRecord;
    }

    public static TaskVisibleOccurrence fromMarkdown(ObsidianTask task) {
        return new TaskVisibleOccurrence(task, null);
    }

    public static TaskVisibleOccurrence fromHistory(TaskOccurrenceRecord record) {
        return new TaskVisibleOccurrence(null, record);
    }

    public boolean isHistorical() {
        return historyRecord != null;
    }

    public ObsidianTask getMarkdownTask() {
        return markdownTask;
    }

    public TaskOccurrenceRecord getHistoryRecord() {
        return historyRecord;
    }

    /**
     * Stable id for UI tags: markdown {@link ObsidianTask#getTaskKey()} or a
     * deterministic history key.
     */
    public String getStableRowId() {
        if (markdownTask != null) {
            return markdownTask.getTaskKey();
        }
        return "history:"
                + historyRecord.getSeriesId()
                + ":"
                + historyRecord.getOccurrenceDueAt()
                + ":"
                + historyRecord.getOccurrenceStatus().name();
    }

    public LocalDateTime getDisplayReminderAt() {
        if (markdownTask != null) {
            return markdownTask.getReminderAt();
        }
        return historyRecord.getOccurrenceDueAt();
    }

    public String getTitle() {
        if (markdownTask != null) {
            return markdownTask.getTitle();
        }
        return historyRecord.getTaskTitleSnapshot();
    }

    public TaskGrouping.Bucket bucket(String groupingMode, UnaryOperator<String> compactSource) {
        if (markdownTask != null) {
            return TaskGrouping.bucketFor(
                    markdownTask,
                    groupingMode,
                    task -> compactSource.apply(task.getSourceName())
            );
        }
        String source = historyRecord.getSourceName() == null ? "" : historyRecord.getSourceName();
        return TaskGrouping.bucketForHistoryRecord(
                historyRecord,
                groupingMode,
                compactSource.apply(source)
        );
    }

    /**
     * Calendar-style status: resolved historical occurrences map to completed/skipped;
     * current markdown tasks use parser state and overdue rules.
     */
    public TaskStatus getCalendarStatus(LocalDateTime now, Duration overdueGrace) {
        if (markdownTask != null) {
            return markdownTask.getStatus(now, overdueGrace);
        }
        if (historyRecord.getOccurrenceStatus() == OccurrenceStatus.COMPLETED) {
            return TaskStatus.COMPLETED;
        }
        if (historyRecord.getOccurrenceStatus() == OccurrenceStatus.SKIPPED) {
            return TaskStatus.SKIPPED;
        }
        if (historyRecord.getOccurrenceStatus() == OccurrenceStatus.OVERDUE) {
            return TaskStatus.OVERDUE;
        }
        return TaskStatus.WAITING;
    }
}
