package com.regstar.obsidiannotification.debug;

import com.regstar.obsidiannotification.core.reminders.*;
import com.regstar.obsidiannotification.core.source.*;
import com.regstar.obsidiannotification.core.tasks.*;
import com.regstar.obsidiannotification.prefs.*;
import com.regstar.obsidiannotification.support.ErrorLog;

public final class DebugActionResult {
    private final boolean success;
    private final String message;

    private DebugActionResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public static DebugActionResult success(String message) {
        return new DebugActionResult(true, message);
    }

    public static DebugActionResult failure(String message) {
        return new DebugActionResult(false, message);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }
}
