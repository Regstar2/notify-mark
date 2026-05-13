package com.regstar.obsidiannotification.core.tasks;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Parsed Obsidian Tasks-style inline metadata for a single markdown task line.
 *
 * <p>Kept separate from {@link ObsidianTask} so the core task model stays small while still
 * carrying enough information for write-back, warnings, and syntax detection.</p>
 */
public final class TaskLineMetadata {
    public static final TaskLineMetadata EMPTY = new Builder().build();

    private final TaskSyntaxStyle syntaxStyle;
    private final Character originalCheckboxChar;
    private final boolean customCheckboxStatus;
    private final LocalDate createdDate;
    private final LocalDate startDate;
    private final LocalDate scheduledDate;
    private final LocalDate dueDate;
    private final LocalDateTime obsidianReminderAt;
    private final LocalDate doneDate;
    private final LocalDate cancelledDate;
    private final String recurrenceRawText;
    private final RepeatRule mappedRepeatRule;
    private final boolean recurrenceWhenDone;
    private final boolean recurrenceUnsupported;
    private final String taskId;
    private final List<String> dependencyIds;
    private final boolean nativeExplicitDue;
    private final boolean obsidianMetadataConflict;
    private final boolean priorityFromEmoji;

    private TaskLineMetadata(Builder b) {
        this.syntaxStyle = b.syntaxStyle == null ? TaskSyntaxStyle.UNKNOWN : b.syntaxStyle;
        this.originalCheckboxChar = b.originalCheckboxChar;
        this.customCheckboxStatus = b.customCheckboxStatus;
        this.createdDate = b.createdDate;
        this.startDate = b.startDate;
        this.scheduledDate = b.scheduledDate;
        this.dueDate = b.dueDate;
        this.obsidianReminderAt = b.obsidianReminderAt;
        this.doneDate = b.doneDate;
        this.cancelledDate = b.cancelledDate;
        this.recurrenceRawText = b.recurrenceRawText == null ? "" : b.recurrenceRawText;
        this.mappedRepeatRule = b.mappedRepeatRule;
        this.recurrenceWhenDone = b.recurrenceWhenDone;
        this.recurrenceUnsupported = b.recurrenceUnsupported;
        this.taskId = b.taskId == null ? "" : b.taskId;
        this.dependencyIds = Collections.unmodifiableList(new ArrayList<>(b.dependencyIds));
        this.nativeExplicitDue = b.nativeExplicitDue;
        this.obsidianMetadataConflict = b.obsidianMetadataConflict;
        this.priorityFromEmoji = b.priorityFromEmoji;
    }

    public TaskSyntaxStyle getSyntaxStyle() {
        return syntaxStyle;
    }

    public Character getOriginalCheckboxChar() {
        return originalCheckboxChar;
    }

    public boolean isCustomCheckboxStatus() {
        return customCheckboxStatus;
    }

    public LocalDate getCreatedDate() {
        return createdDate;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getScheduledDate() {
        return scheduledDate;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public LocalDateTime getObsidianReminderAt() {
        return obsidianReminderAt;
    }

    public LocalDate getDoneDate() {
        return doneDate;
    }

    public LocalDate getCancelledDate() {
        return cancelledDate;
    }

    public String getRecurrenceRawText() {
        return recurrenceRawText;
    }

    public RepeatRule getMappedRepeatRule() {
        return mappedRepeatRule;
    }

    public boolean isRecurrenceWhenDone() {
        return recurrenceWhenDone;
    }

    public boolean isRecurrenceUnsupported() {
        return recurrenceUnsupported;
    }

    public String getTaskId() {
        return taskId;
    }

    public List<String> getDependencyIds() {
        return dependencyIds;
    }

    public boolean hasNativeExplicitDue() {
        return nativeExplicitDue;
    }

    public boolean hasObsidianMetadataConflict() {
        return obsidianMetadataConflict;
    }

    public boolean isPriorityFromEmoji() {
        return priorityFromEmoji;
    }

    public boolean isEmpty() {
        return this == EMPTY;
    }

    public TaskLineMetadata withSyntaxStyle(TaskSyntaxStyle style) {
        if (style == null || style == syntaxStyle) {
            return this;
        }
        Builder b = toBuilder();
        b.syntaxStyle = style;
        return b.build();
    }

    public Builder toBuilder() {
        Builder b = new Builder();
        b.syntaxStyle = syntaxStyle;
        b.originalCheckboxChar = originalCheckboxChar;
        b.customCheckboxStatus = customCheckboxStatus;
        b.createdDate = createdDate;
        b.startDate = startDate;
        b.scheduledDate = scheduledDate;
        b.dueDate = dueDate;
        b.obsidianReminderAt = obsidianReminderAt;
        b.doneDate = doneDate;
        b.cancelledDate = cancelledDate;
        b.recurrenceRawText = recurrenceRawText;
        b.mappedRepeatRule = mappedRepeatRule;
        b.recurrenceWhenDone = recurrenceWhenDone;
        b.recurrenceUnsupported = recurrenceUnsupported;
        b.taskId = taskId;
        b.dependencyIds.addAll(dependencyIds);
        b.nativeExplicitDue = nativeExplicitDue;
        b.obsidianMetadataConflict = obsidianMetadataConflict;
        b.priorityFromEmoji = priorityFromEmoji;
        return b;
    }

    public static final class Builder {
        private TaskSyntaxStyle syntaxStyle = TaskSyntaxStyle.UNKNOWN;
        private Character originalCheckboxChar;
        private boolean customCheckboxStatus;
        private LocalDate createdDate;
        private LocalDate startDate;
        private LocalDate scheduledDate;
        private LocalDate dueDate;
        private LocalDateTime obsidianReminderAt;
        private LocalDate doneDate;
        private LocalDate cancelledDate;
        private String recurrenceRawText = "";
        private RepeatRule mappedRepeatRule;
        private boolean recurrenceWhenDone;
        private boolean recurrenceUnsupported;
        private String taskId = "";
        private final List<String> dependencyIds = new ArrayList<>();
        private boolean nativeExplicitDue;
        private boolean obsidianMetadataConflict;
        private boolean priorityFromEmoji;

        public Builder syntaxStyle(TaskSyntaxStyle v) {
            this.syntaxStyle = v;
            return this;
        }

        public Builder originalCheckboxChar(Character v) {
            this.originalCheckboxChar = v;
            return this;
        }

        public Builder customCheckboxStatus(boolean v) {
            this.customCheckboxStatus = v;
            return this;
        }

        public Builder createdDate(LocalDate v) {
            this.createdDate = v;
            return this;
        }

        public Builder startDate(LocalDate v) {
            this.startDate = v;
            return this;
        }

        public Builder scheduledDate(LocalDate v) {
            this.scheduledDate = v;
            return this;
        }

        public Builder dueDate(LocalDate v) {
            this.dueDate = v;
            return this;
        }

        public Builder obsidianReminderAt(LocalDateTime v) {
            this.obsidianReminderAt = v;
            return this;
        }

        public Builder doneDate(LocalDate v) {
            this.doneDate = v;
            return this;
        }

        public Builder cancelledDate(LocalDate v) {
            this.cancelledDate = v;
            return this;
        }

        public Builder recurrenceRawText(String v) {
            this.recurrenceRawText = v == null ? "" : v;
            return this;
        }

        public Builder mappedRepeatRule(RepeatRule v) {
            this.mappedRepeatRule = v;
            return this;
        }

        public Builder recurrenceWhenDone(boolean v) {
            this.recurrenceWhenDone = v;
            return this;
        }

        public Builder recurrenceUnsupported(boolean v) {
            this.recurrenceUnsupported = v;
            return this;
        }

        public Builder taskId(String v) {
            this.taskId = v == null ? "" : v;
            return this;
        }

        public Builder addDependencyId(String id) {
            if (id != null && !id.trim().isEmpty()) {
                this.dependencyIds.add(id.trim());
            }
            return this;
        }

        public Builder nativeExplicitDue(boolean v) {
            this.nativeExplicitDue = v;
            return this;
        }

        public Builder obsidianMetadataConflict(boolean v) {
            this.obsidianMetadataConflict = v;
            return this;
        }

        public Builder priorityFromEmoji(boolean v) {
            this.priorityFromEmoji = v;
            return this;
        }

        public TaskLineMetadata build() {
            return new TaskLineMetadata(this);
        }
    }
}
