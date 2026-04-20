package com.regstar.obsidiannotification;

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
                ? "Строка " + lineNumber
                : sourceName + ", строка " + lineNumber;
        return prefix + ": " + message;
    }
}
