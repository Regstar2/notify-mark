package com.regstar.obsidiannotification.core.stats;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Fully aggregated statistics payload consumed by the UI.
 */
public final class StatisticsReport {
    public static final class StatusBreakdown {
        private final int activeCount;
        private final int overdueCount;
        private final int completedCount;
        private final int skippedCount;

        public StatusBreakdown(
                int activeCount,
                int overdueCount,
                int completedCount,
                int skippedCount
        ) {
            this.activeCount = Math.max(0, activeCount);
            this.overdueCount = Math.max(0, overdueCount);
            this.completedCount = Math.max(0, completedCount);
            this.skippedCount = Math.max(0, skippedCount);
        }

        public int getActiveCount() {
            return activeCount;
        }

        public int getOverdueCount() {
            return overdueCount;
        }

        public int getCompletedCount() {
            return completedCount;
        }

        public int getSkippedCount() {
            return skippedCount;
        }

        public int getTotalCount() {
            return activeCount + overdueCount + completedCount + skippedCount;
        }
    }

    public static final class SubtaskSummary {
        private final int totalCount;
        private final int activeCount;
        private final int overdueCount;
        private final int completedCount;
        private final int skippedCount;
        private final int parentTaskCount;
        private final int averageParentProgressPercent;

        public SubtaskSummary(
                int totalCount,
                int activeCount,
                int overdueCount,
                int completedCount,
                int skippedCount,
                int parentTaskCount,
                int averageParentProgressPercent
        ) {
            this.totalCount = Math.max(0, totalCount);
            this.activeCount = Math.max(0, activeCount);
            this.overdueCount = Math.max(0, overdueCount);
            this.completedCount = Math.max(0, completedCount);
            this.skippedCount = Math.max(0, skippedCount);
            this.parentTaskCount = Math.max(0, parentTaskCount);
            this.averageParentProgressPercent = Math.max(0, averageParentProgressPercent);
        }

        public int getTotalCount() {
            return totalCount;
        }

        public int getActiveCount() {
            return activeCount;
        }

        public int getOverdueCount() {
            return overdueCount;
        }

        public int getCompletedCount() {
            return completedCount;
        }

        public int getSkippedCount() {
            return skippedCount;
        }

        public int getParentTaskCount() {
            return parentTaskCount;
        }

        public int getAverageParentProgressPercent() {
            return averageParentProgressPercent;
        }
    }

    private final StatisticsFilters filters;
    private final StatisticsSummary summary;
    private final StatusBreakdown statusBreakdown;
    private final SubtaskSummary subtaskSummary;
    private final List<StatisticsTimelineBucket> timeline;
    private final List<StatisticsBreakdownRow> groupBreakdown;
    private final List<StatisticsBreakdownRow> fileBreakdown;
    private final List<StatisticsBreakdownRow> tagBreakdown;
    private final List<StatisticsInsight> insights;
    private final List<String> availableGroups;
    private final List<String> availableTags;
    private final List<String> availableSources;
    private final int historicalRecordCount;

    public StatisticsReport(
            StatisticsFilters filters,
            StatisticsSummary summary,
            StatusBreakdown statusBreakdown,
            SubtaskSummary subtaskSummary,
            List<StatisticsTimelineBucket> timeline,
            List<StatisticsBreakdownRow> groupBreakdown,
            List<StatisticsBreakdownRow> fileBreakdown,
            List<StatisticsBreakdownRow> tagBreakdown,
            List<StatisticsInsight> insights,
            List<String> availableGroups,
            List<String> availableTags,
            List<String> availableSources,
            int historicalRecordCount
    ) {
        this.filters = filters == null ? StatisticsFilters.defaults() : filters;
        this.summary = summary == null ? new StatisticsSummary(0, 0, 0, 0, 0, 0, 0, 0, 0L, false) : summary;
        this.statusBreakdown = statusBreakdown == null ? new StatusBreakdown(0, 0, 0, 0) : statusBreakdown;
        this.subtaskSummary = subtaskSummary == null ? new SubtaskSummary(0, 0, 0, 0, 0, 0, 0) : subtaskSummary;
        this.timeline = immutableCopy(timeline);
        this.groupBreakdown = immutableCopy(groupBreakdown);
        this.fileBreakdown = immutableCopy(fileBreakdown);
        this.tagBreakdown = immutableCopy(tagBreakdown);
        this.insights = immutableCopy(insights);
        this.availableGroups = immutableCopy(availableGroups);
        this.availableTags = immutableCopy(availableTags);
        this.availableSources = immutableCopy(availableSources);
        this.historicalRecordCount = Math.max(0, historicalRecordCount);
    }

    public StatisticsFilters getFilters() {
        return filters;
    }

    public StatisticsSummary getSummary() {
        return summary;
    }

    public StatusBreakdown getStatusBreakdown() {
        return statusBreakdown;
    }

    public SubtaskSummary getSubtaskSummary() {
        return subtaskSummary;
    }

    public List<StatisticsTimelineBucket> getTimeline() {
        return timeline;
    }

    public List<StatisticsBreakdownRow> getGroupBreakdown() {
        return groupBreakdown;
    }

    public List<StatisticsBreakdownRow> getFileBreakdown() {
        return fileBreakdown;
    }

    public List<StatisticsBreakdownRow> getTagBreakdown() {
        return tagBreakdown;
    }

    public List<StatisticsInsight> getInsights() {
        return insights;
    }

    public List<String> getAvailableGroups() {
        return availableGroups;
    }

    public List<String> getAvailableTags() {
        return availableTags;
    }

    public List<String> getAvailableSources() {
        return availableSources;
    }

    public int getHistoricalRecordCount() {
        return historicalRecordCount;
    }

    public boolean hasSnapshotData() {
        return summary.getSnapshotTotalCount() > 0;
    }

    public boolean hasHistoricalData() {
        return historicalRecordCount > 0;
    }

    public boolean isCompletelyEmpty() {
        return !hasSnapshotData() && !hasHistoricalData();
    }

    private static <T> List<T> immutableCopy(List<T> values) {
        return Collections.unmodifiableList(new ArrayList<>(values == null
                ? Collections.emptyList()
                : values));
    }
}
