package com.regstar.obsidiannotification;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TaskParser {
    private static final Pattern TASK =
            Pattern.compile("^\\s*[-*+]\\s+\\[([ xX])\\]\\s+(.+)$");
    private static final Pattern NON_CHECKBOX_BULLET =
            Pattern.compile("^\\s*[-*+]\\s+(?!\\[[ xX]\\]\\s+)(.+)$");
    private static final Pattern ISO_REMINDER =
            Pattern.compile("(?i)(?:^|\\s)@(\\d{4}-\\d{2}-\\d{2})(?:[ T]+)(\\d{1,2}:\\d{2})\\b");
    private static final Pattern RU_REMINDER =
            Pattern.compile("(?i)(?:^|\\s)@(\\d{1,2}\\.\\d{1,2}\\.\\d{4})\\s+(\\d{1,2}:\\d{2})\\b");
    private static final Pattern ISO_DATE_ONLY_REMINDER =
            Pattern.compile("(?i)(?:^|\\s)@(\\d{4}-\\d{2}-\\d{2})\\b");
    private static final Pattern RU_DATE_ONLY_REMINDER =
            Pattern.compile("(?i)(?:^|\\s)@(\\d{1,2}\\.\\d{1,2}\\.\\d{4})\\b");
    private static final Pattern TIME_ONLY_REMINDER =
            Pattern.compile("(?i)(?:^|\\s)@(\\d{1,2}:\\d{2})\\b");
    private static final Pattern REPEAT =
            Pattern.compile("(?iu)(?:\\b(?:every|repeat|повтор|каждые)\\s*:?\\s*)(\\d+)\\s*(m|min|мин|м|h|hr|ч|d|day|д)\\b");
    private static final Pattern ANY_AT_TOKEN =
            Pattern.compile("@\\S+");
    private static final Pattern ANY_REPEAT_WORD =
            Pattern.compile("(?iu)\\b(?:every|repeat|повтор|каждые)\\b\\s*:?\\s*\\d+\\S*");
    private static final Pattern ISO_DATE_TIME_VALUE =
            Pattern.compile("^(\\d{4}-\\d{2}-\\d{2})(?:[ T]+)(\\d{1,2}:\\d{2})$");
    private static final Pattern RU_DATE_TIME_VALUE =
            Pattern.compile("^(\\d{1,2}\\.\\d{1,2}\\.\\d{4})\\s+(\\d{1,2}:\\d{2})$");
    private static final Pattern ISO_DATE_VALUE =
            Pattern.compile("^(\\d{4}-\\d{2}-\\d{2})$");
    private static final Pattern RU_DATE_VALUE =
            Pattern.compile("^(\\d{1,2}\\.\\d{1,2}\\.\\d{4})$");
    private static final Pattern TIME_VALUE =
            Pattern.compile("^(\\d{1,2}:\\d{2})$");
    private static final Pattern DURATION_VALUE =
            Pattern.compile("(?iu)^(\\d+)\\s*(m|min|мин|м|h|hr|ч|d|day|д)$");
    private static final Pattern HASH_TAG =
            Pattern.compile("(?<!\\S)#([\\p{L}\\p{N}_/-]+)");
    private static final Pattern SNOOZED_COUNT =
            Pattern.compile("(?iu)@snoozed\\(\\s*\\d+\\s*\\)");

    private static final DateTimeFormatter ISO_DATE =
            DateTimeFormatter.ISO_LOCAL_DATE.withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter RU_DATE =
            DateTimeFormatter.ofPattern("d.M.uuuu").withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("H:mm").withResolverStyle(ResolverStyle.STRICT);
    private static final LocalTime DATE_ONLY_DEFAULT_TIME = LocalTime.of(9, 0);

    private TaskParser() {
    }

    public static List<ObsidianTask> parse(String markdown) {
        return parse(markdown, LocalDate.now());
    }

    static List<ObsidianTask> parse(String markdown, LocalDate defaultDate) {
        return parseDocument(markdown, defaultDate, "", TaskFormatSettings.defaults())
                .getActiveTasks();
    }

    public static TaskParseResult parseDocument(String markdown, String sourceName) {
        return parseDocument(markdown, LocalDate.now(), sourceName, TaskFormatSettings.defaults());
    }

    static TaskParseResult parseDocument(String markdown, LocalDate defaultDate, String sourceName) {
        return parseDocument(markdown, defaultDate, sourceName, TaskFormatSettings.defaults());
    }

    static TaskParseResult parseDocument(
            String markdown,
            LocalDate defaultDate,
            String sourceName,
            TaskFormatSettings formatSettings
    ) {
        TaskFormatSettings format = formatSettings == null
                ? TaskFormatSettings.defaults()
                : formatSettings;
        List<ObsidianTask> tasks = new ArrayList<>();
        List<TaskParseError> errors = new ArrayList<>();
        String[] lines = markdown.split("\\R", -1);

        for (int i = 0; i < lines.length; i++) {
            String line = stripBom(lines[i]);
            Matcher taskMatcher = TASK.matcher(line);
            int lineNumber = i + 1;
            boolean completed = false;
            boolean checkboxTask = taskMatcher.find();
            String body;
            if (checkboxTask) {
                completed = taskMatcher.group(1).equalsIgnoreCase("x");
                body = taskMatcher.group(2).trim();
            } else {
                body = nonCheckboxReminderBody(line, format);
                if (body == null) {
                    continue;
                }
            }

            ParsedTaskFields fields = parseFields(body, defaultDate, format);
            if (!checkboxTask && fields.reminderAt == null) {
                addParseWarnings(errors, sourceName, lineNumber, body, fields, format);
                continue;
            }

            String title = cleanTitle(body, format);
            String displayTitle = title.isEmpty() ? body : title;

            addParseWarnings(errors, sourceName, lineNumber, body, fields, format);

            tasks.add(new ObsidianTask(
                    ObsidianTask.createTaskKey(
                            sourceName,
                            lineNumber,
                            displayTitle,
                            fields.reminderAt,
                            fields.repeatInterval,
                            fields.repeatMode
                    ),
                    sourceName,
                    lineNumber,
                    displayTitle,
                    lines[i],
                    fields.reminderAt,
                    fields.repeatInterval,
                    fields.repeatMode,
                    completed,
                    fields.tags,
                    fields.priority,
                    fields.group
            ));
        }

        return new TaskParseResult(tasks, errors);
    }

    private static String nonCheckboxReminderBody(String line, TaskFormatSettings format) {
        String trimmed = line == null ? "" : line.trim();
        if (trimmed.isEmpty()
                || trimmed.startsWith("#")
                || trimmed.startsWith(">")
                || trimmed.startsWith("|")
                || trimmed.startsWith("```")) {
            return null;
        }

        Matcher bulletMatcher = NON_CHECKBOX_BULLET.matcher(line);
        String body = bulletMatcher.find() ? bulletMatcher.group(1).trim() : trimmed;
        return hasReminderSyntax(body, format) ? body : null;
    }

    private static String stripBom(String line) {
        if (line != null && !line.isEmpty() && line.charAt(0) == '\uFEFF') {
            return line.substring(1);
        }
        return line;
    }

    private static ParsedTaskFields parseFields(
            String body,
            LocalDate defaultDate,
            TaskFormatSettings format
    ) {
        ParsedTaskFields fields = new ParsedTaskFields();
        fields.reminderAt = parseReminder(body, defaultDate, format, fields);
        fields.repeatInterval = parseRepeat(body, format, fields);
        fields.repeatMode = parseRepeatMode(body, fields.repeatInterval, format);
        fields.tags = parseTags(body, format);
        fields.priority = parsePriority(body, format, fields);
        fields.group = parseGroup(body, format);
        return fields;
    }

    private static LocalDateTime parseReminder(
            String body,
            LocalDate defaultDate,
            TaskFormatSettings format,
            ParsedTaskFields fields
    ) {
        String dueValue = findFunctionValue(body, format.dueKeywords());
        if (dueValue != null) {
            LocalDateTime parsed = parseDueValue(dueValue, defaultDate);
            fields.dueFunctionInvalid = parsed == null;
            return parsed;
        }

        Matcher isoMatcher = ISO_REMINDER.matcher(body);
        if (isoMatcher.find()) {
            return parseDateTime(isoMatcher.group(1), isoMatcher.group(2), ISO_DATE);
        }

        Matcher ruMatcher = RU_REMINDER.matcher(body);
        if (ruMatcher.find()) {
            return parseDateTime(ruMatcher.group(1), ruMatcher.group(2), RU_DATE);
        }

        Matcher isoDateMatcher = ISO_DATE_ONLY_REMINDER.matcher(body);
        if (isoDateMatcher.find()) {
            return parseDateAtDefaultTime(isoDateMatcher.group(1), ISO_DATE);
        }

        Matcher ruDateMatcher = RU_DATE_ONLY_REMINDER.matcher(body);
        if (ruDateMatcher.find()) {
            return parseDateAtDefaultTime(ruDateMatcher.group(1), RU_DATE);
        }

        Matcher timeMatcher = TIME_ONLY_REMINDER.matcher(body);
        if (timeMatcher.find()) {
            return parseTimeAtDate(timeMatcher.group(1), defaultDate);
        }

        return null;
    }

    private static LocalDateTime parseDueValue(String rawValue, LocalDate defaultDate) {
        String value = rawValue == null ? "" : rawValue.trim();
        Matcher isoDateTimeMatcher = ISO_DATE_TIME_VALUE.matcher(value);
        if (isoDateTimeMatcher.find()) {
            return parseDateTime(isoDateTimeMatcher.group(1), isoDateTimeMatcher.group(2), ISO_DATE);
        }

        Matcher ruDateTimeMatcher = RU_DATE_TIME_VALUE.matcher(value);
        if (ruDateTimeMatcher.find()) {
            return parseDateTime(ruDateTimeMatcher.group(1), ruDateTimeMatcher.group(2), RU_DATE);
        }

        Matcher isoDateMatcher = ISO_DATE_VALUE.matcher(value);
        if (isoDateMatcher.find()) {
            return parseDateAtDefaultTime(isoDateMatcher.group(1), ISO_DATE);
        }

        Matcher ruDateMatcher = RU_DATE_VALUE.matcher(value);
        if (ruDateMatcher.find()) {
            return parseDateAtDefaultTime(ruDateMatcher.group(1), RU_DATE);
        }

        Matcher timeMatcher = TIME_VALUE.matcher(value);
        if (timeMatcher.find()) {
            return parseTimeAtDate(timeMatcher.group(1), defaultDate);
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

    private static LocalDateTime parseDateAtDefaultTime(
            String date,
            DateTimeFormatter dateFormatter
    ) {
        try {
            return LocalDateTime.of(LocalDate.parse(date, dateFormatter), DATE_ONLY_DEFAULT_TIME);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private static LocalDateTime parseTimeAtDate(String time, LocalDate defaultDate) {
        try {
            return LocalDateTime.of(defaultDate, LocalTime.parse(time, TIME));
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private static Duration parseRepeat(
            String body,
            TaskFormatSettings format,
            ParsedTaskFields fields
    ) {
        String repeatUntilDoneValue = findFunctionValue(body, format.repeatUntilDoneKeywords());
        if (repeatUntilDoneValue != null) {
            Duration repeatInterval = parseDurationValue(repeatUntilDoneValue);
            fields.repeatFunctionInvalid = repeatInterval == null;
            return repeatInterval;
        }

        String repeatValue = findFunctionValue(body, format.repeatKeywords());
        if (repeatValue != null) {
            Duration repeatInterval = parseDurationValue(repeatValue);
            fields.repeatFunctionInvalid = repeatInterval == null;
            return repeatInterval;
        }

        Matcher matcher = REPEAT.matcher(body);
        if (!matcher.find()) {
            return null;
        }

        return parseDuration(matcher.group(1), matcher.group(2));
    }

    private static RepeatMode parseRepeatMode(
            String body,
            Duration repeatInterval,
            TaskFormatSettings format
    ) {
        if (repeatInterval == null) {
            return RepeatMode.NONE;
        }

        if (findFunctionValue(body, format.repeatUntilDoneKeywords()) != null) {
            return RepeatMode.UNTIL_DONE;
        }

        return RepeatMode.ALWAYS;
    }

    private static Duration parseDurationValue(String rawValue) {
        String value = rawValue == null ? "" : rawValue.trim();
        Matcher matcher = DURATION_VALUE.matcher(value);
        if (!matcher.find()) {
            return null;
        }
        return parseDuration(matcher.group(1), matcher.group(2));
    }

    private static Duration parseDuration(String rawAmount, String rawUnit) {
        long amount;
        try {
            amount = Long.parseLong(rawAmount);
        } catch (NumberFormatException ignored) {
            return null;
        }

        if (amount <= 0) {
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

    private static List<String> parseTags(String body, TaskFormatSettings format) {
        Set<String> tags = new LinkedHashSet<>();
        String tagValue = findFunctionValue(body, format.tagKeywords());
        if (tagValue != null) {
            for (String rawTag : tagValue.split("[,;\\s]+")) {
                addTag(tags, rawTag);
            }
        }

        Matcher hashTagMatcher = HASH_TAG.matcher(body);
        while (hashTagMatcher.find()) {
            addTag(tags, hashTagMatcher.group(1));
        }
        return new ArrayList<>(tags);
    }

    private static void addTag(Set<String> tags, String rawTag) {
        String tag = rawTag == null ? "" : rawTag.trim();
        while (tag.startsWith("#")) {
            tag = tag.substring(1);
        }
        tag = tag.replaceAll("[^\\p{L}\\p{N}_/-]", "");
        if (!tag.isEmpty()) {
            tags.add(tag);
        }
    }

    private static TaskPriority parsePriority(
            String body,
            TaskFormatSettings format,
            ParsedTaskFields fields
    ) {
        String priorityValue = findFunctionValue(body, format.priorityKeywords());
        if (priorityValue == null) {
            return TaskPriority.NONE;
        }

        fields.priorityFunctionInvalid = !TaskPriority.isRecognized(priorityValue);
        return TaskPriority.fromName(priorityValue);
    }

    private static String parseGroup(String body, TaskFormatSettings format) {
        String groupValue = findFunctionValue(body, format.groupKeywords());
        return ObsidianTask.normalizeGroup(groupValue);
    }

    private static void addParseWarnings(
            List<TaskParseError> errors,
            String sourceName,
            int lineNumber,
            String body,
            ParsedTaskFields fields,
            TaskFormatSettings format
    ) {
        if (fields.dueFunctionInvalid) {
            errors.add(new TaskParseError(
                    sourceName,
                    lineNumber,
                    "не удалось разобрать @"
                            + format.getDueKeyword()
                            + "(...). Используйте дату-время, дату или время: @"
                            + format.getDueKeyword()
                            + "(2026-04-20 14:30), @"
                            + format.getDueKeyword()
                            + "(2026-04-20), @"
                            + format.getDueKeyword()
                            + "(14:30)"
            ));
        } else if (fields.reminderAt == null && hasSuspiciousReminderToken(body, format)) {
            errors.add(new TaskParseError(
                    sourceName,
                    lineNumber,
                    "не удалось разобрать время. Используйте @"
                            + format.getDueKeyword()
                            + "(2026-04-20 14:30), @"
                            + format.getDueKeyword()
                            + "(2026-04-20), @"
                            + format.getDueKeyword()
                            + "(14:30) или legacy @2026-04-20 14:30"
            ));
        }

        if (fields.repeatFunctionInvalid
                || (fields.repeatInterval == null && hasSuspiciousRepeatToken(body, format))) {
            errors.add(new TaskParseError(
                    sourceName,
                    lineNumber,
                    "не удалось разобрать повтор. Используйте @"
                            + format.getRepeatKeyword()
                            + "(15m) или @"
                            + format.getRepeatUntilDoneKeyword()
                            + "(15m)"
            ));
        }

        if (fields.priorityFunctionInvalid) {
            errors.add(new TaskParseError(
                    sourceName,
                    lineNumber,
                    "не удалось разобрать приоритет. Используйте @"
                            + format.getPriorityKeyword()
                            + "(low), @"
                            + format.getPriorityKeyword()
                            + "(medium), @"
                            + format.getPriorityKeyword()
                            + "(high) или @"
                            + format.getPriorityKeyword()
                            + "(urgent)"
            ));
        }
    }

    private static boolean hasSuspiciousReminderToken(String body, TaskFormatSettings format) {
        Matcher matcher = ANY_AT_TOKEN.matcher(body);
        while (matcher.find()) {
            String token = matcher.group();
            if (!isKnownFunctionToken(token, format)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasReminderSyntax(String body, TaskFormatSettings format) {
        return findFunctionValue(body, format.dueKeywords()) != null
                || ISO_REMINDER.matcher(body).find()
                || RU_REMINDER.matcher(body).find()
                || ISO_DATE_ONLY_REMINDER.matcher(body).find()
                || RU_DATE_ONLY_REMINDER.matcher(body).find()
                || TIME_ONLY_REMINDER.matcher(body).find();
    }

    private static boolean hasSuspiciousRepeatToken(String body, TaskFormatSettings format) {
        return ANY_REPEAT_WORD.matcher(body).find()
                || findFunctionValue(body, format.repeatKeywords()) != null
                || findFunctionValue(body, format.repeatUntilDoneKeywords()) != null;
    }

    private static String cleanTitle(String body, TaskFormatSettings format) {
        String cleaned = removeFunctions(body, format.dueKeywords());
        cleaned = removeFunctions(cleaned, format.repeatUntilDoneKeywords());
        cleaned = removeFunctions(cleaned, format.repeatKeywords());
        cleaned = removeFunctions(cleaned, format.tagKeywords());
        cleaned = removeFunctions(cleaned, format.priorityKeywords());
        cleaned = removeFunctions(cleaned, format.groupKeywords());
        cleaned = SNOOZED_COUNT.matcher(cleaned).replaceAll(" ");
        cleaned = ISO_REMINDER.matcher(cleaned).replaceAll(" ");
        cleaned = RU_REMINDER.matcher(cleaned).replaceAll(" ");
        cleaned = ISO_DATE_ONLY_REMINDER.matcher(cleaned).replaceAll(" ");
        cleaned = RU_DATE_ONLY_REMINDER.matcher(cleaned).replaceAll(" ");
        cleaned = TIME_ONLY_REMINDER.matcher(cleaned).replaceAll(" ");
        cleaned = REPEAT.matcher(cleaned).replaceAll(" ");
        cleaned = HASH_TAG.matcher(cleaned).replaceAll(" ");
        return cleaned.replaceAll("\\s{2,}", " ").trim();
    }

    private static String findFunctionValue(String body, List<String> keywords) {
        Matcher matcher = functionPattern(keywords, "([^)]*)").matcher(body);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private static String removeFunctions(String body, List<String> keywords) {
        return functionPattern(keywords, "[^)]*").matcher(body).replaceAll(" ");
    }

    private static Pattern functionPattern(List<String> keywords, String valuePattern) {
        return Pattern.compile("(?iu)@(?:" + keywordAlternatives(keywords) + ")\\(\\s*"
                + valuePattern
                + "\\s*\\)");
    }

    private static String keywordAlternatives(List<String> keywords) {
        StringBuilder builder = new StringBuilder();
        for (String keyword : keywords) {
            if (keyword == null || keyword.trim().isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('|');
            }
            builder.append(Pattern.quote(keyword.trim()));
        }
        if (builder.length() == 0) {
            builder.append(Pattern.quote(TaskFormatSettings.DEFAULT_DUE_KEYWORD));
        }
        return builder.toString();
    }

    private static boolean isKnownFunctionToken(String token, TaskFormatSettings format) {
        String normalized = token.toLowerCase(Locale.ROOT);
        return normalized.startsWith("@snoozed(")
                || startsWithFunction(normalized, format.dueKeywords())
                || startsWithFunction(normalized, format.repeatKeywords())
                || startsWithFunction(normalized, format.repeatUntilDoneKeywords())
                || startsWithFunction(normalized, format.tagKeywords())
                || startsWithFunction(normalized, format.priorityKeywords())
                || startsWithFunction(normalized, format.groupKeywords());
    }

    private static boolean startsWithFunction(String token, List<String> keywords) {
        for (String keyword : keywords) {
            if (token.startsWith("@" + keyword.toLowerCase(Locale.ROOT) + "(")) {
                return true;
            }
        }
        return false;
    }

    private static final class ParsedTaskFields {
        private LocalDateTime reminderAt;
        private Duration repeatInterval;
        private RepeatMode repeatMode = RepeatMode.NONE;
        private List<String> tags = new ArrayList<>();
        private TaskPriority priority = TaskPriority.NONE;
        private String group = ObsidianTask.DEFAULT_GROUP;
        private boolean dueFunctionInvalid;
        private boolean repeatFunctionInvalid;
        private boolean priorityFunctionInvalid;
    }
}
