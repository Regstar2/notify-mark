package com.regstar.obsidiannotification.core.tasks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Builds stable group-name suggestions for the task editor from parsed tasks.
 */
public final class TaskGroupSuggestionProvider {
    private TaskGroupSuggestionProvider() {
    }

    /**
     * Collects non-empty groups from tasks and nested subtasks, drops duplicates
     * case-insensitively (first casing wins), excludes the implicit default bucket label,
     * and sorts case-insensitively for a predictable dropdown order.
     */
    public static List<String> distinctSortedGroups(Iterable<ObsidianTask> tasks) {
        if (tasks == null) {
            return Collections.emptyList();
        }
        Map<String, String> uniqueByLower = new LinkedHashMap<>();
        for (ObsidianTask task : tasks) {
            collectFromTask(task, uniqueByLower);
        }
        List<String> sorted = new ArrayList<>(uniqueByLower.values());
        Collections.sort(sorted, String.CASE_INSENSITIVE_ORDER);
        return sorted;
    }

    private static void collectFromTask(ObsidianTask task, Map<String, String> uniqueByLower) {
        if (task == null) {
            return;
        }
        addGroup(task.getGroup(), uniqueByLower);
        List<ObsidianTask> subs = task.getSubtasks();
        if (subs != null) {
            for (ObsidianTask sub : subs) {
                collectFromTask(sub, uniqueByLower);
            }
        }
    }

    private static void addGroup(String group, Map<String, String> uniqueByLower) {
        if (group == null) {
            return;
        }
        String trimmed = group.trim();
        if (trimmed.isEmpty() || ObsidianTask.DEFAULT_GROUP.equals(trimmed)) {
            return;
        }
        String key = trimmed.toLowerCase(Locale.ROOT);
        uniqueByLower.putIfAbsent(key, trimmed);
    }
}
