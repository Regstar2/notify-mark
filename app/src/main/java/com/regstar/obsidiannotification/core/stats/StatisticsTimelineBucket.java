package com.regstar.obsidiannotification.core.stats;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One time bucket on the activity timeline.
 */
public final class StatisticsTimelineBucket {
    private final LocalDateTime start;
    private final LocalDateTime endExclusive;
    private final String label;
    private final int completedCount;
    private final int skippedCount;
    private final int overdueCount;

    public StatisticsTimelineBucket(
            LocalDateTime start,
            LocalDateTime endExclusive,
            String label,
            int completedCount,
            int skippedCount,
            int overdueCount
    ) {
        this.start = start;
        this.endExclusive = endExclusive;
        this.label = label == null ? "" : label;
        this.completedCount = Math.max(0, completedCount);
        this.skippedCount = Math.max(0, skippedCount);
        this.overdueCount = Math.max(0, overdueCount);
    }

    public LocalDateTime getStart() {
        return start;
    }

    public LocalDateTime getEndExclusive() {
        return endExclusive;
    }

    public LocalDate getStartDate() {
        return start == null ? null : start.toLocalDate();
    }

    public String getLabel() {
        return label;
    }

    public int getCompletedCount() {
        return completedCount;
    }

    public int getSkippedCount() {
        return skippedCount;
    }

    public int getOverdueCount() {
        return overdueCount;
    }

    public int getTotalCount() {
        return completedCount + skippedCount + overdueCount;
    }
}
