package com.regstar.obsidiannotification.core.tasks;

import com.regstar.obsidiannotification.core.reminders.*;
import com.regstar.obsidiannotification.core.source.*;
import com.regstar.obsidiannotification.prefs.*;
import com.regstar.obsidiannotification.support.ErrorLog;

public enum OccurrenceStatus {
    COMPLETED,
    SKIPPED,
    OVERDUE,
    REVERTED;

    public static OccurrenceStatus fromName(String value) {
        if (value == null) {
            return COMPLETED;
        }
        try {
            return valueOf(value);
        } catch (IllegalArgumentException exception) {
            return COMPLETED;
        }
    }
}
