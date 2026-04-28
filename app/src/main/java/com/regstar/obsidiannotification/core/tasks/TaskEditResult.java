package com.regstar.obsidiannotification.core.tasks;

import com.regstar.obsidiannotification.core.reminders.*;
import com.regstar.obsidiannotification.core.source.*;
import com.regstar.obsidiannotification.prefs.*;
import com.regstar.obsidiannotification.support.ErrorLog;

public final class TaskEditResult {
    public enum Status {
        UPDATED,
        ALREADY_DONE,
        NOT_FOUND,
        CONFLICT,
        WRITE_FAILED
    }

    private final Status status;
    private final String message;

    private TaskEditResult(Status status, String message) {
        this.status = status;
        this.message = message;
    }

    public static TaskEditResult updated(String message) {
        return new TaskEditResult(Status.UPDATED, message);
    }

    public static TaskEditResult alreadyDone(String message) {
        return new TaskEditResult(Status.ALREADY_DONE, message);
    }

    public static TaskEditResult notFound(String message) {
        return new TaskEditResult(Status.NOT_FOUND, message);
    }

    public static TaskEditResult conflict(String message) {
        return new TaskEditResult(Status.CONFLICT, message);
    }

    public static TaskEditResult writeFailed(String message) {
        return new TaskEditResult(Status.WRITE_FAILED, message);
    }

    public Status getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public boolean shouldStopReminder() {
        return status == Status.UPDATED || status == Status.ALREADY_DONE;
    }

    public boolean isUpdated() {
        return status == Status.UPDATED;
    }

    public boolean isFailure() {
        return status == Status.NOT_FOUND
                || status == Status.CONFLICT
                || status == Status.WRITE_FAILED;
    }
}
