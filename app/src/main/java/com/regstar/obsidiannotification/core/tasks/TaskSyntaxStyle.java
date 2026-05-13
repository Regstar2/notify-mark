package com.regstar.obsidiannotification.core.tasks;

/**
 * Detected markdown task-line syntax family for parser, writer, and conflict handling.
 */
public enum TaskSyntaxStyle {
    /** NotifyMark {@code @due(...)}, {@code @repeat(...)}, etc. */
    NATIVE,
    /** Obsidian Tasks emoji metadata ({@code 📅}, {@code ⏰}, {@code 🔁}, …). */
    OBSIDIAN_TASKS,
    /** Both native and Obsidian markers contribute to the same line. */
    MIXED,
    /** Could not classify confidently (treated conservatively in write-back). */
    UNKNOWN
}
