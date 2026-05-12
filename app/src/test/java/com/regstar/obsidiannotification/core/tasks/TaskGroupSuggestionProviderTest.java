package com.regstar.obsidiannotification.core.tasks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class TaskGroupSuggestionProviderTest {
    private static final LocalDateTime DUE = LocalDateTime.of(2026, 5, 1, 10, 0);

    @Test
    public void distinctSortedGroups_collectsFromTasks() {
        ObsidianTask a = new ObsidianTask(
                "k1", "s", 1, "t", "- [ ] x @due(2026-05-01 10:00)",
                DUE, null, RepeatMode.NONE, false,
                Collections.emptyList(), TaskPriority.NONE, "Alpha"
        );
        ObsidianTask b = new ObsidianTask(
                "k2", "s", 2, "t", "- [ ] x @due(2026-05-01 11:00)",
                DUE, null, RepeatMode.NONE, false,
                Collections.emptyList(), TaskPriority.NONE, "Beta"
        );
        List<String> groups = TaskGroupSuggestionProvider.distinctSortedGroups(Arrays.asList(a, b));
        assertEquals(Arrays.asList("Alpha", "Beta"), groups);
    }

    @Test
    public void distinctSortedGroups_ignoresEmptyAndDefault() {
        ObsidianTask a = new ObsidianTask(
                "k1", "s", 1, "t", "- [ ] x",
                DUE, null, RepeatMode.NONE, false,
                Collections.emptyList(), TaskPriority.NONE, ObsidianTask.DEFAULT_GROUP
        );
        ObsidianTask b = new ObsidianTask(
                "k2", "s", 2, "t", "- [ ] x",
                DUE, null, RepeatMode.NONE, false,
                Collections.emptyList(), TaskPriority.NONE, "Zed"
        );
        List<String> groups = TaskGroupSuggestionProvider.distinctSortedGroups(Arrays.asList(a, b));
        assertEquals(Collections.singletonList("Zed"), groups);
    }

    @Test
    public void distinctSortedGroups_dedupesCaseInsensitive_keepsReadableCasing() {
        ObsidianTask a = new ObsidianTask(
                "k1", "s", 1, "t", "- [ ] x",
                DUE, null, RepeatMode.NONE, false,
                Collections.emptyList(), TaskPriority.NONE, "Work"
        );
        ObsidianTask b = new ObsidianTask(
                "k2", "s", 2, "t", "- [ ] x",
                DUE, null, RepeatMode.NONE, false,
                Collections.emptyList(), TaskPriority.NONE, "work"
        );
        List<String> groups = TaskGroupSuggestionProvider.distinctSortedGroups(Arrays.asList(a, b));
        assertEquals(1, groups.size());
        assertEquals("Work", groups.get(0));
    }

    @Test
    public void distinctSortedGroups_includesSubtasks() {
        ObsidianTask parent = new ObsidianTask(
                "k1", "s", 1, "t", "- [ ] x",
                DUE, null, RepeatMode.NONE, false,
                Collections.emptyList(), TaskPriority.NONE, "ParentOnly"
        );
        ObsidianTask sub = new ObsidianTask(
                "k2", "s", 2, "t", "  - [ ] y",
                DUE, null, RepeatMode.NONE, false,
                Collections.emptyList(), TaskPriority.NONE, "SubGroup"
        );
        parent.addSubtask(sub);
        List<String> groups = TaskGroupSuggestionProvider.distinctSortedGroups(Collections.singletonList(parent));
        assertTrue(groups.contains("ParentOnly"));
        assertTrue(groups.contains("SubGroup"));
    }

    @Test
    public void distinctSortedGroups_nullIterable_returnsEmpty() {
        assertTrue(TaskGroupSuggestionProvider.distinctSortedGroups(null).isEmpty());
    }
}
