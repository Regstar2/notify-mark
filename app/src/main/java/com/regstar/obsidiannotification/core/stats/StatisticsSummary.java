package com.regstar.obsidiannotification.core.stats;

/**
 * Top-level metrics shown in the statistics summary cards.
 */
public final class StatisticsSummary {
    private final int snapshotTotalCount;
    private final int activeNowCount;
    private final int overdueNowCount;
    private final int completedNowCount;
    private final int skippedNowCount;
    private final int historicalCompletedCount;
    private final int historicalSkippedCount;
    private final int historicalOverdueCount;
    private final long averageCompletionMinutes;
    private final boolean hasAverageCompletionTime;

    public StatisticsSummary(
            int snapshotTotalCount,
            int activeNowCount,
            int overdueNowCount,
            int completedNowCount,
            int skippedNowCount,
            int historicalCompletedCount,
            int historicalSkippedCount,
            int historicalOverdueCount,
            long averageCompletionMinutes,
            boolean hasAverageCompletionTime
    ) {
        this.snapshotTotalCount = Math.max(0, snapshotTotalCount);
        this.activeNowCount = Math.max(0, activeNowCount);
        this.overdueNowCount = Math.max(0, overdueNowCount);
        this.completedNowCount = Math.max(0, completedNowCount);
        this.skippedNowCount = Math.max(0, skippedNowCount);
        this.historicalCompletedCount = Math.max(0, historicalCompletedCount);
        this.historicalSkippedCount = Math.max(0, historicalSkippedCount);
        this.historicalOverdueCount = Math.max(0, historicalOverdueCount);
        this.averageCompletionMinutes = Math.max(0L, averageCompletionMinutes);
        this.hasAverageCompletionTime = hasAverageCompletionTime;
    }

    public int getSnapshotTotalCount() {
        return snapshotTotalCount;
    }

    public int getActiveNowCount() {
        return activeNowCount;
    }

    public int getOverdueNowCount() {
        return overdueNowCount;
    }

    public int getCompletedNowCount() {
        return completedNowCount;
    }

    public int getSkippedNowCount() {
        return skippedNowCount;
    }

    public int getHistoricalCompletedCount() {
        return historicalCompletedCount;
    }

    public int getHistoricalSkippedCount() {
        return historicalSkippedCount;
    }

    public int getHistoricalOverdueCount() {
        return historicalOverdueCount;
    }

    public long getAverageCompletionMinutes() {
        return averageCompletionMinutes;
    }

    public boolean hasAverageCompletionTime() {
        return hasAverageCompletionTime;
    }

    public int getHistoricalResolvedCount() {
        return historicalCompletedCount + historicalSkippedCount + historicalOverdueCount;
    }

    public boolean hasHistoricalResolutionData() {
        return getHistoricalResolvedCount() > 0;
    }

    public double getHistoricalCompletionRate() {
        int resolvedCount = getHistoricalResolvedCount();
        if (resolvedCount <= 0) {
            return 0d;
        }
        return (double) historicalCompletedCount / (double) resolvedCount;
    }
}
