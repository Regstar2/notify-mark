package com.regstar.obsidiannotification;

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
