package com.regstar.obsidiannotification.core.tasks;

import com.regstar.obsidiannotification.core.reminders.*;
import com.regstar.obsidiannotification.core.source.*;
import com.regstar.obsidiannotification.prefs.*;
import com.regstar.obsidiannotification.support.ErrorLog;

public enum RepeatMode {
    NONE,
    ALWAYS,
    UNTIL_DONE;

    public static RepeatMode fromName(String name) {
        if (name == null) {
            return NONE;
        }

        try {
            return RepeatMode.valueOf(name);
        } catch (IllegalArgumentException exception) {
            return NONE;
        }
    }
}
