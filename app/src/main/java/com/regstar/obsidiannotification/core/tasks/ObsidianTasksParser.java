package com.regstar.obsidiannotification.core.tasks;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts Obsidian Tasks emoji metadata from a task body (checkbox text after {@code [ ]}).
 */
public final class ObsidianTasksParser {
    private static final DateTimeFormatter ISO_DATE =
            DateTimeFormatter.ISO_LOCAL_DATE.withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("H:mm").withResolverStyle(ResolverStyle.STRICT);

    private static final String E_CREATED = "\u2795";
    private static final String E_START = "\uD83D\uDEEB";
    private static final String E_SCHEDULED = "\u23F3";
    private static final String E_DUE = "\uD83D\uDCC5";
    private static final String E_DONE = "\u2705";
    private static final String E_CANCELLED = "\u274C";
    private static final String E_REMINDER = "\u23F0";
    private static final String E_REPEAT = "\uD83D\uDD01";
    private static final String E_ID = "\uD83C\uDD94";
    private static final String E_DEP = "\u26D4";

    /** Service markers that begin a dated or textual value segment (excludes {@code 🔁} and priority emoji). */
    private static final Pattern VALUE_SEGMENT_HEAD =
            Pattern.compile("(" + Pattern.quote(E_CREATED)
                    + "|" + Pattern.quote(E_START)
                    + "|" + Pattern.quote(E_SCHEDULED)
                    + "|" + Pattern.quote(E_DUE)
                    + "|" + Pattern.quote(E_DONE)
                    + "|" + Pattern.quote(E_CANCELLED)
                    + "|" + Pattern.quote(E_REMINDER)
                    + "|" + Pattern.quote(E_ID)
                    + "|" + Pattern.quote(E_DEP)
                    + ")\\s+");

    private static final Pattern PRIORITY_ANYWHERE = Pattern.compile(
            "(\\s+|^)("
                    + "\\uD83D\\uDD3A|\\u23EB|\\uD83D\\uDD3C|\\uD83D\\uDD3D|\\u23EC"
                    + ")(?=\\s|$)"
    );

    private static final Pattern REPEAT_SEGMENT =
            Pattern.compile(Pattern.quote(E_REPEAT) + "\\s+(.+?)(?=(?:\\s+(?:"
                    + Pattern.quote(E_CREATED) + "|"
                    + Pattern.quote(E_START) + "|"
                    + Pattern.quote(E_SCHEDULED) + "|"
                    + Pattern.quote(E_DUE) + "|"
                    + Pattern.quote(E_DONE) + "|"
                    + Pattern.quote(E_CANCELLED) + "|"
                    + Pattern.quote(E_REMINDER) + "|"
                    + Pattern.quote(E_ID) + "|"
                    + Pattern.quote(E_DEP) + "|"
                    + "\\uD83D\\uDD3A|\\u23EB|\\uD83D\\uDD3C|\\uD83D\\uDD3D|\\u23EC"
                    + ")\\s)|$)"
    );

    private ObsidianTasksParser() {
    }

    /**
     * Returns true if the line body may contain Obsidian Tasks service emoji metadata.
     */
    public static boolean containsObsidianTasksServiceMarkers(String body) {
        if (body == null || body.isEmpty()) {
            return false;
        }
        return body.contains(E_CREATED)
                || body.contains(E_START)
                || body.contains(E_SCHEDULED)
                || body.contains(E_DUE)
                || body.contains(E_DONE)
                || body.contains(E_CANCELLED)
                || body.contains(E_REMINDER)
                || body.contains(E_REPEAT)
                || body.contains(E_ID)
                || body.contains(E_DEP)
                || PRIORITY_ANYWHERE.matcher(body).find();
    }

    public static Result parse(
            String body,
            LocalDate anchorDate,
            LocalTime defaultReminderTime,
            TaskFormatCompatibilityMode mode
    ) {
        Result result = new Result();
        if (body == null || body.isEmpty()) {
            return result;
        }
        if (mode == TaskFormatCompatibilityMode.NATIVE) {
            return result;
        }

        LocalDate anchorForTimeOnly = anchorDate;
        Matcher dueFirst = Pattern.compile(Pattern.quote(E_DUE) + "\\s+(\\d{4}-\\d{2}-\\d{2})").matcher(body);
        if (dueFirst.find()) {
            LocalDate parsedDue = parseIsoDate(dueFirst.group(1));
            if (parsedDue != null) {
                anchorForTimeOnly = parsedDue;
            }
        }

        scanDatesAndReminder(body, anchorForTimeOnly, defaultReminderTime, result);
        scanRepeat(body, result);
        scanPriority(body, result);
        scanIdAndDeps(body, result);
        return result;
    }

    /**
     * Removes recognized Obsidian Tasks service metadata spans (emoji + associated values) from
     * a task line body for display and editor title. Does not modify the stored raw markdown line.
     *
     * @param anchorDate        anchor for {@code ⏰ HH:mm} fragments (same as parse)
     */
    public static String stripServiceMetadataForDisplay(
            String body,
            LocalDate anchorDate,
            TaskFormatCompatibilityMode mode
    ) {
        if (body == null || body.isEmpty()) {
            return "";
        }
        if (mode == TaskFormatCompatibilityMode.NATIVE) {
            return body;
        }
        if (!containsObsidianTasksServiceMarkers(body)) {
            return body;
        }
        List<int[]> spans = new ArrayList<>();

        Matcher repeatMatcher = REPEAT_SEGMENT.matcher(body);
        while (repeatMatcher.find()) {
            spans.add(new int[]{repeatMatcher.start(), repeatMatcher.end()});
        }

        Matcher m = VALUE_SEGMENT_HEAD.matcher(body);
        while (m.find()) {
            String emoji = m.group(1);
            int spanStart = m.start();
            int valueStart = m.end();
            int spanEnd;
            if (E_REMINDER.equals(emoji) || E_ID.equals(emoji) || E_DEP.equals(emoji)) {
                spanEnd = boundaryBeforeNextServiceOrRepeat(body, valueStart);
            } else {
                if (valueStart >= body.length() || !Character.isDigit(body.charAt(valueStart))) {
                    spanEnd = valueStart;
                } else {
                    spanEnd = endOfSpaceDelimitedToken(body, valueStart);
                }
            }
            if (spanEnd < spanStart) {
                spanEnd = spanStart;
            }
            spans.add(new int[]{spanStart, spanEnd});
        }

        Matcher pm = PRIORITY_ANYWHERE.matcher(body);
        while (pm.find()) {
            spans.add(new int[]{pm.start(), pm.end()});
        }

        List<int[]> merged = mergeSpans(spans);
        if (merged.isEmpty()) {
            return body.trim();
        }
        StringBuilder out = new StringBuilder(body.length());
        int cursor = 0;
        for (int[] span : merged) {
            if (span[0] > cursor) {
                out.append(body, cursor, span[0]);
            }
            cursor = Math.max(cursor, span[1]);
        }
        if (cursor < body.length()) {
            out.append(body, cursor, body.length());
        }
        return out.toString().replaceAll("\\s{2,}", " ").trim();
    }

    private static List<int[]> mergeSpans(List<int[]> spans) {
        if (spans.isEmpty()) {
            return spans;
        }
        Collections.sort(spans, Comparator.comparingInt(a -> a[0]));
        List<int[]> out = new ArrayList<>();
        int cs = spans.get(0)[0];
        int ce = spans.get(0)[1];
        for (int i = 1; i < spans.size(); i++) {
            int s = spans.get(i)[0];
            int e = spans.get(i)[1];
            if (s <= ce) {
                ce = Math.max(ce, e);
            } else {
                out.add(new int[]{cs, ce});
                cs = s;
                ce = e;
            }
        }
        out.add(new int[]{cs, ce});
        return out;
    }

    private static int boundaryBeforeNextServiceOrRepeat(String body, int from) {
        int end = body.length();
        Matcher next = VALUE_SEGMENT_HEAD.matcher(body);
        if (next.find(from)) {
            end = next.start();
        }
        Matcher repeatNext = Pattern.compile(Pattern.quote(E_REPEAT)).matcher(body);
        if (repeatNext.find(from) && repeatNext.start() < end) {
            end = repeatNext.start();
        }
        return end;
    }

    private static int endOfSpaceDelimitedToken(String body, int start) {
        int end = start;
        while (end < body.length() && !Character.isWhitespace(body.charAt(end))) {
            end++;
        }
        return end;
    }

    private static void scanDatesAndReminder(
            String body,
            LocalDate anchorDate,
            LocalTime defaultReminderTime,
            Result r
    ) {
        Matcher m = VALUE_SEGMENT_HEAD.matcher(body);
        while (m.find()) {
            String emoji = m.group(1);
            int valueStart = m.end();
            if (emoji.equals(E_REMINDER)) {
                ReminderParse rp = parseReminderValue(body, valueStart, anchorDate, defaultReminderTime);
                if (rp.dateTime != null) {
                    r.reminderAt = rp.dateTime;
                }
                if (rp.invalid) {
                    r.invalidReminderToken = true;
                }
                continue;
            }
            LocalDate d = parseIsoDateFollowing(body, valueStart);
            if (emoji.equals(E_CREATED)) {
                r.createdDate = d;
            } else if (emoji.equals(E_START)) {
                r.startDate = d;
            } else if (emoji.equals(E_SCHEDULED)) {
                r.scheduledDate = d;
            } else if (emoji.equals(E_DUE)) {
                r.dueDate = d;
            } else if (emoji.equals(E_DONE)) {
                r.doneDate = d;
            } else if (emoji.equals(E_CANCELLED)) {
                r.cancelledDate = d;
            }
            if (d == null && startsWithDigit(body, valueStart)) {
                r.invalidDateToken = true;
            }
        }
    }

    private static boolean startsWithDigit(String body, int at) {
        return at < body.length() && Character.isDigit(body.charAt(at));
    }

    private static LocalDate parseIsoDateFollowing(String body, int start) {
        int end = start;
        while (end < body.length() && !Character.isWhitespace(body.charAt(end))) {
            end++;
        }
        if (end <= start) {
            return null;
        }
        String token = body.substring(start, end);
        try {
            return LocalDate.parse(token, ISO_DATE);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private static final class ReminderParse {
        private final LocalDateTime dateTime;
        private final boolean invalid;

        private ReminderParse(LocalDateTime dateTime, boolean invalid) {
            this.dateTime = dateTime;
            this.invalid = invalid;
        }
    }

    private static ReminderParse parseReminderValue(
            String body,
            int start,
            LocalDate anchorDate,
            LocalTime defaultReminderTime
    ) {
        int end = body.length();
        Matcher next = VALUE_SEGMENT_HEAD.matcher(body);
        if (next.find(start)) {
            end = next.start();
        }
        Matcher repeatNext = Pattern.compile(Pattern.quote(E_REPEAT)).matcher(body);
        if (repeatNext.find(start) && repeatNext.start() < end) {
            end = repeatNext.start();
        }
        String chunk = body.substring(start, end).trim();
        if (chunk.isEmpty()) {
            return new ReminderParse(null, false);
        }
        Matcher dateTime = Pattern.compile(
                "^(\\d{4}-\\d{2}-\\d{2})\\s+(\\d{1,2}:\\d{2})$"
        ).matcher(chunk);
        if (dateTime.find()) {
            LocalDateTime dt = parseDateTime(dateTime.group(1), dateTime.group(2));
            return new ReminderParse(dt, dt == null);
        }
        Matcher dateOnly = Pattern.compile("^(\\d{4}-\\d{2}-\\d{2})$").matcher(chunk);
        if (dateOnly.find()) {
            LocalDate d = parseIsoDate(dateOnly.group(1));
            if (d == null) {
                return new ReminderParse(null, true);
            }
            LocalTime t = defaultReminderTime != null ? defaultReminderTime : LocalTime.of(9, 0);
            return new ReminderParse(LocalDateTime.of(d, t), false);
        }
        Matcher timeOnly = Pattern.compile("^(\\d{1,2}:\\d{2})$").matcher(chunk);
        if (timeOnly.find()) {
            LocalTime t = parseTime(timeOnly.group(1));
            if (t == null || anchorDate == null) {
                return new ReminderParse(null, true);
            }
            return new ReminderParse(LocalDateTime.of(anchorDate, t), false);
        }
        return new ReminderParse(null, true);
    }

    private static LocalDateTime parseDateTime(String date, String time) {
        try {
            LocalDate d = LocalDate.parse(date, ISO_DATE);
            LocalTime t = LocalTime.parse(time, TIME_FMT);
            return LocalDateTime.of(d, t);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private static LocalDate parseIsoDate(String raw) {
        try {
            return LocalDate.parse(raw, ISO_DATE);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private static LocalTime parseTime(String raw) {
        try {
            return LocalTime.parse(raw, TIME_FMT);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private static void scanRepeat(String body, Result r) {
        Matcher m = REPEAT_SEGMENT.matcher(body);
        if (!m.find()) {
            return;
        }
        String raw = m.group(1) == null ? "" : m.group(1).trim();
        if (raw.isEmpty()) {
            return;
        }
        r.recurrenceRawText = raw;
        r.recurrenceWhenDone = ObsidianTasksRecurrenceMapper.isWhenDonePhrase(raw);
        boolean[] unsupported = new boolean[]{false};
        RepeatRule rule = ObsidianTasksRecurrenceMapper.mapOrNull(raw, unsupported);
        r.recurrenceUnsupported = unsupported[0] || r.recurrenceWhenDone;
        r.mappedRepeatRule = rule;
    }

    private static void scanPriority(String body, Result r) {
        int bestPos = Integer.MAX_VALUE;
        TaskPriority best = null;
        String[] emojis = new String[]{
                "\uD83D\uDD3A", "\u23EB", "\uD83D\uDD3C", "\uD83D\uDD3D", "\u23EC"
        };
        TaskPriority[] priorities = new TaskPriority[]{
                TaskPriority.URGENT,
                TaskPriority.HIGH,
                TaskPriority.MEDIUM,
                TaskPriority.LOW,
                TaskPriority.LOW
        };
        for (int i = 0; i < emojis.length; i++) {
            int pos = body.indexOf(emojis[i]);
            if (pos >= 0 && pos < bestPos) {
                bestPos = pos;
                best = priorities[i];
            }
        }
        r.priority = best;
    }

    private static void scanIdAndDeps(String body, Result r) {
        Matcher m = VALUE_SEGMENT_HEAD.matcher(body);
        while (m.find()) {
            String emoji = m.group(1);
            int valueStart = m.end();
            int end = body.length();
            Matcher next = VALUE_SEGMENT_HEAD.matcher(body);
            if (next.find(valueStart)) {
                end = next.start();
            }
            Matcher repeatNext = Pattern.compile(Pattern.quote(E_REPEAT)).matcher(body);
            if (repeatNext.find(valueStart) && repeatNext.start() < end) {
                end = repeatNext.start();
            }
            String chunk = body.substring(valueStart, end).trim();
            if (emoji.equals(E_ID) && !chunk.isEmpty()) {
                r.taskId = chunk.split("\\s+")[0];
            } else if (emoji.equals(E_DEP) && !chunk.isEmpty()) {
                for (String part : chunk.split(",")) {
                    String id = part.trim();
                    if (!id.isEmpty()) {
                        r.dependencyIds.add(id);
                    }
                }
            }
        }
    }

    public static final class Result {
        LocalDate createdDate;
        LocalDate startDate;
        LocalDate scheduledDate;
        LocalDate dueDate;
        LocalDateTime reminderAt;
        LocalDate doneDate;
        LocalDate cancelledDate;
        String recurrenceRawText = "";
        RepeatRule mappedRepeatRule;
        boolean recurrenceWhenDone;
        boolean recurrenceUnsupported;
        TaskPriority priority;
        String taskId = "";
        final List<String> dependencyIds = new ArrayList<>();
        boolean invalidDateToken;
        boolean invalidReminderToken;

        public boolean hasAnyServiceMarker() {
            return createdDate != null
                    || startDate != null
                    || scheduledDate != null
                    || dueDate != null
                    || doneDate != null
                    || cancelledDate != null
                    || reminderAt != null
                    || (recurrenceRawText != null && !recurrenceRawText.isEmpty())
                    || priority != null
                    || (taskId != null && !taskId.isEmpty())
                    || !dependencyIds.isEmpty();
        }

        public TaskLineMetadata toMetadata(
                TaskSyntaxStyle syntaxStyle,
                Character originalCheckbox,
                boolean customCheckbox,
                boolean nativeExplicitDue,
                boolean conflict
        ) {
            TaskLineMetadata.Builder b = new TaskLineMetadata.Builder();
            b.syntaxStyle(syntaxStyle);
            b.originalCheckboxChar(originalCheckbox);
            b.customCheckboxStatus(customCheckbox);
            b.createdDate(createdDate);
            b.startDate(startDate);
            b.scheduledDate(scheduledDate);
            b.dueDate(dueDate);
            b.obsidianReminderAt(reminderAt);
            b.doneDate(doneDate);
            b.cancelledDate(cancelledDate);
            b.recurrenceRawText(recurrenceRawText);
            b.mappedRepeatRule(recurrenceWhenDone ? null : mappedRepeatRule);
            b.recurrenceWhenDone(recurrenceWhenDone);
            b.recurrenceUnsupported(recurrenceUnsupported);
            b.taskId(taskId);
            for (String d : dependencyIds) {
                b.addDependencyId(d);
            }
            b.nativeExplicitDue(nativeExplicitDue);
            b.obsidianMetadataConflict(conflict);
            b.priorityFromEmoji(priority != null);
            return b.build();
        }
    }
}
