package com.regstar.obsidiannotification.core.tasks;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * Merges parsed markdown tasks with local repeat occurrence history for calendar views
 * and tasks-tab filters that surface resolved repeat heads.
 */
public final class TaskCalendarProjection {
    private TaskCalendarProjection() {
    }

    /**
     * Builds per-day calendar rows: markdown tasks (with reminder dates) plus historical
     * repeat resolutions keyed by {@link TaskOccurrenceRecord#getOccurrenceDueAt()}.
     *
     * <p>History rows are omitted when they duplicate the same series head still present
     * in markdown (same {@code seriesId} and same due instant).</p>
     */
    public static Map<LocalDate, List<TaskVisibleOccurrence>> mergeTasksByDate(
            List<ObsidianTask> markdownTasks,
            List<TaskOccurrenceRecord> history,
            boolean hidePrivateTasks,
            String privateMarker
    ) {
        List<ObsidianTask> safeMarkdown = markdownTasks == null ? Collections.emptyList() : markdownTasks;
        List<TaskOccurrenceRecord> safeHistory = history == null ? Collections.emptyList() : history;

        Map<LocalDate, List<TaskVisibleOccurrence>> byDate = new HashMap<>();
        Set<String> markdownSeriesDueKeys = new HashSet<>();

        for (ObsidianTask task : safeMarkdown) {
            if (hidePrivateTasks && TaskGrouping.isPrivateTask(task, privateMarker)) {
                continue;
            }
            if (task.getReminderAt() == null) {
                continue;
            }
            LocalDate date = task.getReminderAt().toLocalDate();
            if (task.hasStableSeriesId() && task.getReminderAt() != null) {
                markdownSeriesDueKeys.add(seriesDueKey(task.getSeriesId(), task.getReminderAt()));
            }
            byDate.computeIfAbsent(date, ignored -> new ArrayList<>()).add(TaskVisibleOccurrence.fromMarkdown(task));
        }

        Set<String> addedHistoryKeys = new LinkedHashSet<>();
        for (TaskOccurrenceRecord record : safeHistory) {
            if (record.getOccurrenceDueAt() == null) {
                continue;
            }
            if (hidePrivateTasks && TaskGrouping.isPrivateHistoryRecord(record, privateMarker)) {
                continue;
            }
            if (record.getOccurrenceStatus() == OccurrenceStatus.REVERTED) {
                continue;
            }
            String seriesId = record.getSeriesId();
            if (seriesId != null
                    && !seriesId.trim().isEmpty()
                    && markdownSeriesDueKeys.contains(seriesDueKey(seriesId, record.getOccurrenceDueAt()))) {
                continue;
            }
            String dedupe = historyDedupeKey(record);
            if (!addedHistoryKeys.add(dedupe)) {
                continue;
            }
            LocalDate date = record.getOccurrenceDueAt().toLocalDate();
            byDate.computeIfAbsent(date, ignored -> new ArrayList<>()).add(TaskVisibleOccurrence.fromHistory(record));
        }

        Comparator<TaskVisibleOccurrence> comparator = Comparator
                .comparing((TaskVisibleOccurrence entry) -> entry.getDisplayReminderAt(),
                        Comparator.nullsLast(LocalDateTime::compareTo))
                .thenComparing(TaskVisibleOccurrence::getTitle, String.CASE_INSENSITIVE_ORDER);

        for (List<TaskVisibleOccurrence> list : byDate.values()) {
            list.sort(comparator);
        }

        return byDate;
    }

    public static List<TaskVisibleOccurrence> filterBySelectedBucket(
            List<TaskVisibleOccurrence> entries,
            String selectedBucketKey,
            String groupingMode,
            java.util.function.UnaryOperator<String> compactSource
    ) {
        if (entries == null || entries.isEmpty()) {
            return Collections.emptyList();
        }
        if (selectedBucketKey == null || selectedBucketKey.trim().isEmpty()) {
            return new ArrayList<>(entries);
        }
        List<TaskVisibleOccurrence> out = new ArrayList<>();
        for (TaskVisibleOccurrence entry : entries) {
            if (selectedBucketKey.equals(entry.bucket(groupingMode, compactSource).getKey())) {
                out.add(entry);
            }
        }
        return out;
    }

    /**
     * Appends {@link TaskVisibleOccurrence} history rows to the tasks list when the user
     * filters by skipped or completed: repeat heads that advanced in markdown no longer
     * carry {@code @skipped}/{@code [x]}, but their resolutions remain in
     * {@link OccurrenceHistoryStore}.
     */
    public static void appendTaskListHistoryForResolvedStatus(
            List<TaskVisibleOccurrence> target,
            List<TaskOccurrenceRecord> history,
            OccurrenceStatus requiredStatus,
            boolean hidePrivateTasks,
            String privateMarker,
            String selectedGroup,
            String groupingMode,
            UnaryOperator<String> compactSource
    ) {
        if (target == null || requiredStatus == null) {
            return;
        }
        if (requiredStatus != OccurrenceStatus.SKIPPED && requiredStatus != OccurrenceStatus.COMPLETED) {
            return;
        }
        LinkedHashSet<String> dedupe = new LinkedHashSet<>();
        for (TaskVisibleOccurrence row : target) {
            if (row.isHistorical()) {
                dedupe.add(historyDedupeKey(row.getHistoryRecord()));
            }
        }
        List<TaskOccurrenceRecord> safeHistory = history == null ? Collections.emptyList() : history;
        for (TaskOccurrenceRecord record : safeHistory) {
            if (record.getOccurrenceStatus() != requiredStatus) {
                continue;
            }
            if (record.getOccurrenceDueAt() == null) {
                continue;
            }
            if (hidePrivateTasks && TaskGrouping.isPrivateHistoryRecord(record, privateMarker)) {
                continue;
            }
            TaskVisibleOccurrence candidate = TaskVisibleOccurrence.fromHistory(record);
            List<TaskVisibleOccurrence> inBucket = filterBySelectedBucket(
                    Collections.singletonList(candidate),
                    selectedGroup,
                    groupingMode,
                    compactSource
            );
            if (inBucket.isEmpty()) {
                continue;
            }
            if (!dedupe.add(historyDedupeKey(record))) {
                continue;
            }
            target.add(candidate);
        }
    }

    private static String seriesDueKey(String seriesId, LocalDateTime dueAt) {
        return seriesId.trim() + "|" + dueAt.toString();
    }

    private static String historyDedupeKey(TaskOccurrenceRecord record) {
        return record.getSeriesId()
                + "|"
                + (record.getOccurrenceDueAt() == null ? "" : record.getOccurrenceDueAt().toString())
                + "|"
                + record.getOccurrenceStatus().name()
                + "|"
                + (record.getResolvedAt() == null ? "" : record.getResolvedAt().toString());
    }
}
