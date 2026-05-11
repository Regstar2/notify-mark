package com.regstar.obsidiannotification.core.tasks;

import com.regstar.obsidiannotification.core.reminders.*;
import com.regstar.obsidiannotification.core.source.*;
import com.regstar.obsidiannotification.prefs.*;
import com.regstar.obsidiannotification.support.ErrorLog;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Shared visibility + grouping helpers for task list style screens.
 *
 * <p>The key rule is that visibility and grouping are different stages:
 * visibility decides whether a task may be shown at all, and grouping only
 * decides which bucket a visible task belongs to. Missing grouping keys are
 * therefore mapped to stable fallback buckets instead of silently dropping the task.</p>
 */
public final class TaskGrouping {
    public static final String FALLBACK_GROUP_KEY = "__bucket_ungrouped__";
    public static final String FALLBACK_TAG_KEY = "__bucket_untagged__";
    public static final String FALLBACK_GROUP_LABEL = "\u0411\u0435\u0437 \u0433\u0440\u0443\u043f\u043f\u044b";
    public static final String FALLBACK_TAG_LABEL = "\u0411\u0435\u0437 \u0442\u0435\u0433\u0430";

    public interface SourceLabelResolver {
        String resolve(ObsidianTask task);
    }

    public static final class Bucket {
        private final String key;
        private final String label;

        public Bucket(String key, String label) {
            this.key = key == null ? "" : key;
            this.label = label == null ? "" : label;
        }

        public String getKey() {
            return key;
        }

        public String getLabel() {
            return label;
        }
    }

    private TaskGrouping() {
    }

    public static List<ObsidianTask> filterVisibleTasks(
            List<ObsidianTask> tasks,
            String filter,
            boolean hidePrivateTasks,
            String privateMarker,
            LocalDateTime now,
            Duration overdueGrace
    ) {
        ArrayList<ObsidianTask> visibleTasks = new ArrayList<>();
        if (tasks == null) {
            return visibleTasks;
        }
        for (ObsidianTask task : tasks) {
            if (hidePrivateTasks && isPrivateTask(task, privateMarker)) {
                continue;
            }
            if (UserPreferences.FILTER_ALL.equals(filter)) {
                visibleTasks.add(task);
                continue;
            }
            TaskStatus status = task.getStatus(now, overdueGrace);
            if (UserPreferences.FILTER_ACTIVE.equals(filter)
                    && status != TaskStatus.COMPLETED
                    && status != TaskStatus.SKIPPED) {
                visibleTasks.add(task);
            } else if (UserPreferences.FILTER_OVERDUE.equals(filter) && status == TaskStatus.OVERDUE) {
                visibleTasks.add(task);
            } else if (UserPreferences.FILTER_COMPLETED.equals(filter) && status == TaskStatus.COMPLETED) {
                visibleTasks.add(task);
            } else if (UserPreferences.FILTER_SKIPPED.equals(filter) && status == TaskStatus.SKIPPED) {
                visibleTasks.add(task);
            }
        }
        return visibleTasks;
    }

    public static List<ObsidianTask> filterCalendarContextTasks(
            List<ObsidianTask> tasks,
            boolean hidePrivateTasks,
            String privateMarker
    ) {
        ArrayList<ObsidianTask> visibleTasks = new ArrayList<>();
        if (tasks == null) {
            return visibleTasks;
        }
        for (ObsidianTask task : tasks) {
            if (hidePrivateTasks && isPrivateTask(task, privateMarker)) {
                continue;
            }
            visibleTasks.add(task);
        }
        return visibleTasks;
    }

    public static List<ObsidianTask> filterBySelectedBucket(
            List<ObsidianTask> visibleTasks,
            String selectedBucketKey,
            String groupingMode,
            SourceLabelResolver sourceLabelResolver
    ) {
        ArrayList<ObsidianTask> filteredTasks = new ArrayList<>();
        if (visibleTasks == null) {
            return filteredTasks;
        }
        if (selectedBucketKey == null || selectedBucketKey.trim().isEmpty()) {
            filteredTasks.addAll(visibleTasks);
            return filteredTasks;
        }
        for (ObsidianTask task : visibleTasks) {
            if (selectedBucketKey.equals(bucketFor(task, groupingMode, sourceLabelResolver).getKey())) {
                filteredTasks.add(task);
            }
        }
        return filteredTasks;
    }

    public static List<Bucket> collectBuckets(
            List<ObsidianTask> visibleTasks,
            String groupingMode,
            SourceLabelResolver sourceLabelResolver
    ) {
        LinkedHashMap<String, Bucket> buckets = new LinkedHashMap<>();
        if (visibleTasks == null) {
            return new ArrayList<>();
        }
        for (ObsidianTask task : visibleTasks) {
            Bucket bucket = bucketFor(task, groupingMode, sourceLabelResolver);
            buckets.putIfAbsent(bucket.getKey(), bucket);
        }
        return new ArrayList<>(buckets.values());
    }

    public static Bucket bucketFor(
            ObsidianTask task,
            String groupingMode,
            SourceLabelResolver sourceLabelResolver
    ) {
        String mode = groupingMode == null ? UserPreferences.GROUPING_SMART : groupingMode;
        if (UserPreferences.GROUPING_GROUP.equals(mode)) {
            return explicitGroupBucket(task);
        }
        if (UserPreferences.GROUPING_TAG.equals(mode)) {
            return tagBucket(task);
        }
        if (UserPreferences.GROUPING_FILE.equals(mode)) {
            return fileBucket(task, sourceLabelResolver);
        }
        return smartBucket(task, sourceLabelResolver);
    }

    public static boolean isPrivateTask(ObsidianTask task, String privateMarker) {
        String marker = normalizePrivateMarker(privateMarker);
        if (marker.isEmpty()) {
            return false;
        }
        if (marker.equals(normalizePrivateMarker(task.getGroup()))) {
            return true;
        }
        for (String tag : task.getTags()) {
            if (marker.equals(normalizePrivateMarker(tag))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Same privacy rule as {@link #isPrivateTask(ObsidianTask, String)} applied to
     * occurrence snapshot fields.
     */
    public static boolean isPrivateHistoryRecord(TaskOccurrenceRecord record, String privateMarker) {
        if (record == null) {
            return false;
        }
        String marker = normalizePrivateMarker(privateMarker);
        if (marker.isEmpty()) {
            return false;
        }
        if (marker.equals(normalizePrivateMarker(record.getGroupSnapshot()))) {
            return true;
        }
        for (String tag : record.getTagsSnapshot()) {
            if (marker.equals(normalizePrivateMarker(tag))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Grouping bucket for a historical occurrence using the same modes as
     * {@link #bucketFor(ObsidianTask, String, SourceLabelResolver)}.
     *
     * @param displaySourceLabel compact display string for the source (file), e.g. from {@code compactName(uri)}
     */
    public static Bucket bucketForHistoryRecord(
            TaskOccurrenceRecord record,
            String groupingMode,
            String displaySourceLabel
    ) {
        if (record == null) {
            return new Bucket("", "");
        }
        String mode = groupingMode == null ? UserPreferences.GROUPING_SMART : groupingMode;
        String group = normalizeGroup(record.getGroupSnapshot());
        List<String> tags = record.getTagsSnapshot();
        String sourceLabel = displaySourceLabel == null ? "" : displaySourceLabel.trim();

        if (UserPreferences.GROUPING_GROUP.equals(mode)) {
            if (group.isEmpty()) {
                return new Bucket(FALLBACK_GROUP_KEY, FALLBACK_GROUP_LABEL);
            }
            return new Bucket(group, group);
        }
        if (UserPreferences.GROUPING_TAG.equals(mode)) {
            if (tags.isEmpty()) {
                return new Bucket(FALLBACK_TAG_KEY, FALLBACK_TAG_LABEL);
            }
            String tag = normalizeTag(tags.get(0));
            if (tag.isEmpty()) {
                return new Bucket(FALLBACK_TAG_KEY, FALLBACK_TAG_LABEL);
            }
            return new Bucket(tag, tag);
        }
        if (UserPreferences.GROUPING_FILE.equals(mode)) {
            return new Bucket(sourceLabel, sourceLabel);
        }
        if (!group.isEmpty()) {
            return new Bucket(group, group);
        }
        if (!tags.isEmpty()) {
            String tag = normalizeTag(tags.get(0));
            if (!tag.isEmpty()) {
                return new Bucket(tag, tag);
            }
        }
        return new Bucket(sourceLabel, sourceLabel);
    }

    private static Bucket explicitGroupBucket(ObsidianTask task) {
        String group = normalizeGroup(task.getGroup());
        if (group.isEmpty()) {
            return new Bucket(FALLBACK_GROUP_KEY, FALLBACK_GROUP_LABEL);
        }
        return new Bucket(group, group);
    }

    private static Bucket tagBucket(ObsidianTask task) {
        if (task.getTags().isEmpty()) {
            return new Bucket(FALLBACK_TAG_KEY, FALLBACK_TAG_LABEL);
        }
        String tag = normalizeTag(task.getTags().get(0));
        if (tag.isEmpty()) {
            return new Bucket(FALLBACK_TAG_KEY, FALLBACK_TAG_LABEL);
        }
        return new Bucket(tag, tag);
    }

    private static Bucket fileBucket(ObsidianTask task, SourceLabelResolver sourceLabelResolver) {
        String label = sourceLabelResolver == null ? task.getSourceName() : sourceLabelResolver.resolve(task);
        String normalized = label == null ? "" : label.trim();
        return new Bucket(normalized, normalized);
    }

    private static Bucket smartBucket(ObsidianTask task, SourceLabelResolver sourceLabelResolver) {
        String group = normalizeGroup(task.getGroup());
        if (!group.isEmpty()) {
            return new Bucket(group, group);
        }
        if (!task.getTags().isEmpty()) {
            String tag = normalizeTag(task.getTags().get(0));
            if (!tag.isEmpty()) {
                return new Bucket(tag, tag);
            }
        }
        return fileBucket(task, sourceLabelResolver);
    }

    private static String normalizeGroup(String group) {
        if (group == null) {
            return "";
        }
        String normalized = group.trim();
        if (normalized.isEmpty() || ObsidianTask.DEFAULT_GROUP.equals(normalized)) {
            return "";
        }
        return normalized;
    }

    private static String normalizeTag(String tag) {
        return tag == null ? "" : tag.trim();
    }

    private static String normalizePrivateMarker(String value) {
        return UserPreferences.normalizePrivateMarker(value).toLowerCase(Locale.ROOT);
    }
}
