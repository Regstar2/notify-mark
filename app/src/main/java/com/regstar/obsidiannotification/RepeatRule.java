package com.regstar.obsidiannotification;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Canonical repeat schedule model for a markdown-backed task series.
 *
 * <p>`@repeat(...)` controls only when the next head occurrence should appear.
 * It is intentionally separate from `@repeatUntilDone(...)`, which is only the
 * nag interval after a due occurrence has already started.</p>
 */
public final class RepeatRule {
    public enum Unit {
        MINUTES("m"),
        HOURS("h"),
        DAYS("d"),
        WEEKS("w"),
        MONTHS("mo");

        private final String token;

        Unit(String token) {
            this.token = token;
        }

        public String getToken() {
            return token;
        }
    }

    public enum MonthDaySelector {
        SAME_AS_DUE,
        LAST_DAY
    }

    private final int amount;
    private final Unit unit;
    private final EnumSet<DayOfWeek> daysOfWeek;
    private final Integer dayOfMonth;
    private final MonthDaySelector monthDaySelector;

    private RepeatRule(
            int amount,
            Unit unit,
            Set<DayOfWeek> daysOfWeek,
            Integer dayOfMonth,
            MonthDaySelector monthDaySelector
    ) {
        this.amount = Math.max(1, amount);
        this.unit = unit;
        this.daysOfWeek = daysOfWeek == null || daysOfWeek.isEmpty()
                ? EnumSet.noneOf(DayOfWeek.class)
                : EnumSet.copyOf(daysOfWeek);
        this.dayOfMonth = dayOfMonth == null ? null : clampDayOfMonth(dayOfMonth);
        this.monthDaySelector = monthDaySelector == null
                ? MonthDaySelector.SAME_AS_DUE
                : monthDaySelector;
    }

    public static RepeatRule fromDuration(Duration duration) {
        if (duration == null || duration.isNegative() || duration.isZero()) {
            return null;
        }
        long minutes = duration.toMinutes();
        if (minutes % (24L * 60L) == 0L) {
            long days = minutes / (24L * 60L);
            if (days <= Integer.MAX_VALUE) {
                return interval((int) days, Unit.DAYS);
            }
        }
        if (minutes % 60L == 0L) {
            long hours = minutes / 60L;
            if (hours <= Integer.MAX_VALUE) {
                return interval((int) hours, Unit.HOURS);
            }
        }
        if (minutes <= Integer.MAX_VALUE) {
            return interval((int) minutes, Unit.MINUTES);
        }
        return null;
    }

    public static RepeatRule parseStoredSpec(String spec) {
        String raw = spec == null ? "" : spec.trim();
        if (raw.isEmpty()) {
            return null;
        }

        String[] tokens = raw.split("\\s+");
        ParsedToken durationToken = parseToken(tokens[0]);
        if (durationToken == null) {
            return null;
        }

        String daysValue = "";
        String monthDayValue = "";
        for (int i = 1; i < tokens.length; i++) {
            String token = tokens[i].trim();
            if (token.startsWith("@days(") && token.endsWith(")")) {
                daysValue = token.substring("@days(".length(), token.length() - 1);
            } else if (token.startsWith("@monthday(") && token.endsWith(")")) {
                monthDayValue = token.substring("@monthday(".length(), token.length() - 1);
            }
        }

        if (!daysValue.isEmpty() && durationToken.unit == Unit.WEEKS) {
            Set<DayOfWeek> days = parseDaysValue(daysValue);
            return days.isEmpty() ? null : weekly(durationToken.amount, days);
        }
        if (!monthDayValue.isEmpty() && durationToken.unit == Unit.MONTHS) {
            return isLastMonthDayValue(monthDayValue)
                    ? monthlyLastDay(durationToken.amount)
                    : monthly(durationToken.amount, parseMonthDayValue(monthDayValue));
        }
        return interval(durationToken.amount, durationToken.unit);
    }

    public static RepeatRule interval(int amount, Unit unit) {
        return new RepeatRule(amount, unit, EnumSet.noneOf(DayOfWeek.class), null, null);
    }

    public static RepeatRule weekly(int intervalWeeks, Set<DayOfWeek> daysOfWeek) {
        return new RepeatRule(intervalWeeks, Unit.WEEKS, normalizeDays(daysOfWeek), null, null);
    }

    public static RepeatRule monthly(int intervalMonths, Integer dayOfMonth) {
        return new RepeatRule(
                intervalMonths,
                Unit.MONTHS,
                EnumSet.noneOf(DayOfWeek.class),
                dayOfMonth,
                MonthDaySelector.SAME_AS_DUE
        );
    }

    public static RepeatRule monthlyLastDay(int intervalMonths) {
        return new RepeatRule(
                intervalMonths,
                Unit.MONTHS,
                EnumSet.noneOf(DayOfWeek.class),
                null,
                MonthDaySelector.LAST_DAY
        );
    }

    public int getAmount() {
        return amount;
    }

    public Unit getUnit() {
        return unit;
    }

    public Set<DayOfWeek> getDaysOfWeek() {
        return Collections.unmodifiableSet(daysOfWeek);
    }

    public Integer getDayOfMonth() {
        return dayOfMonth;
    }

    public MonthDaySelector getMonthDaySelector() {
        return monthDaySelector;
    }

    public boolean hasDaySelector() {
        return !daysOfWeek.isEmpty();
    }

    public boolean hasMonthDaySelector() {
        return dayOfMonth != null || monthDaySelector == MonthDaySelector.LAST_DAY;
    }

    public Duration toSimpleDuration() {
        if (unit == Unit.MINUTES) {
            return Duration.ofMinutes(amount);
        }
        if (unit == Unit.HOURS) {
            return Duration.ofHours(amount);
        }
        if (unit == Unit.DAYS) {
            return Duration.ofDays(amount);
        }
        if (unit == Unit.WEEKS && !hasDaySelector()) {
            return Duration.ofDays((long) amount * 7L);
        }
        return null;
    }

    public String toCanonicalToken() {
        return amount + unit.getToken();
    }

    public String formatMarkdown(TaskFormatSettings formatSettings, boolean compactSyntax) {
        String repeatKeyword = compactSyntax ? "r" : formatSettings.getRepeatKeyword();
        StringBuilder builder = new StringBuilder()
                .append(" @")
                .append(repeatKeyword)
                .append('(')
                .append(toCanonicalToken())
                .append(')');
        if (hasDaySelector()) {
            builder.append(" @days(").append(formatDays()).append(')');
        }
        if (hasMonthDaySelector()) {
            builder.append(" @monthday(").append(formatMonthDay()).append(')');
        }
        return builder.toString();
    }

    public String formatForUi() {
        StringBuilder builder = new StringBuilder(toCanonicalToken());
        if (hasDaySelector()) {
            builder.append(" @days(").append(formatDays()).append(')');
        }
        if (hasMonthDaySelector()) {
            builder.append(" @monthday(").append(formatMonthDay()).append(')');
        }
        return builder.toString();
    }

    public LocalDateTime nextDueAfter(LocalDateTime currentDue) {
        if (currentDue == null) {
            return null;
        }
        if (unit == Unit.MINUTES) {
            return currentDue.plusMinutes(amount);
        }
        if (unit == Unit.HOURS) {
            return currentDue.plusHours(amount);
        }
        if (unit == Unit.DAYS) {
            return currentDue.plusDays(amount);
        }
        if (unit == Unit.WEEKS) {
            return nextWeeklyDue(currentDue);
        }
        if (unit == Unit.MONTHS) {
            return nextMonthlyDue(currentDue);
        }
        return null;
    }

    private LocalDateTime nextWeeklyDue(LocalDateTime currentDue) {
        if (daysOfWeek.isEmpty()) {
            return currentDue.plusWeeks(amount);
        }

        List<DayOfWeek> orderedDays = new ArrayList<>(daysOfWeek);
        orderedDays.sort(Comparator.comparingInt(DayOfWeek::getValue));
        LocalDateTime candidate = currentDue.plusDays(1);
        for (int i = 0; i < 366 * Math.max(1, amount); i++) {
            DayOfWeek candidateDay = candidate.getDayOfWeek();
            if (daysOfWeek.contains(candidateDay)) {
                long weeksBetween = java.time.temporal.ChronoUnit.WEEKS.between(
                        startOfWeek(currentDue),
                        startOfWeek(candidate)
                );
                if (weeksBetween >= 0 && weeksBetween % amount == 0) {
                    return candidate.withHour(currentDue.getHour())
                            .withMinute(currentDue.getMinute())
                            .withSecond(currentDue.getSecond())
                            .withNano(currentDue.getNano());
                }
            }
            candidate = candidate.plusDays(1);
        }
        return currentDue.plusWeeks(amount);
    }

    private LocalDateTime nextMonthlyDue(LocalDateTime currentDue) {
        YearMonth month = YearMonth.from(currentDue).plusMonths(amount);
        int targetDay = resolveDayOfMonth(month, currentDue.getDayOfMonth());
        return currentDue.withYear(month.getYear())
                .withMonth(month.getMonthValue())
                .withDayOfMonth(targetDay);
    }

    private int resolveDayOfMonth(YearMonth month, int fallbackDay) {
        if (monthDaySelector == MonthDaySelector.LAST_DAY) {
            return month.lengthOfMonth();
        }
        int target = dayOfMonth == null ? fallbackDay : dayOfMonth;
        return Math.min(target, month.lengthOfMonth());
    }

    private String formatDays() {
        if (daysOfWeek.equals(EnumSet.of(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY
        ))) {
            return "weekdays";
        }
        if (daysOfWeek.equals(EnumSet.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY))) {
            return "weekends";
        }
        List<DayOfWeek> orderedDays = new ArrayList<>(daysOfWeek);
        orderedDays.sort(Comparator.comparingInt(DayOfWeek::getValue));
        StringBuilder builder = new StringBuilder();
        for (DayOfWeek day : orderedDays) {
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(dayToken(day));
        }
        return builder.toString();
    }

    private String formatMonthDay() {
        if (monthDaySelector == MonthDaySelector.LAST_DAY) {
            return "last";
        }
        return String.valueOf(dayOfMonth);
    }

    private static LocalDateTime startOfWeek(LocalDateTime value) {
        return value.toLocalDate()
                .minusDays(value.getDayOfWeek().getValue() - 1L)
                .atTime(value.toLocalTime());
    }

    public static Set<DayOfWeek> parseDaysValue(String rawValue) {
        String value = rawValue == null ? "" : rawValue.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            return Collections.emptySet();
        }
        if ("weekdays".equals(value)) {
            return EnumSet.of(
                    DayOfWeek.MONDAY,
                    DayOfWeek.TUESDAY,
                    DayOfWeek.WEDNESDAY,
                    DayOfWeek.THURSDAY,
                    DayOfWeek.FRIDAY
            );
        }
        if ("weekends".equals(value)) {
            return EnumSet.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);
        }

        EnumSet<DayOfWeek> result = EnumSet.noneOf(DayOfWeek.class);
        for (String token : value.split("[,;\\s]+")) {
            DayOfWeek day = parseDayToken(token);
            if (day == null) {
                return Collections.emptySet();
            }
            result.add(day);
        }
        return result;
    }

    public static Integer parseMonthDayValue(String rawValue) {
        String value = rawValue == null ? "" : rawValue.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty() || "last".equals(value)) {
            return null;
        }
        try {
            return clampDayOfMonth(Integer.parseInt(value));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    public static boolean isLastMonthDayValue(String rawValue) {
        return "last".equalsIgnoreCase(rawValue == null ? "" : rawValue.trim());
    }

    private static EnumSet<DayOfWeek> normalizeDays(Set<DayOfWeek> source) {
        if (source == null || source.isEmpty()) {
            return EnumSet.noneOf(DayOfWeek.class);
        }
        return EnumSet.copyOf(source);
    }

    private static DayOfWeek parseDayToken(String token) {
        String value = token == null ? "" : token.trim().toLowerCase(Locale.ROOT);
        if ("mon".equals(value)) {
            return DayOfWeek.MONDAY;
        }
        if ("tue".equals(value)) {
            return DayOfWeek.TUESDAY;
        }
        if ("wed".equals(value)) {
            return DayOfWeek.WEDNESDAY;
        }
        if ("thu".equals(value)) {
            return DayOfWeek.THURSDAY;
        }
        if ("fri".equals(value)) {
            return DayOfWeek.FRIDAY;
        }
        if ("sat".equals(value)) {
            return DayOfWeek.SATURDAY;
        }
        if ("sun".equals(value)) {
            return DayOfWeek.SUNDAY;
        }
        return null;
    }

    private static String dayToken(DayOfWeek day) {
        switch (day) {
            case MONDAY:
                return "mon";
            case TUESDAY:
                return "tue";
            case WEDNESDAY:
                return "wed";
            case THURSDAY:
                return "thu";
            case FRIDAY:
                return "fri";
            case SATURDAY:
                return "sat";
            case SUNDAY:
                return "sun";
            default:
                return day.name().toLowerCase(Locale.ROOT);
        }
    }

    private static int clampDayOfMonth(int value) {
        if (value < 1) {
            return 1;
        }
        return Math.min(31, value);
    }

    private static ParsedToken parseToken(String token) {
        String value = token == null ? "" : token.trim().toLowerCase(Locale.ROOT);
        if (value.length() < 2) {
            return null;
        }
        int index = 0;
        while (index < value.length() && Character.isDigit(value.charAt(index))) {
            index++;
        }
        if (index == 0 || index >= value.length()) {
            return null;
        }
        int amount;
        try {
            amount = Integer.parseInt(value.substring(0, index));
        } catch (NumberFormatException exception) {
            return null;
        }
        String unitToken = value.substring(index);
        Unit unit;
        if ("m".equals(unitToken)) {
            unit = Unit.MINUTES;
        } else if ("h".equals(unitToken)) {
            unit = Unit.HOURS;
        } else if ("d".equals(unitToken)) {
            unit = Unit.DAYS;
        } else if ("w".equals(unitToken)) {
            unit = Unit.WEEKS;
        } else if ("mo".equals(unitToken)) {
            unit = Unit.MONTHS;
        } else {
            return null;
        }
        return new ParsedToken(amount, unit);
    }

    private static final class ParsedToken {
        private final int amount;
        private final Unit unit;

        private ParsedToken(int amount, Unit unit) {
            this.amount = amount;
            this.unit = unit;
        }
    }
}
