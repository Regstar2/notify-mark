package com.regstar.obsidiannotification.core.stats;

/**
 * Immutable statistics filter state.
 *
 * <p>Filters intentionally target dimensions that are already stable in both
 * snapshot tasks and occurrence history: group, tag, and source name. Status
 * toggles are left for a later pass because historical status coverage is not
 * yet symmetrical for every task type.</p>
 */
public final class StatisticsFilters {
    private final StatisticsPeriod period;
    private final String group;
    private final String tag;
    private final String sourceName;

    public StatisticsFilters(
            StatisticsPeriod period,
            String group,
            String tag,
            String sourceName
    ) {
        this.period = period == null ? StatisticsPeriod.LAST_30_DAYS : period;
        this.group = normalize(group);
        this.tag = normalize(tag);
        this.sourceName = normalize(sourceName);
    }

    public static StatisticsFilters defaults() {
        return new StatisticsFilters(StatisticsPeriod.LAST_30_DAYS, "", "", "");
    }

    public StatisticsPeriod getPeriod() {
        return period;
    }

    public String getGroup() {
        return group;
    }

    public String getTag() {
        return tag;
    }

    public String getSourceName() {
        return sourceName;
    }

    public StatisticsFilters withPeriod(StatisticsPeriod newPeriod) {
        return new StatisticsFilters(newPeriod, group, tag, sourceName);
    }

    public StatisticsFilters withGroup(String newGroup) {
        return new StatisticsFilters(period, newGroup, tag, sourceName);
    }

    public StatisticsFilters withTag(String newTag) {
        return new StatisticsFilters(period, group, newTag, sourceName);
    }

    public StatisticsFilters withSourceName(String newSourceName) {
        return new StatisticsFilters(period, group, tag, newSourceName);
    }

    public StatisticsFilters clearDimensions() {
        return new StatisticsFilters(period, "", "", "");
    }

    public boolean hasDimensionFilters() {
        return !group.isEmpty() || !tag.isEmpty() || !sourceName.isEmpty();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
