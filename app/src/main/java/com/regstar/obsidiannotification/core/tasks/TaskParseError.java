package com.regstar.obsidiannotification.core.tasks;

import com.regstar.obsidiannotification.core.reminders.*;
import com.regstar.obsidiannotification.core.source.*;
import com.regstar.obsidiannotification.prefs.*;
import com.regstar.obsidiannotification.R;

import android.content.Context;

public final class TaskParseError {
    public enum Kind {
        DUE_FUNCTION_INVALID(R.string.task_parse_error_due_function_invalid),
        DUE_TIME_INVALID(R.string.task_parse_error_due_time_invalid),
        REPEAT_RULE_INVALID(R.string.task_parse_error_repeat_rule_invalid),
        PRIORITY_INVALID(R.string.task_parse_error_priority_invalid),
        GRACE_INVALID(R.string.task_parse_error_grace_invalid),
        SNOOZE_INVALID(R.string.task_parse_error_snooze_invalid),
        OBSIDIAN_METADATA_CONFLICT(R.string.task_parse_error_obsidian_conflict),
        OBSIDIAN_CUSTOM_CHECKBOX(R.string.task_parse_error_obsidian_custom_checkbox),
        OBSIDIAN_INVALID_DATE_OR_TIME(R.string.task_parse_error_obsidian_invalid_date_time),
        OBSIDIAN_RECURRENCE_UNSUPPORTED(R.string.task_parse_error_obsidian_recurrence_unsupported);

        private final int messageResId;

        Kind(int messageResId) {
            this.messageResId = messageResId;
        }
    }

    private final String sourceName;
    private final int lineNumber;
    private final Kind kind;
    private final Object[] messageArgs;

    public TaskParseError(
            String sourceName,
            int lineNumber,
            Kind kind,
            Object... messageArgs
    ) {
        this.sourceName = sourceName;
        this.lineNumber = lineNumber;
        this.kind = kind;
        this.messageArgs = messageArgs == null ? new Object[0] : messageArgs;
    }

    public String getSourceName() {
        return sourceName;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public Kind getKind() {
        return kind;
    }

    public Object[] getMessageArgs() {
        return messageArgs;
    }

    public String format(Context context) {
        if (context == null) {
            return "";
        }

        String prefix = sourceName == null || sourceName.isEmpty()
                ? context.getString(R.string.task_parse_error_prefix_line_only, lineNumber)
                : context.getString(R.string.task_parse_error_prefix_line_with_source, sourceName, lineNumber);

        String message = kind == null
                ? ""
                : context.getString(kind.messageResId, messageArgs);

        return prefix + ": " + message;
    }
}
