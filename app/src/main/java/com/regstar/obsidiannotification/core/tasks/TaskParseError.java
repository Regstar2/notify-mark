package com.regstar.obsidiannotification.core.tasks;

import com.regstar.obsidiannotification.core.reminders.*;
import com.regstar.obsidiannotification.core.source.*;
import com.regstar.obsidiannotification.prefs.*;
import com.regstar.obsidiannotification.support.ErrorLog;

public final class TaskParseError {
    private final String sourceName;
    private final int lineNumber;
    private final String message;

    public TaskParseError(String sourceName, int lineNumber, String message) {
        this.sourceName = sourceName;
        this.lineNumber = lineNumber;
        this.message = message;
    }

    public String getSourceName() {
        return sourceName;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public String getMessage() {
        return message;
    }

    public String format() {
        String prefix = sourceName == null || sourceName.isEmpty()
                ? "РЎС‚СЂРѕРєР° " + lineNumber
                : sourceName + ", СЃС‚СЂРѕРєР° " + lineNumber;
        return prefix + ": " + message;
    }
}
