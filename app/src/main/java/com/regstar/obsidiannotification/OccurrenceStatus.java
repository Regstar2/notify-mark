package com.regstar.obsidiannotification;

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
