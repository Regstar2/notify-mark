package com.regstar.obsidiannotification;

import android.content.Context;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Applies app-level defaults to parsed tasks without losing which values were explicit in markdown.
 */
public final class TaskDefaultsResolver {
    private TaskDefaultsResolver() {
    }

    public static List<ObsidianTask> resolve(Context context, List<ObsidianTask> tasks) {
        Duration defaultRepeatUntilDone = Duration.ofMinutes(
                ActionPreferences.getRepeatUntilDoneMinutes(context)
        );
        Duration defaultGrace = Duration.ofMinutes(ActionPreferences.getOverdueGraceMinutes(context));
        return resolve(tasks, defaultRepeatUntilDone, defaultGrace);
    }

    static List<ObsidianTask> resolve(
            List<ObsidianTask> tasks,
            Duration defaultRepeatUntilDone,
            Duration defaultGrace
    ) {
        List<ObsidianTask> resolvedTasks = new ArrayList<>();
        if (tasks == null) {
            return resolvedTasks;
        }

        Map<String, ObsidianTask> resolvedByKey = new LinkedHashMap<>();
        for (ObsidianTask task : tasks) {
            ObsidianTask resolvedTask = resolve(task, defaultRepeatUntilDone, defaultGrace);
            if (resolvedTask == null) {
                continue;
            }
            resolvedTasks.add(resolvedTask);
            resolvedByKey.put(resolvedTask.getTaskKey(), resolvedTask);
        }

        for (ObsidianTask originalTask : tasks) {
            ObsidianTask resolvedParent = resolvedByKey.get(originalTask.getTaskKey());
            if (resolvedParent == null) {
                continue;
            }
            for (ObsidianTask originalSubtask : originalTask.getSubtasks()) {
                ObsidianTask resolvedSubtask = resolvedByKey.get(originalSubtask.getTaskKey());
                if (resolvedSubtask != null) {
                    resolvedParent.addSubtask(resolvedSubtask);
                }
            }
        }
        return resolvedTasks;
    }

    public static ObsidianTask resolve(
            ObsidianTask task,
            Duration defaultRepeatUntilDone,
            Duration defaultGrace
    ) {
        if (task == null) {
            return null;
        }

        Duration resolvedRepeatUntilDone = task.getExplicitRepeatUntilDoneInterval();
        if (resolvedRepeatUntilDone == null
                && task.getReminderAt() != null
                && defaultRepeatUntilDone != null
                && !defaultRepeatUntilDone.isNegative()
                && !defaultRepeatUntilDone.isZero()) {
            resolvedRepeatUntilDone = defaultRepeatUntilDone;
        }

        Duration resolvedGrace = task.getExplicitOverdueGracePeriod();
        if (resolvedGrace == null && defaultGrace != null && !defaultGrace.isNegative()) {
            resolvedGrace = defaultGrace;
        }

        return task.withResolvedDefaults(resolvedRepeatUntilDone, resolvedGrace);
    }
}
