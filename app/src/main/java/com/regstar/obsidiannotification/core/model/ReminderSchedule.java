package com.regstar.obsidiannotification.core.model;

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


