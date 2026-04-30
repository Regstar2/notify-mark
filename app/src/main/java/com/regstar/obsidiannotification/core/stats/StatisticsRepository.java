package com.regstar.obsidiannotification.core.stats;

import android.content.Context;

import com.regstar.obsidiannotification.core.source.NoteStore;
import com.regstar.obsidiannotification.core.tasks.ObsidianTask;
import com.regstar.obsidiannotification.core.tasks.OccurrenceHistoryStore;
import com.regstar.obsidiannotification.core.tasks.OccurrenceStatus;
import com.regstar.obsidiannotification.core.tasks.TaskGrouping;
import com.regstar.obsidiannotification.core.tasks.TaskOccurrenceRecord;
import com.regstar.obsidiannotification.core.tasks.TaskStatus;
import com.regstar.obsidiannotification.prefs.ActionPreferences;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Aggregates current markdown snapshot tasks and local occurrence history into a
 * statistics report suitable for the UI.
 *
 * <p>The repository intentionally keeps snapshot and historical signals
 * separate. Current-state counts come from parsed markdown tasks. Time-based
 * analytics come from local occurrence history where that history actually
 * exists.</p>
 */
public final class StatisticsRepository {
    private static final String UNTAGGED_KEY = "__untagged__";

    private StatisticsRepository() {
    }

    public static StatisticsReport buildReport(
            Context context,
            List<ObsidianTask> allTasks,
            StatisticsFilters filters,
            LocalDateTime now
    ) {
        Duration overdueGrace = Duration.ofMinutes(ActionPreferences.getOverdueGraceMinutes(context));
        List<TaskOccurrenceRecord> history = OccurrenceHistoryStore.getAllHistory(context);
        return buildReport(
                allTasks,
                history,
                filters,
                now == null ? LocalDateTime.now() : now,
                overdueGrace
        );
    }

    static StatisticsReport buildReport(
            List<ObsidianTask> allTasks,
            List<TaskOccurrenceRecord> history,
            StatisticsFilters filters,
            LocalDateTime now,
            Duration overdueGrace
    ) {
        StatisticsFilters safeFilters = filters == null ? StatisticsFilters.defaults() : filters;
        LocalDateTime safeNow = now == null ? LocalDateTime.now() : now;
        Duration safeGrace = overdueGrace == null ? Duration.ZERO : overdueGrace;
        List<ObsidianTask> safeTasks = allTasks == null ? Collections.emptyList() : allTasks;
        List<TaskOccurrenceRecord> safeHistory = history == null ? Collections.emptyList() : history;

        List<ObsidianTask> filteredSnapshotTasks = new ArrayList<>();
        for (ObsidianTask task : safeTasks) {
            if (matchesSnapshotFilters(task, safeFilters)) {
                filteredSnapshotTasks.add(task);
            }
        }

        List<TaskOccurrenceRecord> filteredHistory = new ArrayList<>();
        for (TaskOccurrenceRecord record : safeHistory) {
            if (matchesHistoryFilters(record, safeFilters)
                    && matchesPeriod(record, safeFilters.getPeriod(), safeNow.toLocalDate())) {
                filteredHistory.add(record);
            }
        }

        StatisticsSummary summary = buildSummary(filteredSnapshotTasks, filteredHistory, safeNow, safeGrace);
        StatisticsReport.StatusBreakdown statusBreakdown = buildStatusBreakdown(filteredSnapshotTasks, safeNow, safeGrace);
        StatisticsReport.SubtaskSummary subtaskSummary = buildSubtaskSummary(
                filteredSnapshotTasks,
                safeFilters,
                safeNow,
                safeGrace
        );

        List<StatisticsTimelineBucket> timeline = buildTimeline(filteredHistory, safeFilters.getPeriod(), safeNow);
        List<StatisticsBreakdownRow> groupRows = buildBreakdownRows(
                filteredSnapshotTasks,
                filteredHistory,
                BreakdownKind.GROUP,
                safeNow,
                safeGrace
        );
        List<StatisticsBreakdownRow> fileRows = buildBreakdownRows(
                filteredSnapshotTasks,
                filteredHistory,
                BreakdownKind.FILE,
                safeNow,
                safeGrace
        );
        List<StatisticsBreakdownRow> tagRows = buildBreakdownRows(
                filteredSnapshotTasks,
                filteredHistory,
                BreakdownKind.TAG,
                safeNow,
                safeGrace
        );

        List<StatisticsInsight> insights = buildInsights(summary, groupRows, fileRows, tagRows, subtaskSummary);

        return new StatisticsReport(
                safeFilters,
                summary,
                statusBreakdown,
                subtaskSummary,
                timeline,
                groupRows,
                fileRows,
                tagRows,
                insights,
                collectAvailableGroups(safeTasks, safeHistory),
                collectAvailableTags(safeTasks, safeHistory),
                collectAvailableSources(safeTasks, safeHistory),
                filteredHistory.size()
        );
    }

    private static StatisticsSummary buildSummary(
            List<ObsidianTask> snapshotTasks,
            List<TaskOccurrenceRecord> history,
            LocalDateTime now,
            Duration overdueGrace
    ) {
        int activeNow = 0;
        int overdueNow = 0;
        int completedNow = 0;
        int skippedNow = 0;
        for (ObsidianTask task : snapshotTasks) {
            TaskStatus status = task.getStatus(now, overdueGrace);
            if (status == TaskStatus.COMPLETED) {
                completedNow++;
            } else if (status == TaskStatus.SKIPPED) {
                skippedNow++;
            } else if (status == TaskStatus.OVERDUE) {
                overdueNow++;
            } else {
                activeNow++;
            }
        }

        int historicalCompleted = 0;
        int historicalSkipped = 0;
        int historicalOverdue = 0;
        long completionMinutesSum = 0L;
        int completionMinutesCount = 0;
        for (TaskOccurrenceRecord record : history) {
            if (record.getOccurrenceStatus() == OccurrenceStatus.COMPLETED) {
                historicalCompleted++;
                if (record.getOccurrenceDueAt() != null && record.getResolvedAt() != null) {
                    long minutes = Duration.between(
                            record.getOccurrenceDueAt(),
                            record.getResolvedAt()
                    ).toMinutes();
                    if (minutes >= 0L) {
                        completionMinutesSum += minutes;
                        completionMinutesCount++;
                    }
                }
            } else if (record.getOccurrenceStatus() == OccurrenceStatus.SKIPPED) {
                historicalSkipped++;
            } else if (record.getOccurrenceStatus() == OccurrenceStatus.OVERDUE) {
                historicalOverdue++;
            }
        }

        boolean hasAverage = completionMinutesCount > 0;
        long averageMinutes = hasAverage ? completionMinutesSum / completionMinutesCount : 0L;
        return new StatisticsSummary(
                snapshotTasks.size(),
                activeNow,
                overdueNow,
                completedNow,
                skippedNow,
                historicalCompleted,
                historicalSkipped,
                historicalOverdue,
                averageMinutes,
                hasAverage
        );
    }

    private static StatisticsReport.StatusBreakdown buildStatusBreakdown(
            List<ObsidianTask> tasks,
            LocalDateTime now,
            Duration overdueGrace
    ) {
        int active = 0;
        int overdue = 0;
        int completed = 0;
        int skipped = 0;
        for (ObsidianTask task : tasks) {
            TaskStatus status = task.getStatus(now, overdueGrace);
            if (status == TaskStatus.COMPLETED) {
                completed++;
            } else if (status == TaskStatus.SKIPPED) {
                skipped++;
            } else if (status == TaskStatus.OVERDUE) {
                overdue++;
            } else {
                active++;
            }
        }
        return new StatisticsReport.StatusBreakdown(active, overdue, completed, skipped);
    }

    private static StatisticsReport.SubtaskSummary buildSubtaskSummary(
            List<ObsidianTask> tasks,
            StatisticsFilters filters,
            LocalDateTime now,
            Duration overdueGrace
    ) {
        int total = 0;
        int active = 0;
        int overdue = 0;
        int completed = 0;
        int skipped = 0;
        int parentCount = 0;
        int progressPercentSum = 0;

        for (ObsidianTask task : tasks) {
            if (task.isSubtask()) {
                total++;
                TaskStatus status = task.getStatus(now, overdueGrace);
                if (status == TaskStatus.COMPLETED) {
                    completed++;
                } else if (status == TaskStatus.SKIPPED) {
                    skipped++;
                } else if (status == TaskStatus.OVERDUE) {
                    overdue++;
                } else {
                    active++;
                }
            }

            if (!task.isSubtask() && !task.getSubtasks().isEmpty()) {
                int done = 0;
                int relevantSubtasks = 0;
                for (ObsidianTask subtask : task.getSubtasks()) {
                    if (!matchesSnapshotFilters(subtask, filters)) {
                        continue;
                    }
                    relevantSubtasks++;
                    if (subtask.getStatus(now, overdueGrace) == TaskStatus.COMPLETED) {
                        done++;
                    }
                }
                if (relevantSubtasks > 0) {
                    parentCount++;
                    progressPercentSum += (int) Math.round((done * 100d) / relevantSubtasks);
                }
            }
        }

        int averageProgress = parentCount == 0 ? 0 : progressPercentSum / parentCount;
        return new StatisticsReport.SubtaskSummary(
                total,
                active,
                overdue,
                completed,
                skipped,
                parentCount,
                averageProgress
        );
    }

    private static List<StatisticsTimelineBucket> buildTimeline(
            List<TaskOccurrenceRecord> history,
            StatisticsPeriod period,
            LocalDateTime now
    ) {
        if (history.isEmpty()) {
            return Collections.emptyList();
        }

        LocalDateTime earliest = null;
        LocalDateTime latest = null;
        for (TaskOccurrenceRecord record : history) {
            LocalDateTime resolvedAt = record.getResolvedAt();
            if (resolvedAt == null) {
                continue;
            }
            if (earliest == null || resolvedAt.isBefore(earliest)) {
                earliest = resolvedAt;
            }
            if (latest == null || resolvedAt.isAfter(latest)) {
                latest = resolvedAt;
            }
        }
        if (earliest == null || latest == null) {
            return Collections.emptyList();
        }

        StatisticsPeriod safePeriod = period == null ? StatisticsPeriod.LAST_30_DAYS : period;
        StatisticsPeriod.BucketGranularity granularity = safePeriod.bucketGranularity(
                earliest.toLocalDate(),
                latest.toLocalDate()
        );

        LocalDateTime start = alignTimelineStart(safePeriod, granularity, now.toLocalDate(), earliest);
        LocalDateTime endExclusive = alignTimelineEndExclusive(safePeriod, granularity, now, latest);

        List<BucketAccumulator> buckets = new ArrayList<>();
        LocalDateTime cursor = start;
        while (!cursor.isAfter(endExclusive.minusNanos(1L))) {
            LocalDateTime next = nextBucketStart(cursor, granularity);
            if (next == null || !next.isAfter(cursor)) {
                break;
            }
            buckets.add(new BucketAccumulator(cursor, next, formatBucketLabel(cursor, granularity)));
            cursor = next;
        }

        for (TaskOccurrenceRecord record : history) {
            LocalDateTime resolvedAt = record.getResolvedAt();
            if (resolvedAt == null || resolvedAt.isBefore(start) || !resolvedAt.isBefore(endExclusive)) {
                continue;
            }
            BucketAccumulator bucket = findBucket(buckets, resolvedAt);
            if (bucket == null) {
                continue;
            }
            if (record.getOccurrenceStatus() == OccurrenceStatus.COMPLETED) {
                bucket.completed++;
            } else if (record.getOccurrenceStatus() == OccurrenceStatus.SKIPPED) {
                bucket.skipped++;
            } else if (record.getOccurrenceStatus() == OccurrenceStatus.OVERDUE) {
                bucket.overdue++;
            }
        }

        List<StatisticsTimelineBucket> timeline = new ArrayList<>();
        for (BucketAccumulator bucket : buckets) {
            timeline.add(bucket.toTimelineBucket());
        }
        return timeline;
    }

    private static LocalDateTime alignTimelineStart(
            StatisticsPeriod period,
            StatisticsPeriod.BucketGranularity granularity,
            LocalDate today,
            LocalDateTime earliest
    ) {
        if (period == StatisticsPeriod.ALL_TIME) {
            return alignToBucketStart(earliest, granularity);
        }
        LocalDate startDate = period.startDate(today);
        LocalDateTime start = startDate == null
                ? earliest
                : LocalDateTime.of(startDate, granularity == StatisticsPeriod.BucketGranularity.HOUR
                        ? LocalTime.MIN
                        : LocalTime.MIN);
        return alignToBucketStart(start, granularity);
    }

    private static LocalDateTime alignTimelineEndExclusive(
            StatisticsPeriod period,
            StatisticsPeriod.BucketGranularity granularity,
            LocalDateTime now,
            LocalDateTime latest
    ) {
        LocalDateTime reference = period == StatisticsPeriod.ALL_TIME
                ? latest
                : (now == null ? latest : (latest.isAfter(now) ? latest : now));
        if (reference == null) {
            reference = latest;
        }
        return nextBucketStart(alignToBucketStart(reference, granularity), granularity);
    }

    private static LocalDateTime alignToBucketStart(
            LocalDateTime dateTime,
            StatisticsPeriod.BucketGranularity granularity
    ) {
        if (granularity == StatisticsPeriod.BucketGranularity.HOUR) {
            return dateTime.withMinute(0).withSecond(0).withNano(0);
        }
        if (granularity == StatisticsPeriod.BucketGranularity.DAY) {
            return dateTime.toLocalDate().atStartOfDay();
        }
        if (granularity == StatisticsPeriod.BucketGranularity.WEEK) {
            return dateTime.toLocalDate()
                    .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    .atStartOfDay();
        }
        return YearMonth.from(dateTime).atDay(1).atStartOfDay();
    }

    private static LocalDateTime nextBucketStart(
            LocalDateTime current,
            StatisticsPeriod.BucketGranularity granularity
    ) {
        if (granularity == StatisticsPeriod.BucketGranularity.HOUR) {
            return current.plusHours(1);
        }
        if (granularity == StatisticsPeriod.BucketGranularity.DAY) {
            return current.plusDays(1);
        }
        if (granularity == StatisticsPeriod.BucketGranularity.WEEK) {
            return current.plusWeeks(1);
        }
        return current.plusMonths(1);
    }

    private static String formatBucketLabel(
            LocalDateTime start,
            StatisticsPeriod.BucketGranularity granularity
    ) {
        Locale locale = Locale.getDefault();
        if (granularity == StatisticsPeriod.BucketGranularity.HOUR) {
            return String.format(locale, "%02d", start.getHour());
        }
        if (granularity == StatisticsPeriod.BucketGranularity.DAY) {
            return String.valueOf(start.getDayOfMonth());
        }
        if (granularity == StatisticsPeriod.BucketGranularity.WEEK) {
            return String.valueOf(start.getDayOfMonth());
        }
        return start.getMonth().getDisplayName(TextStyle.SHORT, locale);
    }

    private static BucketAccumulator findBucket(
            List<BucketAccumulator> buckets,
            LocalDateTime resolvedAt
    ) {
        for (BucketAccumulator bucket : buckets) {
            if ((resolvedAt.isEqual(bucket.start) || resolvedAt.isAfter(bucket.start))
                    && resolvedAt.isBefore(bucket.endExclusive)) {
                return bucket;
            }
        }
        return null;
    }

    private static List<StatisticsBreakdownRow> buildBreakdownRows(
            List<ObsidianTask> tasks,
            List<TaskOccurrenceRecord> history,
            BreakdownKind kind,
            LocalDateTime now,
            Duration overdueGrace
    ) {
        LinkedHashMap<String, BreakdownAccumulator> rows = new LinkedHashMap<>();

        for (ObsidianTask task : tasks) {
            List<BreakdownKey> keys = keysForTask(task, kind);
            TaskStatus status = task.getStatus(now, overdueGrace);
            for (BreakdownKey key : keys) {
                BreakdownAccumulator row = rows.computeIfAbsent(
                        key.key,
                        ignored -> new BreakdownAccumulator(key.key, key.label)
                );
                row.currentTotal++;
                if (status == TaskStatus.COMPLETED) {
                    row.currentCompleted++;
                } else if (status == TaskStatus.SKIPPED) {
                    row.currentSkipped++;
                } else if (status == TaskStatus.OVERDUE) {
                    row.currentOverdue++;
                } else {
                    row.currentActive++;
                }
            }
        }

        for (TaskOccurrenceRecord record : history) {
            List<BreakdownKey> keys = keysForRecord(record, kind);
            for (BreakdownKey key : keys) {
                BreakdownAccumulator row = rows.computeIfAbsent(
                        key.key,
                        ignored -> new BreakdownAccumulator(key.key, key.label)
                );
                if (record.getOccurrenceStatus() == OccurrenceStatus.COMPLETED) {
                    row.historicalCompleted++;
                } else if (record.getOccurrenceStatus() == OccurrenceStatus.SKIPPED) {
                    row.historicalSkipped++;
                } else if (record.getOccurrenceStatus() == OccurrenceStatus.OVERDUE) {
                    row.historicalOverdue++;
                }
            }
        }

        List<StatisticsBreakdownRow> result = new ArrayList<>();
        for (BreakdownAccumulator row : rows.values()) {
            result.add(row.toRow());
        }
        result.sort(
                Comparator.comparingInt(StatisticsBreakdownRow::getCurrentOverdueCount).reversed()
                        .thenComparing(Comparator.comparingInt(StatisticsBreakdownRow::getHistoricalSkippedCount).reversed())
                        .thenComparing(Comparator.comparingInt(StatisticsBreakdownRow::getCurrentActiveCount).reversed())
                        .thenComparing(Comparator.comparingInt(StatisticsBreakdownRow::getCurrentTotalCount).reversed())
                        .thenComparing(row -> row.getLabel().toLowerCase(Locale.ROOT))
        );
        return result;
    }

    private static List<StatisticsInsight> buildInsights(
            StatisticsSummary summary,
            List<StatisticsBreakdownRow> groupRows,
            List<StatisticsBreakdownRow> fileRows,
            List<StatisticsBreakdownRow> tagRows,
            StatisticsReport.SubtaskSummary subtaskSummary
    ) {
        List<StatisticsInsight> insights = new ArrayList<>();

        StatisticsBreakdownRow topOverdueGroup = firstRowWith(groupRows, row -> row.getCurrentOverdueCount() > 0);
        if (topOverdueGroup != null) {
            insights.add(StatisticsInsight.topOverdueGroup(topOverdueGroup.getLabel()));
        }

        StatisticsBreakdownRow topSkippedFile = firstRowWith(fileRows, row -> row.getHistoricalSkippedCount() > 0);
        if (topSkippedFile != null) {
            insights.add(StatisticsInsight.topSkippedSource(topSkippedFile.getLabel()));
        }

        StatisticsBreakdownRow bestTag = bestCompletionRow(tagRows);
        if (bestTag != null) {
            int percent = (int) Math.round(bestTag.getHistoricalCompletionRate() * 100d);
            insights.add(StatisticsInsight.bestCompletionTag(bestTag.getLabel(), percent));
        }

        if (summary.hasHistoricalResolutionData()) {
            int percent = (int) Math.round(summary.getHistoricalCompletionRate() * 100d);
            insights.add(StatisticsInsight.completionRate(percent));
        }

        if (summary.hasAverageCompletionTime()) {
            insights.add(StatisticsInsight.averageCompletionTime(summary.getAverageCompletionMinutes()));
        }

        if (subtaskSummary.getTotalCount() > 0) {
            insights.add(StatisticsInsight.subtaskAverageProgress(
                    subtaskSummary.getAverageParentProgressPercent()
            ));
        }

        if (insights.size() > 5) {
            return insights.subList(0, 5);
        }
        return insights;
    }

    private static StatisticsBreakdownRow firstRowWith(
            List<StatisticsBreakdownRow> rows,
            RowPredicate predicate
    ) {
        for (StatisticsBreakdownRow row : rows) {
            if (predicate.matches(row)) {
                return row;
            }
        }
        return null;
    }

    private static StatisticsBreakdownRow bestCompletionRow(List<StatisticsBreakdownRow> rows) {
        StatisticsBreakdownRow best = null;
        for (StatisticsBreakdownRow row : rows) {
            if (row.getHistoricalResolvedCount() < 2) {
                continue;
            }
            if (best == null || row.getHistoricalCompletionRate() > best.getHistoricalCompletionRate()) {
                best = row;
            }
        }
        return best;
    }

    private static List<String> collectAvailableGroups(
            List<ObsidianTask> tasks,
            List<TaskOccurrenceRecord> history
    ) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (ObsidianTask task : tasks) {
            if (!task.getGroup().trim().isEmpty()) {
                values.add(task.getGroup().trim());
            }
        }
        for (TaskOccurrenceRecord record : history) {
            if (!record.getGroupSnapshot().trim().isEmpty()) {
                values.add(record.getGroupSnapshot().trim());
            }
        }
        return sortStrings(values);
    }

    private static List<String> collectAvailableTags(
            List<ObsidianTask> tasks,
            List<TaskOccurrenceRecord> history
    ) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (ObsidianTask task : tasks) {
            values.addAll(task.getTags());
        }
        for (TaskOccurrenceRecord record : history) {
            values.addAll(record.getTagsSnapshot());
        }
        return sortStrings(values);
    }

    private static List<String> collectAvailableSources(
            List<ObsidianTask> tasks,
            List<TaskOccurrenceRecord> history
    ) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (ObsidianTask task : tasks) {
            if (!task.getSourceName().trim().isEmpty()) {
                values.add(task.getSourceName().trim());
            }
        }
        for (TaskOccurrenceRecord record : history) {
            if (!record.getSourceName().trim().isEmpty()) {
                values.add(record.getSourceName().trim());
            }
        }
        return sortStrings(values);
    }

    private static List<String> sortStrings(Set<String> values) {
        List<String> sorted = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                sorted.add(value.trim());
            }
        }
        sorted.sort(String.CASE_INSENSITIVE_ORDER);
        return sorted;
    }

    private static boolean matchesSnapshotFilters(ObsidianTask task, StatisticsFilters filters) {
        if (task == null) {
            return false;
        }
        if (!filters.getGroup().isEmpty() && !filters.getGroup().equals(task.getGroup())) {
            return false;
        }
        if (!filters.getTag().isEmpty() && !task.getTags().contains(filters.getTag())) {
            return false;
        }
        return filters.getSourceName().isEmpty() || filters.getSourceName().equals(task.getSourceName());
    }

    private static boolean matchesHistoryFilters(TaskOccurrenceRecord record, StatisticsFilters filters) {
        if (record == null) {
            return false;
        }
        if (!filters.getGroup().isEmpty() && !filters.getGroup().equals(record.getGroupSnapshot())) {
            return false;
        }
        if (!filters.getTag().isEmpty() && !record.getTagsSnapshot().contains(filters.getTag())) {
            return false;
        }
        return filters.getSourceName().isEmpty() || filters.getSourceName().equals(record.getSourceName());
    }

    private static boolean matchesPeriod(
            TaskOccurrenceRecord record,
            StatisticsPeriod period,
            LocalDate today
    ) {
        if (period == null || period == StatisticsPeriod.ALL_TIME) {
            return true;
        }
        LocalDateTime resolvedAt = record.getResolvedAt();
        if (resolvedAt == null) {
            return false;
        }
        LocalDate startDate = period.startDate(today);
        return startDate == null || !resolvedAt.toLocalDate().isBefore(startDate);
    }

    private static List<BreakdownKey> keysForTask(ObsidianTask task, BreakdownKind kind) {
        if (kind == BreakdownKind.GROUP) {
            return Collections.singletonList(new BreakdownKey(task.getGroup(), task.getGroup()));
        }
        if (kind == BreakdownKind.FILE) {
            return Collections.singletonList(new BreakdownKey(task.getSourceName(), task.getSourceName()));
        }
        if (task.getTags().isEmpty()) {
            return Collections.singletonList(new BreakdownKey(UNTAGGED_KEY, TaskGrouping.FALLBACK_TAG_LABEL));
        }
        List<BreakdownKey> keys = new ArrayList<>();
        for (String tag : task.getTags()) {
            keys.add(new BreakdownKey(tag, tag));
        }
        return keys;
    }

    private static List<BreakdownKey> keysForRecord(TaskOccurrenceRecord record, BreakdownKind kind) {
        if (kind == BreakdownKind.GROUP) {
            return Collections.singletonList(new BreakdownKey(record.getGroupSnapshot(), record.getGroupSnapshot()));
        }
        if (kind == BreakdownKind.FILE) {
            return Collections.singletonList(new BreakdownKey(record.getSourceName(), record.getSourceName()));
        }
        if (record.getTagsSnapshot().isEmpty()) {
            return Collections.singletonList(new BreakdownKey(UNTAGGED_KEY, TaskGrouping.FALLBACK_TAG_LABEL));
        }
        List<BreakdownKey> keys = new ArrayList<>();
        for (String tag : record.getTagsSnapshot()) {
            keys.add(new BreakdownKey(tag, tag));
        }
        return keys;
    }

    private interface RowPredicate {
        boolean matches(StatisticsBreakdownRow row);
    }

    private enum BreakdownKind {
        GROUP,
        FILE,
        TAG
    }

    private static final class BreakdownKey {
        private final String key;
        private final String label;

        private BreakdownKey(String key, String label) {
            this.key = key == null ? "" : key;
            this.label = label == null ? "" : label;
        }
    }

    private static final class BreakdownAccumulator {
        private final String key;
        private final String label;
        private int currentTotal;
        private int currentActive;
        private int currentOverdue;
        private int currentCompleted;
        private int currentSkipped;
        private int historicalCompleted;
        private int historicalSkipped;
        private int historicalOverdue;

        private BreakdownAccumulator(String key, String label) {
            this.key = key;
            this.label = label;
        }

        private StatisticsBreakdownRow toRow() {
            return new StatisticsBreakdownRow(
                    key,
                    label,
                    currentTotal,
                    currentActive,
                    currentOverdue,
                    currentCompleted,
                    currentSkipped,
                    historicalCompleted,
                    historicalSkipped,
                    historicalOverdue
            );
        }
    }

    private static final class BucketAccumulator {
        private final LocalDateTime start;
        private final LocalDateTime endExclusive;
        private final String label;
        private int completed;
        private int skipped;
        private int overdue;

        private BucketAccumulator(LocalDateTime start, LocalDateTime endExclusive, String label) {
            this.start = start;
            this.endExclusive = endExclusive;
            this.label = label;
        }

        private StatisticsTimelineBucket toTimelineBucket() {
            return new StatisticsTimelineBucket(start, endExclusive, label, completed, skipped, overdue);
        }
    }
}
