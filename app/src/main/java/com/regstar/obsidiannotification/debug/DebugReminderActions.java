package com.regstar.obsidiannotification.debug;

import com.regstar.obsidiannotification.R;
import com.regstar.obsidiannotification.core.reminders.ReminderReceiver;
import com.regstar.obsidiannotification.core.reminders.ReminderScheduler;
import com.regstar.obsidiannotification.core.source.NoteChangeMonitor;
import com.regstar.obsidiannotification.core.source.NoteStore;
import com.regstar.obsidiannotification.core.tasks.ObsidianTask;
import com.regstar.obsidiannotification.core.tasks.RepeatMode;
import com.regstar.obsidiannotification.core.tasks.TaskEditResult;
import com.regstar.obsidiannotification.core.tasks.TaskParseResult;
import com.regstar.obsidiannotification.prefs.ActionPreferences;
import com.regstar.obsidiannotification.support.ErrorLog;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

public final class DebugReminderActions {
    private static final String DEBUG_URI_PREFIX = "obsidiannotification://debug/";

    private DebugReminderActions() {
    }

    public static DebugActionResult showImmediateReminder(Context context) {
        ObsidianTask task = findDebugTask(context);
        if (task == null) {
            return DebugActionResult.failure(context.getString(R.string.runtime_debug_no_active_task_for_reminder));
        }
        if (!ReminderScheduler.canPostNotifications(context)) {
            return DebugActionResult.failure(context.getString(R.string.runtime_debug_notifications_permission_missing));
        }

        ReminderScheduler.ensureNotificationChannel(context);
        int notificationId = debugNotificationIdFor(task);
        Intent intent = new Intent(context, ReminderReceiver.class)
                .setAction(ReminderScheduler.ACTION_SHOW_REMINDER)
                .setData(Uri.parse(DEBUG_URI_PREFIX + notificationId + "/" + System.currentTimeMillis()));
        intent.putExtra(ReminderScheduler.EXTRA_TASK_KEY, task.getTaskKey());
        intent.putExtra(ReminderScheduler.EXTRA_NOTIFICATION_ID, notificationId);
        intent.putExtra(ReminderScheduler.EXTRA_TITLE, task.getTitle());
        intent.putExtra(ReminderScheduler.EXTRA_LINE_NUMBER, task.getLineNumber());
        intent.putExtra(ReminderScheduler.EXTRA_TRIGGER_AT_MILLIS, System.currentTimeMillis());
        intent.putExtra(ReminderScheduler.EXTRA_REPEAT_INTERVAL_MILLIS, 0L);
        intent.putExtra(ReminderScheduler.EXTRA_REPEAT_MODE, RepeatMode.NONE.name());
        intent.putExtra(ReminderScheduler.EXTRA_GROUP, task.getGroup());
        context.sendBroadcast(intent);
        return DebugActionResult.success(context.getString(R.string.runtime_debug_reminder_sent, task.getTitle()));
    }

    public static DebugActionResult markFirstTaskDone(Context context) {
        ObsidianTask task = findDebugTask(context);
        if (task == null) {
            return DebugActionResult.failure(context.getString(R.string.runtime_debug_no_active_task_for_done));
        }

        TaskEditResult result = NoteStore.markTaskDone(context, task.getTaskKey());
        if (result.shouldStopReminder()) {
            ReminderScheduler.cancelReminder(context, task.getTaskKey());
            NoteChangeMonitor.syncNow(context, true);
            return DebugActionResult.success(context.getString(R.string.runtime_debug_task_marked_done, task.getTitle()));
        }
        return DebugActionResult.failure(context.getString(R.string.runtime_debug_mark_done_failed, result.getMessage()));
    }

    public static DebugActionResult snoozeFirstTask(Context context) {
        ObsidianTask task = findDebugTask(context);
        if (task == null) {
            return DebugActionResult.failure(context.getString(R.string.runtime_debug_no_active_task_for_snooze));
        }
        if (!ReminderScheduler.canPostNotifications(context)) {
            return DebugActionResult.failure(context.getString(R.string.runtime_debug_notifications_permission_missing));
        }

        int snoozeMinutes = ActionPreferences.getSnoozeMinutes(context);
        ReminderScheduler.scheduleSnooze(
                context,
                task.getTaskKey(),
                debugNotificationIdFor(task),
                task.getLineNumber(),
                task.getTitle(),
                Duration.ofMinutes(snoozeMinutes),
                task.getRepeatIntervalMillis(),
                task.getRepeatMode()
        );
        if (ActionPreferences.shouldRecordSnoozeCount(context)) {
            TaskEditResult result = NoteStore.incrementSnoozeCount(context, task.getTaskKey());
            if (result.isFailure()) {
                return DebugActionResult.failure(
                        context.getString(R.string.runtime_debug_snooze_count_failed, result.getMessage())
                );
            }
            NoteChangeMonitor.syncNow(context, true);
        }
        return DebugActionResult.success(
                context.getString(R.string.runtime_debug_task_snoozed, snoozeMinutes, task.getTitle())
        );
    }

    private static ObsidianTask findDebugTask(Context context) {
        try {
            NoteStore.TaskSnapshot snapshot = NoteStore.readTaskSnapshot(context);
            if (NoteChangeMonitor.isSuspiciousPartialRead(context, snapshot)) {
                return null;
            }

            TaskParseResult parseResult = snapshot.getParseResult();
            List<ObsidianTask> activeTasks = parseResult.getActiveTasks();
            if (activeTasks.isEmpty()) {
                return null;
            }
            for (ObsidianTask task : activeTasks) {
                if (task.getReminderAt() != null) {
                    return task;
                }
            }
            return activeTasks.get(0);
        } catch (IOException | RuntimeException exception) {
            ErrorLog.record(context, context.getString(R.string.runtime_debug_action_failed), exception);
            return null;
        }
    }

    private static int debugNotificationIdFor(ObsidianTask task) {
        int hash = Objects.hash("debug", task.getTaskKey());
        if (hash == Integer.MIN_VALUE) {
            hash = 0;
        }

        int id = Math.abs(hash);
        return id == 0 ? 10_001 : id;
    }
}
