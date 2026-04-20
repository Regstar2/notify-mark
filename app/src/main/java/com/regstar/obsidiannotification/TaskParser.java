package com.regstar.obsidiannotification;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TaskParser {
    private static final Pattern ACTIVE_TASK =
            Pattern.compile("^\\s*[-*+]\\s+\\[ \\]\\s+(.+)$");
    private static final Pattern ISO_REMINDER =
            Pattern.compile("(?i)(?:^|\\s)@(\\d{4}-\\d{2}-\\d{2})(?:[ T])(\\d{1,2}:\\d{2})\\b");
    private static final Pattern RU_REMINDER =
            Pattern.compile("(?i)(?:^|\\s)@(\\d{1,2}\\.\\d{1,2}\\.\\d{4})\\s+(\\d{1,2}:\\d{2})\\b");
    private static final Pattern TIME_ONLY_REMINDER =
            Pattern.compile("(?i)(?:^|\\s)@(\\d{1,2}:\\d{2})\\b");
    private static final Pattern REPEAT_UNTIL_DONE_FUNCTION =
            Pattern.compile("(?iu)@(?:repeatUntilDone|repeat-until-done|repeat_until_done|untilDone)\\(\\s*(\\d+)\\s*(m|min|мин|м|h|hr|ч|d|day|д)\\s*\\)");
    private static final Pattern REPEAT_FUNCTION =
            Pattern.compile("(?iu)@repeat\\(\\s*(\\d+)\\s*(m|min|мин|м|h|hr|ч|d|day|д)\\s*\\)");
    private static final Pattern REPEAT =
            Pattern.compile("(?iu)(?:\\b(?:every|repeat|повтор|каждые)\\s*:?\\s*)(\\d+)\\s*(m|min|мин|м|h|hr|ч|d|day|д)\\b");

    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter RU_DATE = DateTimeFormatter.ofPattern("d.M.uuuu");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("H:mm");

    private TaskParser() {
    }

    public static List<ObsidianTask> parse(String markdown) {
        return parse(markdown, LocalDate.now());
    }

    static List<ObsidianTask> parse(String markdown, LocalDate defaultDate) {
        List<ObsidianTask> tasks = new ArrayList<>();
        String[] lines = markdown.split("\\R", -1);

        for (int i = 0; i < lines.length; i++) {
            Matcher taskMatcher = ACTIVE_TASK.matcher(lines[i]);
            if (!taskMatcher.find()) {
                continue;
            }

            String body = taskMatcher.group(1).trim();
            LocalDateTime reminderAt = parseReminder(body, defaultDate);
            Duration repeatInterval = parseRepeat(body);
            RepeatMode repeatMode = parseRepeatMode(body, repeatInterval);
            String title = cleanTitle(body);
            String displayTitle = title.isEmpty() ? body : title;

            tasks.add(new ObsidianTask(
                    ObsidianTask.createTaskKey(
                            i + 1,
                            displayTitle,
                            reminderAt,
                            repeatInterval,
                            repeatMode
                    ),
                    i + 1,
                    displayTitle,
                    lines[i],
                    reminderAt,
                    repeatInterval,
                    repeatMode
            ));
        }

        return tasks;
    }

    private static LocalDateTime parseReminder(String body, LocalDate defaultDate) {
        Matcher isoMatcher = ISO_REMINDER.matcher(body);
        if (isoMatcher.find()) {
            return parseDateTime(isoMatcher.group(1), isoMatcher.group(2), ISO_DATE);
        }

        Matcher ruMatcher = RU_REMINDER.matcher(body);
        if (ruMatcher.find()) {
            return parseDateTime(ruMatcher.group(1), ruMatcher.group(2), RU_DATE);
        }

        Matcher timeMatcher = TIME_ONLY_REMINDER.matcher(body);
        if (timeMatcher.find()) {
            try {
                return LocalDateTime.of(defaultDate, LocalTime.parse(timeMatcher.group(1), TIME));
            } catch (DateTimeParseException ignored) {
                return null;
            }
        }

        return null;
    }

    private static LocalDateTime parseDateTime(
            String date,
            String time,
            DateTimeFormatter dateFormatter
    ) {
        try {
            return LocalDateTime.of(
                    LocalDate.parse(date, dateFormatter),
                    LocalTime.parse(time, TIME)
            );
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private static Duration parseRepeat(String body) {
        Matcher matcher = REPEAT_UNTIL_DONE_FUNCTION.matcher(body);
        if (matcher.find()) {
            return parseDuration(matcher.group(1), matcher.group(2));
        }

        matcher = REPEAT_FUNCTION.matcher(body);
        if (matcher.find()) {
            return parseDuration(matcher.group(1), matcher.group(2));
        }

        matcher = REPEAT.matcher(body);
        if (!matcher.find()) {
            return null;
        }

        return parseDuration(matcher.group(1), matcher.group(2));
    }

    private static RepeatMode parseRepeatMode(String body, Duration repeatInterval) {
        if (repeatInterval == null) {
            return RepeatMode.NONE;
        }

        if (REPEAT_UNTIL_DONE_FUNCTION.matcher(body).find()) {
            return RepeatMode.UNTIL_DONE;
        }

        return RepeatMode.ALWAYS;
    }

    private static Duration parseDuration(String rawAmount, String rawUnit) {
        long amount;
        try {
            amount = Long.parseLong(rawAmount);
        } catch (NumberFormatException ignored) {
            return null;
        }

        String unit = rawUnit.toLowerCase(Locale.ROOT);
        if (unit.equals("m") || unit.equals("min") || unit.equals("мин") || unit.equals("м")) {
            return Duration.ofMinutes(amount);
        }
        if (unit.equals("h") || unit.equals("hr") || unit.equals("ч")) {
            return Duration.ofHours(amount);
        }
        if (unit.equals("d") || unit.equals("day") || unit.equals("д")) {
            return Duration.ofDays(amount);
        }

        return null;
    }

    private static String cleanTitle(String body) {
        String cleaned = ISO_REMINDER.matcher(body).replaceAll(" ");
        cleaned = RU_REMINDER.matcher(cleaned).replaceAll(" ");
        cleaned = TIME_ONLY_REMINDER.matcher(cleaned).replaceAll(" ");
        cleaned = REPEAT_UNTIL_DONE_FUNCTION.matcher(cleaned).replaceAll(" ");
        cleaned = REPEAT_FUNCTION.matcher(cleaned).replaceAll(" ");
        cleaned = REPEAT.matcher(cleaned).replaceAll(" ");
        return cleaned.replaceAll("\\s{2,}", " ").trim();
    }
}
