package com.regstar.obsidiannotification.core.stats;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Supported reporting windows for the statistics screen.
 *
 * <p>The period controls which historical occurrence records are included in
 * timeline and breakdown analytics. Snapshot metrics such as "active now" stay
 * current-state based and are not backfilled into artificial history.</p>
 */
public enum StatisticsPeriod {
    TODAY,
    LAST_7_DAYS,
    LAST_30_DAYS,
    LAST_90_DAYS,
    LAST_YEAR,
    ALL_TIME;

    public enum BucketGranularity {
        HOUR,
        DAY,
        WEEK,
        MONTH
    }

    public LocalDate startDate(LocalDate today) {
        if (today == null) {
            return null;
        }
        if (this == TODAY) {
            return today;
        }
        if (this == LAST_7_DAYS) {
            return today.minusDays(6);
        }
        if (this == LAST_30_DAYS) {
            return today.minusDays(29);
        }
        if (this == LAST_90_DAYS) {
            return today.minusDays(89);
        }
        if (this == LAST_YEAR) {
            return today.minusDays(364);
        }
        return null;
    }

    public BucketGranularity bucketGranularity(LocalDate firstDate, LocalDate lastDate) {
        if (this == TODAY) {
            return BucketGranularity.HOUR;
        }
        if (this == LAST_7_DAYS || this == LAST_30_DAYS) {
            return BucketGranularity.DAY;
        }
        if (this == LAST_90_DAYS) {
            return BucketGranularity.WEEK;
        }
        if (this == LAST_YEAR) {
            return BucketGranularity.MONTH;
        }

        if (firstDate == null || lastDate == null) {
            return BucketGranularity.MONTH;
        }

        long days = Math.max(0L, ChronoUnit.DAYS.between(firstDate, lastDate));
        if (days <= 14L) {
            return BucketGranularity.DAY;
        }
        if (days <= 150L) {
            return BucketGranularity.WEEK;
        }
        return BucketGranularity.MONTH;
    }
}
