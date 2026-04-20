package com.regstar.obsidiannotification;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TaskParseResult {
    private final List<ObsidianTask> tasks;
    private final List<TaskParseError> errors;

    public TaskParseResult(List<ObsidianTask> tasks, List<TaskParseError> errors) {
        this.tasks = Collections.unmodifiableList(new ArrayList<>(tasks));
        this.errors = Collections.unmodifiableList(new ArrayList<>(errors));
    }

    public List<ObsidianTask> getTasks() {
        return tasks;
    }

    public List<TaskParseError> getErrors() {
        return errors;
    }

    public List<ObsidianTask> getActiveTasks() {
        List<ObsidianTask> activeTasks = new ArrayList<>();
        for (ObsidianTask task : tasks) {
            if (!task.isCompleted()) {
                activeTasks.add(task);
            }
        }
        return activeTasks;
    }

    public static TaskParseResult merge(List<TaskParseResult> results) {
        List<ObsidianTask> mergedTasks = new ArrayList<>();
        List<TaskParseError> mergedErrors = new ArrayList<>();
        for (TaskParseResult result : results) {
            mergedTasks.addAll(result.getTasks());
            mergedErrors.addAll(result.getErrors());
        }
        return new TaskParseResult(mergedTasks, mergedErrors);
    }
}
