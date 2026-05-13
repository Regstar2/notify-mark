package com.regstar.obsidiannotification.core.tasks;

import com.regstar.obsidiannotification.core.reminders.*;
import com.regstar.obsidiannotification.core.source.*;
import com.regstar.obsidiannotification.prefs.*;
import com.regstar.obsidiannotification.support.ErrorLog;

import java.time.Duration;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses markdown documents into {@link ObsidianTask} records and parse
 * warnings.
 *
 * <p>This parser is the only supported interpretation of NotifyMark task
 * syntax. UI code and reminder code should consume parsed tasks instead of
 * trying to interpret markdown fragments independently.</p>
 */
public final class TaskParser {
    private static final String SERIES_ID_KEYWORD = "id";
    private static final String DAYS_KEYWORD = "days";
    private static final String MONTHDAY_KEYWORD = "monthday";
    private static final String OVERDUE_GRACE_KEYWORD = "grace";
    private static final String SNOOZE_KEYWORD = "snooze";
    private static final Pattern CHECKBOX_TASK =
            Pattern.compile("^(\\s*)(?:(\\d+)\\.\\s+|[-*+]\\s+)\\[([^\\]]+)\\]\\s+(.+)$");
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
            Pattern.compile("(?iu)^(\\d+)\\s*(m|min|мин|м|h|hr|ч|d|day|д|w|wk|week|mo|mon|month)$");
    private static final Pattern HASH_TAG =
            Pattern.compile("(?<!\\S)#([\\p{L}\\p{N}_/-]+)");
    private static final Pattern SNOOZED_COUNT =
            Pattern.compile("(?iu)@snoozed\\(\\s*\\d+\\s*\\)");
    private static final Pattern SKIPPED_MARKER =
            Pattern.compile("(?iu)(?:@skipped\\b|@skip\\b|@status\\(\\s*skipped\\s*\\))");

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

    public static TaskParseResult parseDocument(String markdown, LocalDate defaultDate, String sourceName) {
        return parseDocument(markdown, defaultDate, sourceName, TaskFormatSettings.defaults());
    }

    public static TaskParseResult parseDocument(
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
        List<ParsedTaskRecord> taskStack = new ArrayList<>();
        String[] lines = markdown.split("\\R", -1);
        boolean inFencedCodeBlock = false;

        for (int i = 0; i < lines.length; i++) {
            String line = stripBom(lines[i]);
            String trimmedLine = line.trim();
            int lineNumber = i + 1;
            int indentLevel = leadingIndentLevel(line);
            if (trimmedLine.startsWith("```") || trimmedLine.startsWith("~~~")) {
                inFencedCodeBlock = !inFencedCodeBlock;
                continue;
            }
            if (inFencedCodeBlock) {
                continue;
            }
            boolean completed = false;
            boolean skipped = false;
            boolean customCheckbox = false;
            Character originalCheckboxChar = null;
            Matcher taskMatcher = CHECKBOX_TASK.matcher(line);
            boolean checkboxTask = taskMatcher.find();
            String body;
            if (checkboxTask) {
                String inside = taskMatcher.group(3).trim();
                completed = "x".equalsIgnoreCase(inside);
                skipped = "-".equals(inside);
                customCheckbox = !completed && !skipped && !inside.isEmpty();
                if (!inside.isEmpty()) {
                    originalCheckboxChar = inside.charAt(0);
                }
                body = taskMatcher.group(4).trim();
            } else {
                body = nonCheckboxReminderBody(line, format);
                if (body == null) {
                    continue;
                }
            }

            ParsedTaskFields fields = parseFields(body, defaultDate, format);
            ObsidianTasksParser.Result obsidian = ObsidianTasksParser.parse(
                    body,
                    defaultDate,
                    format.getObsidianDefaultReminderTime(),
                    format.getCompatibilityMode()
            );
            integrateObsidianMetadata(body, format, fields, obsidian, customCheckbox);
            completed = completed || obsidian.doneDate != null;
            skipped = skipped || obsidian.cancelledDate != null || fields.skipped;

            ParsedTaskRecord parentRecord = parentForIndent(taskStack, indentLevel);
            if (parentRecord != null && !fields.groupExplicit) {
                fields.group = parentRecord.task.getGroup();
            }
            if (!checkboxTask && fields.reminderAt == null) {
                addParseWarnings(errors, sourceName, lineNumber, body, fields, format);
                continue;
            }

            String titleBody = body;
            if (format.getCompatibilityMode() != TaskFormatCompatibilityMode.NATIVE
                    && ObsidianTasksParser.containsObsidianTasksServiceMarkers(body)) {
                titleBody = ObsidianTasksParser.stripServiceMetadataForDisplay(
                        body,
                        defaultDate,
                        format.getCompatibilityMode()
                );
            }
            String title = cleanTitle(
                    titleBody,
                    format,
                    format.getCompatibilityMode() != TaskFormatCompatibilityMode.NATIVE
                            && ObsidianTasksParser.containsObsidianTasksServiceMarkers(body)
            );
            String displayTitle = title.isEmpty() ? titleBody : title;
            displayTitle = displayTitle.replaceAll("\\s{2,}", " ").trim();
            if (displayTitle.isEmpty()) {
                displayTitle = "\u0417\u0430\u0434\u0430\u0447\u0430 \u0431\u0435\u0437 \u043d\u0430\u0437\u0432\u0430\u043d\u0438\u044f";
            }

            TaskSyntaxStyle syntaxStyle = detectSyntaxStyle(body, format, obsidian);
            boolean conflict = fields.obsidianMetadataConflict;
            TaskLineMetadata lineMetadata = obsidian.toMetadata(
                    syntaxStyle,
                    originalCheckboxChar,
                    customCheckbox,
                    findFunctionValue(body, format.dueKeywords()) != null,
                    conflict
            );

            addParseWarnings(errors, sourceName, lineNumber, body, fields, format);

            ObsidianTask task = new ObsidianTask(
                    ObsidianTask.createTaskKey(
                            sourceName,
                            lineNumber,
                            displayTitle,
                            fields.reminderAt,
                            legacyRepeatInterval(fields),
                            legacyRepeatMode(fields)
                    ),
                    sourceName,
                    lineNumber,
                    displayTitle,
                    lines[i],
                    fields.reminderAt,
                    legacyRepeatInterval(fields),
                    legacyRepeatMode(fields),
                    completed,
                    skipped,
                    parentRecord == null ? "" : parentRecord.task.getTaskKey(),
                    parentRecord == null ? 0 : parentRecord.task.getLineNumber(),
                    indentLevel,
                    fields.tags,
                    fields.priority,
                    fields.group,
                    fields.snoozeDuration,
                    fields.explicitOverdueGracePeriod,
                    fields.explicitOverdueGracePeriod,
                    fields.explicitRepeatUntilDoneInterval,
                    fields.explicitRepeatUntilDoneInterval,
                    fields.repeatRule,
                    fields.seriesId,
                    lineMetadata
            );
            tasks.add(task);
            if (parentRecord != null) {
                parentRecord.task.addSubtask(task);
            }
            pushTaskRecord(taskStack, new ParsedTaskRecord(task, indentLevel));
        }

        return new TaskParseResult(tasks, errors);
    }

    private static int leadingIndentLevel(String line) {
        if (line == null || line.isEmpty()) {
            return 0;
        }
        int columns = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == ' ') {
                columns++;
            } else if (c == '\t') {
                columns += 4;
            } else {
                break;
            }
        }
        return columns;
    }

    private static ParsedTaskRecord parentForIndent(List<ParsedTaskRecord> taskStack, int indentLevel) {
        for (int i = taskStack.size() - 1; i >= 0; i--) {
            ParsedTaskRecord record = taskStack.get(i);
            if (record.indentLevel < indentLevel) {
                return record;
            }
        }
        return null;
    }

    private static void pushTaskRecord(List<ParsedTaskRecord> taskStack, ParsedTaskRecord record) {
        while (!taskStack.isEmpty()
                && taskStack.get(taskStack.size() - 1).indentLevel >= record.indentLevel) {
            taskStack.remove(taskStack.size() - 1);
        }
        taskStack.add(record);
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
        fields.seriesId = parseSeriesId(body);
        fields.reminderAt = parseReminder(body, defaultDate, format, fields);
        fields.repeatRule = parseRepeatRule(body, format, fields);
        fields.explicitRepeatUntilDoneInterval = parseRepeatUntilDoneInterval(body, format, fields);
        fields.repeatMode = parseRepeatMode(fields.repeatRule, fields.explicitRepeatUntilDoneInterval);
        fields.skipped = SKIPPED_MARKER.matcher(body).find();
        fields.tags = parseTags(body, format);
        fields.priority = parsePriority(body, format, fields);
        fields.groupExplicit = hasGroupFunction(body, format);
        fields.group = parseGroup(body, format);
        fields.snoozeDuration = parseSnooze(body, fields);
        fields.explicitOverdueGracePeriod = parseOverdueGrace(body, fields);
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

    /**
     * Same rules as internal due parsing; used by the task editor for field validation.
     */
    public static LocalDateTime tryParseDueInput(String combinedDue, LocalDate defaultDate) {
        return parseDueValue(combinedDue == null ? "" : combinedDue.trim(), defaultDate);
    }

    public static Duration tryParseDurationToken(String raw) {
        return parseDurationValue(raw);
    }

    public static Duration tryParseDurationTokenAllowZero(String raw) {
        return parseDurationValueAllowZero(raw);
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

    private static String parseSeriesId(String body) {
        String value = findFunctionValue(body, List.of(SERIES_ID_KEYWORD));
        return value == null ? "" : value.trim();
    }

    private static RepeatRule parseRepeatRule(
            String body,
            TaskFormatSettings format,
            ParsedTaskFields fields
    ) {
        String repeatValue = findFunctionValue(body, format.repeatKeywords());
        if (repeatValue == null) {
            Matcher matcher = REPEAT.matcher(body);
            if (!matcher.find()) {
                if (findFunctionValue(body, List.of(DAYS_KEYWORD)) != null
                        || findFunctionValue(body, List.of(MONTHDAY_KEYWORD)) != null) {
                    fields.repeatCombinationInvalid = true;
                }
                return null;
            }
            repeatValue = matcher.group(1) + matcher.group(2);
        }

        ParsedDurationToken durationToken = parseDurationToken(repeatValue);
        if (durationToken == null) {
            fields.repeatFunctionInvalid = true;
            return null;
        }

        String daysValue = findFunctionValue(body, List.of(DAYS_KEYWORD));
        String monthDayValue = findFunctionValue(body, List.of(MONTHDAY_KEYWORD));
        Set<DayOfWeek> days = Collections.emptySet();
        if (daysValue != null) {
            days = RepeatRule.parseDaysValue(daysValue);
            if (days.isEmpty()) {
                fields.daysFunctionInvalid = true;
            }
        }

        if (monthDayValue != null && !RepeatRule.isLastMonthDayValue(monthDayValue)) {
            Integer parsedDay = RepeatRule.parseMonthDayValue(monthDayValue);
            if (parsedDay == null) {
                fields.monthDayFunctionInvalid = true;
            }
        }

        RepeatRule.Unit unit = durationToken.unit;
        if (daysValue != null && unit != RepeatRule.Unit.WEEKS) {
            fields.repeatCombinationInvalid = true;
            return null;
        }
        if (monthDayValue != null && unit != RepeatRule.Unit.MONTHS) {
            fields.repeatCombinationInvalid = true;
            return null;
        }
        if (daysValue != null && monthDayValue != null) {
            fields.repeatCombinationInvalid = true;
            return null;
        }

        if (unit == RepeatRule.Unit.WEEKS && daysValue != null && !fields.daysFunctionInvalid) {
            return RepeatRule.weekly(durationToken.amount, days);
        }
        if (unit == RepeatRule.Unit.MONTHS && monthDayValue != null && !fields.monthDayFunctionInvalid) {
            return RepeatRule.isLastMonthDayValue(monthDayValue)
                    ? RepeatRule.monthlyLastDay(durationToken.amount)
                    : RepeatRule.monthly(durationToken.amount, RepeatRule.parseMonthDayValue(monthDayValue));
        }
        return RepeatRule.interval(durationToken.amount, unit);
    }

    private static Duration parseRepeatUntilDoneInterval(
            String body,
            TaskFormatSettings format,
            ParsedTaskFields fields
    ) {
        String value = findFunctionValue(body, format.repeatUntilDoneKeywords());
        if (value == null) {
            return null;
        }
        Duration repeatUntilDone = parseDurationValue(value);
        fields.repeatUntilDoneInvalid = repeatUntilDone == null;
        return repeatUntilDone;
    }

    private static Duration parseOverdueGrace(String body, ParsedTaskFields fields) {
        String graceValue = findFunctionValue(body, List.of(OVERDUE_GRACE_KEYWORD, "g"));
        if (graceValue == null) {
            return null;
        }
        Duration grace = parseDurationValueAllowZero(graceValue);
        fields.overdueGraceFunctionInvalid = grace == null;
        return grace;
    }

    private static Duration parseSnooze(String body, ParsedTaskFields fields) {
        String snoozeValue = findFunctionValue(body, List.of(SNOOZE_KEYWORD));
        if (snoozeValue == null) {
            return null;
        }
        Duration snooze = parseDurationValue(snoozeValue);
        fields.snoozeFunctionInvalid = snooze == null;
        return snooze;
    }

    private static RepeatMode parseRepeatMode(
            RepeatRule repeatRule,
            Duration repeatUntilDoneInterval
    ) {
        if (repeatRule != null) {
            return RepeatMode.ALWAYS;
        }
        if (repeatUntilDoneInterval != null) {
            return RepeatMode.UNTIL_DONE;
        }
        return RepeatMode.NONE;
    }

    private static Duration parseDurationValue(String rawValue) {
        ParsedDurationToken durationToken = parseDurationToken(rawValue);
        if (durationToken == null) {
            return null;
        }
        return durationToken.toDuration();
    }

    private static Duration parseDurationValueAllowZero(String rawValue) {
        String value = rawValue == null ? "" : rawValue.trim();
        Matcher matcher = DURATION_VALUE.matcher(value);
        if (!matcher.find()) {
            return null;
        }
        return parseDuration(matcher.group(1), matcher.group(2), true);
    }

    private static Duration parseDuration(String rawAmount, String rawUnit) {
        return parseDuration(rawAmount, rawUnit, false);
    }

    private static Duration parseDuration(String rawAmount, String rawUnit, boolean allowZero) {
        long amount;
        try {
            amount = Long.parseLong(rawAmount);
        } catch (NumberFormatException ignored) {
            return null;
        }

        if (amount < 0 || (!allowZero && amount == 0)) {
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
        if (unit.equals("w") || unit.equals("wk") || unit.equals("week")) {
            return Duration.ofDays(amount * 7L);
        }
        if (unit.equals("mo") || unit.equals("mon") || unit.equals("month")) {
            return null;
        }

        return null;
    }

    private static ParsedDurationToken parseDurationToken(String rawValue) {
        String value = rawValue == null ? "" : rawValue.trim();
        Matcher matcher = DURATION_VALUE.matcher(value);
        if (!matcher.find()) {
            return null;
        }
        int amount;
        try {
            amount = Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException exception) {
            return null;
        }
        if (amount <= 0) {
            return null;
        }
        RepeatRule.Unit unit = parseRepeatUnit(matcher.group(2));
        if (unit == null) {
            return null;
        }
        return new ParsedDurationToken(amount, unit);
    }

    private static RepeatRule.Unit parseRepeatUnit(String rawUnit) {
        String unit = rawUnit == null ? "" : rawUnit.trim().toLowerCase(Locale.ROOT);
        if (unit.equals("m") || unit.equals("min") || unit.equals("мин") || unit.equals("м")) {
            return RepeatRule.Unit.MINUTES;
        }
        if (unit.equals("h") || unit.equals("hr") || unit.equals("ч")) {
            return RepeatRule.Unit.HOURS;
        }
        if (unit.equals("d") || unit.equals("day") || unit.equals("д")) {
            return RepeatRule.Unit.DAYS;
        }
        if (unit.equals("w") || unit.equals("wk") || unit.equals("week")) {
            return RepeatRule.Unit.WEEKS;
        }
        if (unit.equals("mo") || unit.equals("mon") || unit.equals("month")) {
            return RepeatRule.Unit.MONTHS;
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

    private static boolean hasGroupFunction(String body, TaskFormatSettings format) {
        return findFunctionValue(body, format.groupKeywords()) != null;
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
                    TaskParseError.Kind.DUE_FUNCTION_INVALID,
                    format.getDueKeyword()
            ));
        } else if (fields.reminderAt == null && hasSuspiciousReminderToken(body, format)) {
            errors.add(new TaskParseError(
                    sourceName,
                    lineNumber,
                    TaskParseError.Kind.DUE_TIME_INVALID,
                    format.getDueKeyword()
            ));
        }

        if (fields.repeatFunctionInvalid
                || fields.repeatUntilDoneInvalid
                || fields.daysFunctionInvalid
                || fields.monthDayFunctionInvalid
                || fields.repeatCombinationInvalid
                || (fields.repeatRule == null
                && fields.explicitRepeatUntilDoneInterval == null
                && hasSuspiciousRepeatToken(body, format))) {
            errors.add(new TaskParseError(
                    sourceName,
                    lineNumber,
                    TaskParseError.Kind.REPEAT_RULE_INVALID,
                    format.getRepeatKeyword(),
                    format.getRepeatUntilDoneKeyword()
            ));
        }

        if (fields.priorityFunctionInvalid) {
            errors.add(new TaskParseError(
                    sourceName,
                    lineNumber,
                    TaskParseError.Kind.PRIORITY_INVALID,
                    format.getPriorityKeyword()
            ));
        }

        if (fields.overdueGraceFunctionInvalid) {
            errors.add(new TaskParseError(
                    sourceName,
                    lineNumber,
                    TaskParseError.Kind.GRACE_INVALID
            ));
        }
        if (fields.snoozeFunctionInvalid) {
            errors.add(new TaskParseError(
                    sourceName,
                    lineNumber,
                    TaskParseError.Kind.SNOOZE_INVALID
            ));
        }
        if (fields.obsidianMetadataConflict) {
            errors.add(new TaskParseError(
                    sourceName,
                    lineNumber,
                    TaskParseError.Kind.OBSIDIAN_METADATA_CONFLICT
            ));
        }
        if (fields.obsidianCustomCheckbox) {
            errors.add(new TaskParseError(
                    sourceName,
                    lineNumber,
                    TaskParseError.Kind.OBSIDIAN_CUSTOM_CHECKBOX
            ));
        }
        if (fields.obsidianInvalidObsidianDateOrTime) {
            errors.add(new TaskParseError(
                    sourceName,
                    lineNumber,
                    TaskParseError.Kind.OBSIDIAN_INVALID_DATE_OR_TIME
            ));
        }
        if (fields.obsidianRecurrenceUnsupported) {
            errors.add(new TaskParseError(
                    sourceName,
                    lineNumber,
                    TaskParseError.Kind.OBSIDIAN_RECURRENCE_UNSUPPORTED
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
                || findFunctionValue(body, format.repeatUntilDoneKeywords()) != null
                || findFunctionValue(body, List.of(DAYS_KEYWORD)) != null
                || findFunctionValue(body, List.of(MONTHDAY_KEYWORD)) != null;
    }

    private static String cleanTitle(String body, TaskFormatSettings format, boolean preserveInlineHashTags) {
        String cleaned = removeFunctions(body, format.dueKeywords());
        cleaned = removeFunctions(cleaned, format.repeatUntilDoneKeywords());
        cleaned = removeFunctions(cleaned, format.repeatKeywords());
        cleaned = removeFunctions(cleaned, List.of(DAYS_KEYWORD));
        cleaned = removeFunctions(cleaned, List.of(MONTHDAY_KEYWORD));
        cleaned = removeFunctions(cleaned, List.of(SERIES_ID_KEYWORD));
        cleaned = removeFunctions(cleaned, format.tagKeywords());
        cleaned = removeFunctions(cleaned, format.priorityKeywords());
        cleaned = removeFunctions(cleaned, format.groupKeywords());
        cleaned = removeFunctions(cleaned, List.of(SNOOZE_KEYWORD));
        cleaned = removeFunctions(cleaned, List.of(OVERDUE_GRACE_KEYWORD, "g"));
        cleaned = SNOOZED_COUNT.matcher(cleaned).replaceAll(" ");
        cleaned = SKIPPED_MARKER.matcher(cleaned).replaceAll(" ");
        cleaned = ISO_REMINDER.matcher(cleaned).replaceAll(" ");
        cleaned = RU_REMINDER.matcher(cleaned).replaceAll(" ");
        cleaned = ISO_DATE_ONLY_REMINDER.matcher(cleaned).replaceAll(" ");
        cleaned = RU_DATE_ONLY_REMINDER.matcher(cleaned).replaceAll(" ");
        cleaned = TIME_ONLY_REMINDER.matcher(cleaned).replaceAll(" ");
        cleaned = REPEAT.matcher(cleaned).replaceAll(" ");
        if (!preserveInlineHashTags) {
            cleaned = HASH_TAG.matcher(cleaned).replaceAll(" ");
        }
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
                || normalized.equals("@skipped")
                || normalized.equals("@skip")
                || normalized.startsWith("@status(")
                || startsWithFunction(normalized, format.dueKeywords())
                || startsWithFunction(normalized, format.repeatKeywords())
                || startsWithFunction(normalized, format.repeatUntilDoneKeywords())
                || startsWithFunction(normalized, List.of(DAYS_KEYWORD))
                || startsWithFunction(normalized, List.of(MONTHDAY_KEYWORD))
                || startsWithFunction(normalized, List.of(SERIES_ID_KEYWORD))
                || startsWithFunction(normalized, format.tagKeywords())
                || startsWithFunction(normalized, format.priorityKeywords())
                || startsWithFunction(normalized, format.groupKeywords())
                || startsWithFunction(normalized, List.of(SNOOZE_KEYWORD))
                || startsWithFunction(normalized, List.of(OVERDUE_GRACE_KEYWORD, "g"));
    }

    private static boolean startsWithFunction(String token, List<String> keywords) {
        for (String keyword : keywords) {
            if (token.startsWith("@" + keyword.toLowerCase(Locale.ROOT) + "(")) {
                return true;
            }
        }
        return false;
    }

    private static void integrateObsidianMetadata(
            String body,
            TaskFormatSettings format,
            ParsedTaskFields fields,
            ObsidianTasksParser.Result obs,
            boolean customCheckbox
    ) {
        if (format.getCompatibilityMode() == TaskFormatCompatibilityMode.NATIVE) {
            if (customCheckbox) {
                fields.obsidianCustomCheckbox = true;
            }
            return;
        }

        LocalDateTime obsEffective = computeObsidianEffectiveReminder(obs, format.getObsidianDefaultReminderTime());
        boolean nativeExplicitDue = findFunctionValue(body, format.dueKeywords()) != null;
        LocalDateTime nativeBaseline = fields.reminderAt;

        if (obsEffective != null && nativeBaseline != null && !sameReminder(nativeBaseline, obsEffective)) {
            fields.obsidianMetadataConflict = true;
        } else if (obsEffective != null && nativeBaseline == null) {
            fields.reminderAt = obsEffective;
        }

        if (fields.repeatRule != null
                && obs.recurrenceRawText != null
                && !obs.recurrenceRawText.trim().isEmpty()) {
            fields.obsidianMetadataConflict = true;
        } else if (fields.repeatRule == null && obs.mappedRepeatRule != null) {
            fields.repeatRule = obs.mappedRepeatRule;
            fields.repeatMode = parseRepeatMode(fields.repeatRule, fields.explicitRepeatUntilDoneInterval);
        } else if (fields.repeatRule == null
                && obs.recurrenceRawText != null
                && !obs.recurrenceRawText.trim().isEmpty()
                && obs.mappedRepeatRule == null) {
            fields.obsidianRecurrenceUnsupported = true;
        }

        boolean nativePriorityExplicit = findFunctionValue(body, format.priorityKeywords()) != null;
        if (!nativePriorityExplicit && obs.priority != null) {
            if (fields.priority == TaskPriority.NONE || fields.priorityFunctionInvalid) {
                fields.priority = obs.priority;
                fields.priorityFunctionInvalid = false;
            }
        }

        if (customCheckbox) {
            fields.obsidianCustomCheckbox = true;
        }

        if (obs.invalidDateToken || obs.invalidReminderToken) {
            fields.obsidianInvalidObsidianDateOrTime = true;
        }
    }

    private static boolean sameReminder(LocalDateTime a, LocalDateTime b) {
        return a != null && b != null && a.equals(b);
    }

    private static LocalDateTime computeObsidianEffectiveReminder(
            ObsidianTasksParser.Result obs,
            LocalTime defaultTime
    ) {
        if (obs.reminderAt != null) {
            return obs.reminderAt;
        }
        if (obs.dueDate != null) {
            LocalTime t = defaultTime != null ? defaultTime : LocalTime.of(9, 0);
            return LocalDateTime.of(obs.dueDate, t);
        }
        return null;
    }

    private static TaskSyntaxStyle detectSyntaxStyle(
            String body,
            TaskFormatSettings format,
            ObsidianTasksParser.Result obs
    ) {
        boolean nativeLike = findFunctionValue(body, format.dueKeywords()) != null
                || findFunctionValue(body, format.repeatKeywords()) != null
                || findFunctionValue(body, format.repeatUntilDoneKeywords()) != null
                || findFunctionValue(body, format.tagKeywords()) != null
                || findFunctionValue(body, format.priorityKeywords()) != null
                || findFunctionValue(body, format.groupKeywords()) != null
                || ISO_REMINDER.matcher(body).find()
                || RU_REMINDER.matcher(body).find()
                || ISO_DATE_ONLY_REMINDER.matcher(body).find()
                || RU_DATE_ONLY_REMINDER.matcher(body).find()
                || TIME_ONLY_REMINDER.matcher(body).find();
        boolean obsLike = obs.hasAnyServiceMarker();
        if (nativeLike && obsLike) {
            return TaskSyntaxStyle.MIXED;
        }
        if (nativeLike) {
            return TaskSyntaxStyle.NATIVE;
        }
        if (obsLike) {
            return TaskSyntaxStyle.OBSIDIAN_TASKS;
        }
        return TaskSyntaxStyle.UNKNOWN;
    }

    private static final class ParsedTaskFields {
        private String seriesId = "";
        private LocalDateTime reminderAt;
        private RepeatRule repeatRule;
        private Duration explicitRepeatUntilDoneInterval;
        private RepeatMode repeatMode = RepeatMode.NONE;
        private List<String> tags = new ArrayList<>();
        private TaskPriority priority = TaskPriority.NONE;
        private String group = ObsidianTask.DEFAULT_GROUP;
        private Duration snoozeDuration;
        private Duration explicitOverdueGracePeriod;
        private boolean skipped;
        private boolean groupExplicit;
        private boolean dueFunctionInvalid;
        private boolean repeatFunctionInvalid;
        private boolean repeatUntilDoneInvalid;
        private boolean daysFunctionInvalid;
        private boolean monthDayFunctionInvalid;
        private boolean repeatCombinationInvalid;
        private boolean priorityFunctionInvalid;
        private boolean snoozeFunctionInvalid;
        private boolean overdueGraceFunctionInvalid;
        private boolean obsidianMetadataConflict;
        private boolean obsidianCustomCheckbox;
        private boolean obsidianInvalidObsidianDateOrTime;
        private boolean obsidianRecurrenceUnsupported;
    }

    private static Duration legacyRepeatInterval(ParsedTaskFields fields) {
        if (fields == null) {
            return null;
        }
        if (fields.repeatRule != null) {
            return fields.repeatRule.toSimpleDuration();
        }
        return fields.explicitRepeatUntilDoneInterval;
    }

    private static RepeatMode legacyRepeatMode(ParsedTaskFields fields) {
        if (fields == null) {
            return RepeatMode.NONE;
        }
        if (fields.repeatRule != null) {
            return RepeatMode.ALWAYS;
        }
        if (fields.explicitRepeatUntilDoneInterval != null) {
            return RepeatMode.UNTIL_DONE;
        }
        return RepeatMode.NONE;
    }

    private static final class ParsedDurationToken {
        private final int amount;
        private final RepeatRule.Unit unit;

        private ParsedDurationToken(int amount, RepeatRule.Unit unit) {
            this.amount = amount;
            this.unit = unit;
        }

        private Duration toDuration() {
            if (unit == RepeatRule.Unit.MINUTES) {
                return Duration.ofMinutes(amount);
            }
            if (unit == RepeatRule.Unit.HOURS) {
                return Duration.ofHours(amount);
            }
            if (unit == RepeatRule.Unit.DAYS) {
                return Duration.ofDays(amount);
            }
            if (unit == RepeatRule.Unit.WEEKS) {
                return Duration.ofDays((long) amount * 7L);
            }
            return null;
        }
    }

    private static final class ParsedTaskRecord {
        private final ObsidianTask task;
        private final int indentLevel;

        private ParsedTaskRecord(ObsidianTask task, int indentLevel) {
            this.task = task;
            this.indentLevel = indentLevel;
        }
    }
}

