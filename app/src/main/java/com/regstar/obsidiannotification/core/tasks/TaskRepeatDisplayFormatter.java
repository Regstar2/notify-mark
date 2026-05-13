package com.regstar.obsidiannotification.core.tasks;

import java.time.DayOfWeek;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * User-facing Russian strings for repeat information: compact lines for task cards and
 * longer text for editor/details when needed.
 */
public final class TaskRepeatDisplayFormatter {
    private static final String SEP = " \u00b7 ";

    private TaskRepeatDisplayFormatter() {
    }

    /**
     * Single line for task cards: short repeat summary and compact {@code @repeatUntilDone} text,
     * without a leading {@code 🔁} (the card row already shows {@link com.regstar.obsidiannotification.R.drawable#ic_repeat}).
     */
    public static String formatShortCardLine(ObsidianTask task) {
        if (task == null) {
            return "";
        }
        String core = formatShortRepeatCore(task);
        String rud = formatShortRepeatUntilDone(task);
        if (core.isEmpty() && rud.isEmpty()) {
            core = formatShortRepeatCoreFromLegacyInterval(task);
        }
        if (!core.isEmpty() && !rud.isEmpty()) {
            return core + SEP + rud;
        }
        if (!core.isEmpty()) {
            return core;
        }
        return rud;
    }

    /**
     * Primary repeat schedule only (no {@code @repeatUntilDone}), short form for cards.
     */
    public static String formatShortRepeatCore(ObsidianTask task) {
        if (task == null) {
            return "";
        }
        if (task.getRepeatRule() != null) {
            return formatShortMappedRule(task.getRepeatRule());
        }
        String raw = task.getLineMetadata().getRecurrenceRawText().trim();
        if (raw.isEmpty()) {
            return "";
        }
        if (task.getLineMetadata().isRecurrenceWhenDone()) {
            return "\u043f\u043e\u0441\u043b\u0435 \u0432\u044b\u043f\u043e\u043b\u043d\u0435\u043d\u0438\u044f \u043d\u0435 \u043f\u043e\u0434\u0434\u0435\u0440\u0436\u0430\u043d";
        }
        if (task.getLineMetadata().isRecurrenceUnsupported()) {
            return "\u043f\u043e\u0432\u0442\u043e\u0440 \u043d\u0435 \u043f\u043e\u0434\u0434\u0435\u0440\u0436\u0430\u043d";
        }
        return raw;
    }

    /**
     * Full primary repeat line for editor/details/debug: mapped rules in long Russian, or
     * warning prefix plus raw Obsidian recurrence text.
     */
    public static String formatFullPrimaryRepeat(ObsidianTask task) {
        if (task == null) {
            return "";
        }
        if (task.getRepeatRule() != null) {
            return formatFullMappedRule(task.getRepeatRule());
        }
        String raw = task.getLineMetadata().getRecurrenceRawText().trim();
        if (raw.isEmpty()) {
            return "";
        }
        if (task.getLineMetadata().isRecurrenceWhenDone()) {
            return "\u26A0 \u043f\u043e\u0432\u0442\u043e\u0440 \u043f\u043e\u0441\u043b\u0435 \u0432\u044b\u043f\u043e\u043b\u043d\u0435\u043d\u0438\u044f \u043d\u0435 \u043f\u043e\u0434\u0434\u0435\u0440\u0436\u0438\u0432\u0430\u0435\u0442\u0441\u044f: " + raw;
        }
        if (task.getLineMetadata().isRecurrenceUnsupported()) {
            return "\u26A0 \u043f\u043e\u0432\u0442\u043e\u0440 \u043d\u0435 \u0438\u0441\u043f\u043e\u043b\u044c\u0437\u0443\u0435\u0442\u0441\u044f: " + raw;
        }
        return raw;
    }

    /**
     * Same as {@link #formatFullPrimaryRepeat(ObsidianTask)} — kept for existing call sites and tests
     * that expect the detailed primary repeat string.
     */
    public static String formatPrimaryRepeat(ObsidianTask task) {
        return formatFullPrimaryRepeat(task);
    }

    private static String formatShortRepeatUntilDone(ObsidianTask task) {
        Duration d = task.getResolvedRepeatUntilDoneInterval();
        if (d == null) {
            d = task.getExplicitRepeatUntilDoneInterval();
        }
        return formatShortUntilDonePhrase(d);
    }

    private static String formatShortRepeatCoreFromLegacyInterval(ObsidianTask task) {
        if (task.getRepeatMode() == RepeatMode.UNTIL_DONE) {
            return "";
        }
        Duration interval = task.getRepeatInterval();
        if (interval == null || interval.isZero() || interval.isNegative()) {
            return "";
        }
        if (task.getRepeatRule() != null) {
            return "";
        }
        RepeatRule fromDuration = RepeatRule.fromDuration(interval);
        if (fromDuration == null) {
            return "";
        }
        return formatShortMappedRule(fromDuration);
    }

    static String formatShortUntilDonePhrase(Duration duration) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            return "";
        }
        long totalMin = duration.toMinutes();
        if (totalMin <= 0) {
            totalMin = Math.max(1L, duration.getSeconds() / 60L);
        }
        if (totalMin < 60L) {
            return "\u0434\u043e \u0432\u044b\u043f\u043e\u043b\u043d\u0435\u043d\u0438\u044f " + totalMin + " \u043c\u0438\u043d.";
        }
        if (totalMin % 60L == 0L) {
            long h = totalMin / 60L;
            if (h == 1L) {
                return "\u0434\u043e \u0432\u044b\u043f\u043e\u043b\u043d\u0435\u043d\u0438\u044f 1 \u0447";
            }
            return "\u0434\u043e \u0432\u044b\u043f\u043e\u043b\u043d\u0435\u043d\u0438\u044f " + h + " \u0447";
        }
        return "\u0434\u043e \u0432\u044b\u043f\u043e\u043b\u043d\u0435\u043d\u0438\u044f " + totalMin + " \u043c\u0438\u043d.";
    }

    static String formatShortMappedRule(RepeatRule rule) {
        if (rule == null) {
            return "";
        }
        RepeatRule.Unit unit = rule.getUnit();
        int n = rule.getAmount();
        if (unit == RepeatRule.Unit.MINUTES) {
            if (n == 1) {
                return "\u043a\u0430\u0436\u0434\u0443\u044e \u043c\u0438\u043d\u0443\u0442\u0443";
            }
            return "\u043a\u0430\u0436\u0434\u044b\u0435 " + n + " \u043c\u0438\u043d.";
        }
        if (unit == RepeatRule.Unit.HOURS) {
            if (n == 1) {
                return "\u043a\u0430\u0436\u0434\u044b\u0439 \u0447\u0430\u0441";
            }
            return "\u043a\u0430\u0436\u0434\u044b\u0435 " + n + " \u0447";
        }
        if (unit == RepeatRule.Unit.DAYS) {
            if (n == 1) {
                return "\u0435\u0436\u0435\u0434\u043d\u0435\u0432\u043d\u043e";
            }
            return "\u043a\u0430\u0436\u0434\u044b\u0435 " + n + " \u0434\u043d.";
        }
        if (unit == RepeatRule.Unit.WEEKS) {
            return formatShortWeeks(rule);
        }
        if (unit == RepeatRule.Unit.MONTHS) {
            if (n == 1) {
                return "\u0435\u0436\u0435\u043c\u0435\u0441\u044f\u0447\u043d\u043e";
            }
            return "\u043a\u0430\u0436\u0434\u044b\u0435 " + n + " \u043c\u0435\u0441.";
        }
        return rule.formatForUi();
    }

    private static String formatShortWeeks(RepeatRule rule) {
        int n = rule.getAmount();
        if (!rule.hasDaySelector()) {
            if (n == 1) {
                return "\u0435\u0436\u0435\u043d\u0435\u043b\u044c\u043d\u043e";
            }
            return "\u043a\u0430\u0436\u0434\u044b\u0435 " + n + " \u043d\u0435\u0434.";
        }
        Set<DayOfWeek> days = rule.getDaysOfWeek();
        String weeklyCore = n == 1
                ? "\u0435\u0436\u0435\u043d\u0435\u043b\u044c\u043d\u043e"
                : ("\u043a\u0430\u0436\u0434\u044b\u0435 " + n + " \u043d\u0435\u0434.");
        if (days.equals(weekdays())) {
            if (n == 1) {
                return "\u043f\u043e \u0431\u0443\u0434\u043d\u044f\u043c";
            }
            return weeklyCore + SEP + "\u043f\u043e \u0431\u0443\u0434\u043d\u044f\u043c";
        }
        if (days.equals(EnumSet.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY))) {
            if (n == 1) {
                return "\u043f\u043e \u0432\u044b\u0445\u043e\u0434\u043d\u044b\u043c";
            }
            return weeklyCore + SEP + "\u043f\u043e \u0432\u044b\u0445\u043e\u0434\u043d\u044b\u043c";
        }
        if (days.size() == 1) {
            return weeklyCore + SEP + dayShortAbbr(days.iterator().next());
        }
        if (days.size() >= 2 && days.size() <= 3) {
            return weeklyCore + SEP + joinDayShortAbbrs(days);
        }
        return weeklyCore + SEP + "\u0432\u044b\u0431\u0440\u0430\u043d\u043d\u044b\u0435 \u0434\u043d\u0438";
    }

    private static String joinDayShortAbbrs(Set<DayOfWeek> days) {
        List<DayOfWeek> ordered = new ArrayList<>(days);
        ordered.sort(Comparator.comparingInt(DayOfWeek::getValue));
        StringBuilder sb = new StringBuilder();
        for (DayOfWeek d : ordered) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(dayShortAbbr(d));
        }
        return sb.toString();
    }

    private static String dayShortAbbr(DayOfWeek d) {
        switch (d) {
            case MONDAY:
                return "\u043f\u043d";
            case TUESDAY:
                return "\u0432\u0442";
            case WEDNESDAY:
                return "\u0441\u0440";
            case THURSDAY:
                return "\u0447\u0442";
            case FRIDAY:
                return "\u043f\u0442";
            case SATURDAY:
                return "\u0441\u0431";
            case SUNDAY:
            default:
                return "\u0432\u0441";
        }
    }

    private static String formatFullMappedRule(RepeatRule rule) {
        if (rule == null) {
            return "";
        }
        RepeatRule.Unit unit = rule.getUnit();
        int n = rule.getAmount();
        if (unit == RepeatRule.Unit.MINUTES || unit == RepeatRule.Unit.HOURS) {
            return rule.formatForUi();
        }
        if (unit == RepeatRule.Unit.DAYS) {
            if (n == 1) {
                return "\u043a\u0430\u0436\u0434\u044b\u0439 \u0434\u0435\u043d\u044c";
            }
            return "\u043a\u0430\u0436\u0434\u044b\u0435 " + n + " \u0434\u043d\u044f";
        }
        if (unit == RepeatRule.Unit.WEEKS) {
            if (rule.hasDaySelector()) {
                Set<DayOfWeek> days = rule.getDaysOfWeek();
                if (days.equals(weekdays())) {
                    return "\u043f\u043e \u0431\u0443\u0434\u043d\u044f\u043c";
                }
                if (days.equals(EnumSet.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY))) {
                    return "\u043f\u043e \u0432\u044b\u0445\u043e\u0434\u043d\u044b\u043c";
                }
                if (rule.getAmount() == 1 && days.size() == 1) {
                    return weekdayPhraseFull(days.iterator().next());
                }
                return "\u043a\u0430\u0436\u0434\u044b\u0435 " + rule.getAmount()
                        + " \u043d\u0435\u0434. (\u043d\u0435\u0441\u043a\u043e\u043b\u044c\u043a\u043e \u0434\u043d\u0435\u0439)";
            }
            if (n == 1) {
                return "\u043a\u0430\u0436\u0434\u0443\u044e \u043d\u0435\u0434\u0435\u043b\u044e";
            }
            return "\u043a\u0430\u0436\u0434\u044b\u0435 " + n + " \u043d\u0435\u0434\u0435\u043b\u0438";
        }
        if (unit == RepeatRule.Unit.MONTHS) {
            if (rule.hasMonthDaySelector()) {
                return "\u043a\u0430\u0436\u0434\u044b\u0435 " + n + " \u043c\u0435\u0441. (\u0434\u0435\u043d\u044c \u043c\u0435\u0441\u044f\u0446\u0430)";
            }
            if (n == 1) {
                return "\u043a\u0430\u0436\u0434\u044b\u0439 \u043c\u0435\u0441\u044f\u0446";
            }
            return "\u043a\u0430\u0436\u0434\u044b\u0435 " + n + " \u043c\u0435\u0441\u044f\u0446\u0430";
        }
        return rule.formatForUi();
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

    private static String weekdayPhraseFull(DayOfWeek d) {
        switch (d) {
            case MONDAY:
                return "\u043a\u0430\u0436\u0434\u044b\u0439 \u043f\u043e\u043d\u0435\u0434\u0435\u043b\u044c\u043d\u0438\u043a";
            case TUESDAY:
                return "\u043a\u0430\u0436\u0434\u044b\u0439 \u0432\u0442\u043e\u0440\u043d\u0438\u043a";
            case WEDNESDAY:
                return "\u043a\u0430\u0436\u0434\u0443\u044e \u0441\u0440\u0435\u0434\u0443";
            case THURSDAY:
                return "\u043a\u0430\u0436\u0434\u044b\u0439 \u0447\u0435\u0442\u0432\u0435\u0440\u0433";
            case FRIDAY:
                return "\u043a\u0430\u0436\u0434\u0443\u044e \u043f\u044f\u0442\u043d\u0438\u0446\u0443";
            case SATURDAY:
                return "\u043a\u0430\u0436\u0434\u0443\u044e \u0441\u0443\u0431\u0431\u043e\u0442\u0443";
            case SUNDAY:
            default:
                return "\u043a\u0430\u0436\u0434\u043e\u0435 \u0432\u043e\u0441\u043a\u0440\u0435\u0441\u0435\u043d\u044c\u0435";
        }
    }
}
