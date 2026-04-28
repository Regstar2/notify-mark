package com.regstar.obsidiannotification;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

public final class TaskGroupingTest {
    private static final LocalDate DEFAULT_DATE = LocalDate.of(2026, 4, 28);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 4, 28, 9, 0);
    private static final TaskGrouping.SourceLabelResolver SOURCE_LABEL = ObsidianTask::getSourceName;

    @Test
    public void bucketFor_groupModeWithoutGroup_usesFallbackBucket() {
        ObsidianTask task = parseSingle("- [ ] Personal @due(2026-04-28 10:00) #личное\n");

        TaskGrouping.Bucket bucket = TaskGrouping.bucketFor(
                task,
                UserPreferences.GROUPING_GROUP,
                SOURCE_LABEL
        );

        assertEquals(TaskGrouping.FALLBACK_GROUP_KEY, bucket.getKey());
        assertEquals(TaskGrouping.FALLBACK_GROUP_LABEL, bucket.getLabel());
    }

    @Test
    public void bucketFor_tagModeWithoutTags_usesFallbackBucket() {
        ObsidianTask task = parseSingle("- [ ] Home @due(2026-04-28 10:00) @group(дом)\n");

        TaskGrouping.Bucket bucket = TaskGrouping.bucketFor(
                task,
                UserPreferences.GROUPING_TAG,
                SOURCE_LABEL
        );

        assertEquals(TaskGrouping.FALLBACK_TAG_KEY, bucket.getKey());
        assertEquals(TaskGrouping.FALLBACK_TAG_LABEL, bucket.getLabel());
    }

    @Test
    public void filterVisibleTasks_keepsPrivateTaskWhenPrivacyFilterIsOff() {
        List<ObsidianTask> tasks = TaskParser.parse(
                "- [ ] Private @due(2026-04-28 10:00) @group(private)\n",
                DEFAULT_DATE
        );

        List<ObsidianTask> visibleTasks = TaskGrouping.filterVisibleTasks(
                tasks,
                UserPreferences.FILTER_ALL,
                false,
                "private",
                NOW,
                Duration.ZERO
        );

        assertEquals(1, visibleTasks.size());
    }

    @Test
    public void filterVisibleTasks_hidesPrivateTaskBeforeGroupingWhenPrivacyFilterIsOn() {
        List<ObsidianTask> tasks = TaskParser.parse(
                "- [ ] Private @due(2026-04-28 10:00) #private\n",
                DEFAULT_DATE
        );

        List<ObsidianTask> visibleTasks = TaskGrouping.filterVisibleTasks(
                tasks,
                UserPreferences.FILTER_ALL,
                true,
                "private",
                NOW,
                Duration.ZERO
        );

        assertTrue(visibleTasks.isEmpty());
    }

    @Test
    public void filterBySelectedBucket_doesNotDropVisibleTaskWithoutGroup() {
        ObsidianTask task = parseSingle("- [ ] Personal @due(2026-04-28 10:00) #личное\n");

        List<ObsidianTask> groupedTasks = TaskGrouping.filterBySelectedBucket(
                Arrays.asList(task),
                TaskGrouping.FALLBACK_GROUP_KEY,
                UserPreferences.GROUPING_GROUP,
                SOURCE_LABEL
        );

        assertEquals(1, groupedTasks.size());
        assertEquals(task.getTaskKey(), groupedTasks.get(0).getTaskKey());
    }

    @Test
    public void collectBuckets_includesFallbackTagBucketForVisibleTaskWithoutTags() {
        ObsidianTask tagged = parseSingle("- [ ] Tagged @due(2026-04-28 10:00) #личное\n");
        ObsidianTask untagged = parseSingle("- [ ] Untagged @due(2026-04-28 11:00) @group(дом)\n");

        List<TaskGrouping.Bucket> buckets = TaskGrouping.collectBuckets(
                Arrays.asList(tagged, untagged),
                UserPreferences.GROUPING_TAG,
                SOURCE_LABEL
        );

        assertEquals(2, buckets.size());
        assertEquals("личное", buckets.get(0).getLabel());
        assertEquals(TaskGrouping.FALLBACK_TAG_LABEL, buckets.get(1).getLabel());
        assertFalse(buckets.get(1).getKey().isEmpty());
    }

    private ObsidianTask parseSingle(String markdown) {
        return TaskParser.parse(markdown, DEFAULT_DATE).get(0);
    }
}
