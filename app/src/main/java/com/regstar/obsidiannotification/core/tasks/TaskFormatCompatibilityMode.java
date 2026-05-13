package com.regstar.obsidiannotification.core.tasks;

/**
 * User preference controlling how task lines are interpreted and how new tasks are authored.
 */
public enum TaskFormatCompatibilityMode {
    /** Only native NotifyMark metadata is active; emoji markers are not interpreted as scheduling. */
    NATIVE,
    /** Prefer Obsidian Tasks emoji metadata for reads and new-task output where supported. */
    OBSIDIAN_TASKS,
    /** Interpret both native and Obsidian metadata with explicit conflict rules. */
    AUTO_MIXED
}
