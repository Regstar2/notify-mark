package com.regstar.obsidiannotification.core.stats;

/**
 * Combined snapshot + historical metrics for one breakdown dimension row.
 */
public final class StatisticsBreakdownRow {
    private final String key;
    private final String label;
    private final int currentTotalCount;
    private final int currentActiveCount;
    private final int currentOverdueCount;
    private final int currentCompletedCount;
    private final int currentSkippedCount;
    private final int historicalCompletedCount;
    private final int historicalSkippedCount;
    private final int historicalOverdueCount;

    public StatisticsBreakdownRow(
            String key,
            String label,
            int currentTotalCount,
            int currentActiveCount,
            int currentOverdueCount,
            int currentCompletedCount,
            int currentSkippedCount,
            int historicalCompletedCount,
            int historicalSkippedCount,
            int historicalOverdueCount
    ) {
        this.key = key == null ? "" : key;
        this.label = label == null ? "" : label;
        this.currentTotalCount = Math.max(0, currentTotalCount);
        this.currentActiveCount = Math.max(0, currentActiveCount);
        this.currentOverdueCount = Math.max(0, currentOverdueCount);
        this.currentCompletedCount = Math.max(0, currentCompletedCount);
        this.currentSkippedCount = Math.max(0, currentSkippedCount);
        this.historicalCompletedCount = Math.max(0, historicalCompletedCount);
        this.historicalSkippedCount = Math.max(0, historicalSkippedCount);
        this.historicalOverdueCount = Math.max(0, historicalOverdueCount);
    }

    public String getKey() {
        return key;
    }

    public String getLabel() {
        return label;
    }

    public int getCurrentTotalCount() {
        return currentTotalCount;
    }

    public int getCurrentActiveCount() {
        return currentActiveCount;
    }

    public int getCurrentOverdueCount() {
        return currentOverdueCount;
    }

    public int getCurrentCompletedCount() {
        return currentCompletedCount;
    }

    public int getCurrentSkippedCount() {
        return currentSkippedCount;
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

    public int getHistoricalResolvedCount() {
        return historicalCompletedCount + historicalSkippedCount + historicalOverdueCount;
    }

    public boolean hasHistoricalResolutionData() {
        return getHistoricalResolvedCount() > 0;
    }

    public double getHistoricalCompletionRate() {
        int resolved = getHistoricalResolvedCount();
        if (resolved <= 0) {
            return 0d;
        }
        return (double) historicalCompletedCount / (double) resolved;
    }
}
