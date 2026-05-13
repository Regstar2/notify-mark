package com.regstar.obsidiannotification.core.tasks;

import java.time.DayOfWeek;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Maps a small Obsidian Tasks {@code 🔁} English phrase subset onto {@link RepeatRule},
 * or marks the recurrence as unsupported for silent approximation.
 */
final class ObsidianTasksRecurrenceMapper {
    private static final Pattern EVERY_N_DAYS =
            Pattern.compile("(?i)^every\\s+(\\d+)\\s+days?$");
    private static final Pattern EVERY_N_WEEKS =
            Pattern.compile("(?i)^every\\s+(\\d+)\\s+weeks?$");
    private static final Pattern EVERY_DAY_WHEN_DONE =
            Pattern.compile("(?i)^every\\s+day\\s+when\\s+done$");
    private static final Pattern EVERY_WEEK_WHEN_DONE =
            Pattern.compile("(?i)^every\\s+week\\s+when\\s+done$");
    private static final Pattern EVERY_WEEK_ON =
            Pattern.compile("(?i)^every\\s+week\\s+on\\s+(.+)$");

    private ObsidianTasksRecurrenceMapper() {
    }

    static RepeatRule mapOrNull(String raw, boolean[] unsupportedFlag) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return null;
        }
        String compact = value.replaceAll("\\s+", " ").trim();

        if (unsupportedFlag != null && unsupportedFlag.length > 0) {
            unsupportedFlag[0] = false;
        }

        if (compact.equalsIgnoreCase("every day")) {
            return RepeatRule.interval(1, RepeatRule.Unit.DAYS);
        }
        if (compact.equalsIgnoreCase("every week")) {
            return RepeatRule.interval(1, RepeatRule.Unit.WEEKS);
        }
        if (compact.equalsIgnoreCase("every month")) {
            return RepeatRule.interval(1, RepeatRule.Unit.MONTHS);
        }
        if (compact.equalsIgnoreCase("every year")) {
            markUnsupported(unsupportedFlag);
            return null;
        }
        if (compact.equalsIgnoreCase("every weekday")) {
            return RepeatRule.weekly(1, weekdays());
        }

        Matcher mDays = EVERY_N_DAYS.matcher(compact);
        if (mDays.find()) {
            int n = parsePositiveInt(mDays.group(1));
            return n > 0 ? RepeatRule.interval(n, RepeatRule.Unit.DAYS) : null;
        }

        Matcher mWeeks = EVERY_N_WEEKS.matcher(compact);
        if (mWeeks.find()) {
            int n = parsePositiveInt(mWeeks.group(1));
            return n > 0 ? RepeatRule.interval(n, RepeatRule.Unit.WEEKS) : null;
        }

        if (EVERY_DAY_WHEN_DONE.matcher(compact).matches()) {
            markUnsupported(unsupportedFlag);
            return null;
        }
        if (EVERY_WEEK_WHEN_DONE.matcher(compact).matches()) {
            markUnsupported(unsupportedFlag);
            return null;
        }

        Matcher weekOn = EVERY_WEEK_ON.matcher(compact);
        if (weekOn.find()) {
            DayOfWeek day = parseDayName(weekOn.group(1));
            if (day == null) {
                markUnsupported(unsupportedFlag);
                return null;
            }
            return RepeatRule.weekly(1, EnumSet.of(day));
        }

        markUnsupported(unsupportedFlag);
        return null;
    }

    static boolean isWhenDonePhrase(String raw) {
        if (raw == null) {
            return false;
        }
        String compact = raw.trim().replaceAll("\\s+", " ");
        return EVERY_DAY_WHEN_DONE.matcher(compact).matches()
                || EVERY_WEEK_WHEN_DONE.matcher(compact).matches();
    }

    private static void markUnsupported(boolean[] unsupportedFlag) {
        if (unsupportedFlag != null && unsupportedFlag.length > 0) {
            unsupportedFlag[0] = true;
        }
    }

    private static int parsePositiveInt(String raw) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static Set<DayOfWeek> weekdays() {
        return EnumSet.of(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY
        );
    }

    private static DayOfWeek parseDayName(String raw) {
        if (raw == null) {
            return null;
        }
        String token = raw.trim();
        int cut = token.indexOf(',');
        if (cut >= 0) {
            token = token.substring(0, cut).trim();
        }
        String v = token.toLowerCase(Locale.ROOT);
        if (v.startsWith("mon")) {
            return DayOfWeek.MONDAY;
        }
        if (v.startsWith("tue")) {
            return DayOfWeek.TUESDAY;
        }
        if (v.startsWith("wed")) {
            return DayOfWeek.WEDNESDAY;
        }
        if (v.startsWith("thu")) {
            return DayOfWeek.THURSDAY;
        }
        if (v.startsWith("fri")) {
            return DayOfWeek.FRIDAY;
        }
        if (v.startsWith("sat")) {
            return DayOfWeek.SATURDAY;
        }
        if (v.startsWith("sun")) {
            return DayOfWeek.SUNDAY;
        }
        return null;
    }
}
