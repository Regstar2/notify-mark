package com.regstar.obsidiannotification.core.tasks;

import com.regstar.obsidiannotification.core.reminders.*;
import com.regstar.obsidiannotification.core.source.*;
import com.regstar.obsidiannotification.prefs.*;
import com.regstar.obsidiannotification.support.ErrorLog;

import java.util.Locale;

public enum TaskPriority {
    NONE,
    LOW,
    MEDIUM,
    HIGH,
    URGENT;

    public static TaskPriority fromName(String value) {
        if (!isRecognized(value)) {
            return NONE;
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()
                || normalized.equals("none")
                || normalized.equals("normal")
                || normalized.equals("0")) {
            return NONE;
        }
        if (normalized.equals("low")
                || normalized.equals("p4")
                || normalized.equals("4")) {
            return LOW;
        }
        if (normalized.equals("medium")
                || normalized.equals("med")
                || normalized.equals("p3")
                || normalized.equals("3")) {
            return MEDIUM;
        }
        if (normalized.equals("high")
                || normalized.equals("p2")
                || normalized.equals("2")) {
            return HIGH;
        }
        return URGENT;
    }

    public static boolean isRecognized(String value) {
        if (value == null) {
            return false;
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()
                || normalized.equals("none")
                || normalized.equals("normal")
                || normalized.equals("0")) {
            return true;
        }
        if (normalized.equals("low")
                || normalized.equals("p4")
                || normalized.equals("4")) {
            return true;
        }
        if (normalized.equals("medium")
                || normalized.equals("med")
                || normalized.equals("p3")
                || normalized.equals("3")) {
            return true;
        }
        if (normalized.equals("high")
                || normalized.equals("p2")
                || normalized.equals("2")) {
            return true;
        }
        if (normalized.equals("urgent")
                || normalized.equals("critical")
                || normalized.equals("p1")
                || normalized.equals("1")
                || normalized.equals("!")) {
            return true;
        }

        return false;
    }
}
