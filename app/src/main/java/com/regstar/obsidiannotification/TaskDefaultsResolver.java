package com.regstar.obsidiannotification;

import android.content.Context;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Applies app-level defaults to parsed tasks without losing which values were explicit in markdown.
 */
public final class TaskDefaultsResolver {
    private TaskDefaultsResolver() {
    }

    public static List<ObsidianTask> resolve(Context context, List<ObsidianTask> tasks) {
        List<ObsidianTask> resolvedTasks = new ArrayList<>();
        if (tasks == null) {
            return resolvedTasks;
        }

        Duration defaultRepeatUntilDone = Duration.ofMinutes(
                ActionPreferences.getRepeatUntilDoneMinutes(context)
        );
        Duration defaultGrace = Duration.ofMinutes(ActionPreferences.getOverdueGraceMinutes(context));
        for (ObsidianTask task : tasks) {
            resolvedTasks.add(resolve(task, defaultRepeatUntilDone, defaultGrace));
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
                && task.hasRepeatSchedule()
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
