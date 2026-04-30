package com.regstar.obsidiannotification.core.stats;

/**
 * Structured insight produced by the statistics layer.
 *
 * <p>The repository returns insight facts rather than preformatted UI strings
 * so activities can localize and present them without mixing text formatting
 * into the aggregation logic.</p>
 */
public final class StatisticsInsight {
    public enum Kind {
        TOP_OVERDUE_GROUP,
        TOP_SKIPPED_SOURCE,
        BEST_COMPLETION_TAG,
        COMPLETION_RATE,
        AVERAGE_COMPLETION_TIME,
        SUBTASK_AVERAGE_PROGRESS
    }

    private final Kind kind;
    private final String label;
    private final int percentValue;
    private final long minutesValue;

    private StatisticsInsight(
            Kind kind,
            String label,
            int percentValue,
            long minutesValue
    ) {
        this.kind = kind;
        this.label = label == null ? "" : label.trim();
        this.percentValue = percentValue;
        this.minutesValue = minutesValue;
    }

    public static StatisticsInsight topOverdueGroup(String label) {
        return new StatisticsInsight(Kind.TOP_OVERDUE_GROUP, label, 0, 0L);
    }

    public static StatisticsInsight topSkippedSource(String label) {
        return new StatisticsInsight(Kind.TOP_SKIPPED_SOURCE, label, 0, 0L);
    }

    public static StatisticsInsight bestCompletionTag(String label, int percentValue) {
        return new StatisticsInsight(Kind.BEST_COMPLETION_TAG, label, percentValue, 0L);
    }

    public static StatisticsInsight completionRate(int percentValue) {
        return new StatisticsInsight(Kind.COMPLETION_RATE, "", percentValue, 0L);
    }

    public static StatisticsInsight averageCompletionTime(long minutesValue) {
        return new StatisticsInsight(Kind.AVERAGE_COMPLETION_TIME, "", 0, minutesValue);
    }

    public static StatisticsInsight subtaskAverageProgress(int percentValue) {
        return new StatisticsInsight(Kind.SUBTASK_AVERAGE_PROGRESS, "", percentValue, 0L);
    }

    public Kind getKind() {
        return kind;
    }

    public String getLabel() {
        return label;
    }

    public int getPercentValue() {
        return percentValue;
    }

    public long getMinutesValue() {
        return minutesValue;
    }
}
