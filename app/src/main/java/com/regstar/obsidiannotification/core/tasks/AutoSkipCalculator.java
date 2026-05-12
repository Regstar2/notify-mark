package com.regstar.obsidiannotification.core.tasks;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Computes when an unresolved task becomes eligible for global auto-skip:
 * {@code due + resolvedGrace + configuredDelay}.
 *
 * <p>Grace matches {@link TaskStatus#forTask}: explicit task grace, else caller-supplied
 * default from preferences.</p>
 */
public final class AutoSkipCalculator {
    private AutoSkipCalculator() {
    }

    /**
     * @param defaultGraceMinutes fallback when task has no resolved overdue grace (same role as list UI)
     */
    public static LocalDateTime computeAutoSkipAt(
            ObsidianTask task,
            Duration autoSkipDelay,
            Duration defaultGraceFromPreferences
    ) {
        Objects.requireNonNull(autoSkipDelay, "autoSkipDelay");
        LocalDateTime due = task.getReminderAt();
        if (due == null) {
            return null;
        }
        Duration grace = task.getOverdueGracePeriod() != null
                ? task.getOverdueGracePeriod()
                : (defaultGraceFromPreferences == null ? Duration.ZERO : defaultGraceFromPreferences);
        return due.plus(grace).plus(autoSkipDelay);
    }

    public static boolean isConfigured(LocalDateTime autoSkipAt) {
        return autoSkipAt != null;
    }
}
