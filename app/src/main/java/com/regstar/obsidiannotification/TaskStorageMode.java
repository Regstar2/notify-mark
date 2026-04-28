package com.regstar.obsidiannotification;

/**
 * Physical storage mode for markdown-backed tasks.
 *
 * <p>The task engine remains the same in every mode. Only the location of the
 * markdown files changes.</p>
 */
public enum TaskStorageMode {
    INTERNAL_MARKDOWN_STORAGE,
    EXTERNAL_MARKDOWN_STORAGE;

    public static TaskStorageMode fromName(String rawValue) {
        if (rawValue == null) {
            return null;
        }
        for (TaskStorageMode mode : values()) {
            if (mode.name().equalsIgnoreCase(rawValue.trim())) {
                return mode;
            }
        }
        return null;
    }
}
