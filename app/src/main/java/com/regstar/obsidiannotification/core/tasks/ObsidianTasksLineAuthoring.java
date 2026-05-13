package com.regstar.obsidiannotification.core.tasks;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Builds Obsidian Tasks emoji fragments for new/edited markdown lines when compatibility mode
 * is not {@link TaskFormatCompatibilityMode#NATIVE}.
 */
public final class ObsidianTasksLineAuthoring {
    private static final String E_DUE = "\uD83D\uDCC5";
    private static final String E_REMINDER = "\u23F0";
    private static final String E_REPEAT = "\uD83D\uDD01";

    private static final Pattern ISO_DATE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
    private static final DateTimeFormatter ISO_LOCAL =
            DateTimeFormatter.ISO_LOCAL_DATE.withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter FLEX_TIME =
            DateTimeFormatter.ofPattern("H:mm").withResolverStyle(ResolverStyle.STRICT);

    private ObsidianTasksLineAuthoring() {
    }

    /**
     * @return true if emoji due/reminder was appended; false to fall back to native {@code @due(...)}.
     */
    public static boolean tryAppendDueAndReminder(
            StringBuilder builder,
            String dueValue,
            TaskFormatSettings settings
    ) {
        if (builder == null || settings == null) {
            return false;
        }
        if (dueValue == null || dueValue.trim().isEmpty()) {
            return false;
        }
        if (settings.getCompatibilityMode() == TaskFormatCompatibilityMode.NATIVE) {
            return false;
        }
        String trimmed = dueValue.trim();
        String datePart;
        String timePart;
        int sp = trimmed.indexOf(' ');
        if (sp > 0) {
            datePart = trimmed.substring(0, sp).trim();
            timePart = trimmed.substring(sp + 1).trim();
        } else if (ISO_DATE.matcher(trimmed).matches()) {
            datePart = trimmed;
            timePart = "";
        } else {
            return false;
        }
        if (!ISO_DATE.matcher(datePart).matches()) {
            return false;
        }
        try {
            LocalDate.parse(datePart, ISO_LOCAL);
        } catch (DateTimeParseException e) {
            return false;
        }
        if (!timePart.isEmpty() && !looksLikeTime(timePart)) {
            return false;
        }
        int mark = builder.length();
        builder.append(' ').append(E_DUE).append(' ').append(datePart);
        if (!timePart.isEmpty()) {
            String norm = normalizeTimeForObsidian(timePart);
            if (norm == null) {
                builder.setLength(mark);
                return false;
            }
            builder.append(' ').append(E_REMINDER).append(' ').append(datePart).append(' ').append(norm);
        }
        return true;
    }

    private static boolean looksLikeTime(String t) {
        return t.matches("\\d{1,2}:\\d{2}");
    }

    private static String normalizeTimeForObsidian(String timePart) {
        try {
            LocalTime t = LocalTime.parse(timePart.trim(), FLEX_TIME);
            return t.format(FLEX_TIME);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * @return true if a {@code 🔁} segment was appended; false to use native {@code @repeat(...)}.
     */
    public static boolean tryAppendRepeat(StringBuilder builder, String repeatUiValue) {
        if (builder == null) {
            return false;
        }
        if (repeatUiValue == null || repeatUiValue.trim().isEmpty()) {
            return false;
        }
        String phrase = repeatUiToObsidianPhrase(repeatUiValue.trim());
        if (phrase == null || phrase.isEmpty()) {
            return false;
        }
        builder.append(' ').append(E_REPEAT).append(' ').append(phrase);
        return true;
    }

    /**
     * Maps a UI repeat string (as produced by {@link RepeatRule#formatForUi()}) to an Obsidian Tasks
     * English recurrence phrase, or null when no safe single-phrase representation exists.
     */
    public static String repeatUiToObsidianPhrase(String repeatUiValue) {
        RepeatRule rule = RepeatRule.parseStoredSpec(repeatUiValue);
        if (rule == null) {
            return null;
        }
        if (rule.getUnit() == RepeatRule.Unit.MONTHS) {
            if (rule.hasMonthDaySelector()) {
                return null;
            }
            return rule.getAmount() == 1 ? "every month" : null;
        }
        if (rule.getUnit() == RepeatRule.Unit.DAYS) {
            int a = rule.getAmount();
            if (a == 1) {
                return "every day";
            }
            return "every " + a + " days";
        }
        if (rule.getUnit() == RepeatRule.Unit.WEEKS) {
            if (rule.hasDaySelector()) {
                Set<DayOfWeek> days = rule.getDaysOfWeek();
                if (days.equals(weekdays())) {
                    return "every weekday";
                }
                if (rule.getAmount() == 1 && days.size() == 1) {
                    DayOfWeek d = days.iterator().next();
                    return "every week on " + englishDayName(d);
                }
                return null;
            }
            int a = rule.getAmount();
            if (a == 1) {
                return "every week";
            }
            return "every " + a + " weeks";
        }
        return null;
    }

    private static EnumSet<DayOfWeek> weekdays() {
        return EnumSet.of(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY
        );
    }

    private static String englishDayName(DayOfWeek d) {
        return d.getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH);
    }

    public static String priorityEmoji(TaskPriority priority) {
        if (priority == null || priority == TaskPriority.NONE) {
            return "";
        }
        switch (priority) {
            case URGENT:
                return "\uD83D\uDD3A";
            case HIGH:
                return "\u23EB";
            case MEDIUM:
                return "\uD83D\uDD3C";
            case LOW:
                return "\uD83D\uDD3D";
            default:
                return "";
        }
    }
}
