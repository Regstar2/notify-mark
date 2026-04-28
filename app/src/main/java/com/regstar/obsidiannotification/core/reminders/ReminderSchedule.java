package com.regstar.obsidiannotification.core.reminders;

import com.regstar.obsidiannotification.core.source.*;
import com.regstar.obsidiannotification.core.tasks.*;
import com.regstar.obsidiannotification.prefs.*;
import com.regstar.obsidiannotification.support.ErrorLog;

public final class ReminderSchedule {
    private final boolean notificationsAllowed;
    private final int scheduledCount;
    private final ScheduledReminder nextReminder;

    public ReminderSchedule(
            boolean notificationsAllowed,
            int scheduledCount,
            ScheduledReminder nextReminder
    ) {
        this.notificationsAllowed = notificationsAllowed;
        this.scheduledCount = scheduledCount;
        this.nextReminder = nextReminder;
    }

    public boolean isNotificationsAllowed() {
        return notificationsAllowed;
    }

    public int getScheduledCount() {
        return scheduledCount;
    }

    public ScheduledReminder getNextReminder() {
        return nextReminder;
    }
}
