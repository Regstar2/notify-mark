package com.regstar.obsidiannotification.core.tasks;

import com.regstar.obsidiannotification.core.reminders.*;
import com.regstar.obsidiannotification.core.source.*;
import com.regstar.obsidiannotification.prefs.*;
import com.regstar.obsidiannotification.support.ErrorLog;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Centralized markdown writer for task lines so series advance does not duplicate string surgery.
 */
public final class TaskMarkdownWriter {
    private static final String DUE_KEYWORD = "due";
    private static final String REPEAT_UNTIL_DONE_KEYWORD = "repeatUntilDone";
    private static final String GRACE_KEYWORD = "grace";
    private static final String SNOOZE_KEYWORD = "snooze";
    private static final String ID_KEYWORD = "id";
    private static final Pattern ACTIVE_TASK_MARKER =
            Pattern.compile("^(\\s*(?:(?:\\d+)\\.\\s+|[-*+]\\s+)\\[)[ xX](\\].*)$");
    private static final Pattern DONE_TASK_MARKER =
            Pattern.compile("^(\\s*(?:(?:\\d+)\\.\\s+|[-*+]\\s+)\\[)[xX](\\].*)$");
    private static final Pattern NON_CHECKBOX_BULLET_MARKER =
            Pattern.compile("^(\\s*(?:(?:\\d+)\\.\\s+|[-*+]\\s+))(?!\\[[^\\]]+\\]\\s+)(.+)$");
    private static final Pattern SKIPPED_MARKER =
            Pattern.compile("(?iu)(?:\\s+@skipped\\b|\\s+@skip\\b|\\s+@status\\(\\s*skipped\\s*\\))");
    private static final Pattern SNOOZED_COUNT =
            Pattern.compile("(?iu)\\s+@snoozed\\(\\s*\\d+\\s*\\)");
    private static final DateTimeFormatter ISO_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private TaskMarkdownWriter() {
    }

    public static String rewriteSeriesHeadLine(
            ObsidianTask task,
            LocalDateTime nextDue,
            TaskFormatSettings formatSettings
    ) {
        return rewriteSeriesHeadLine(task, nextDue, formatSettings, null);
    }

    public static String rewriteSeriesHeadLine(
            ObsidianTask task,
            LocalDateTime nextDue,
            TaskFormatSettings formatSettings,
            String seriesIdOverride
    ) {
        String line = task.getRawLine();
        String updated = markActive(line);
        updated = removeMetadataMarker(updated, SKIPPED_MARKER);
        updated = removeMetadataMarker(updated, SNOOZED_COUNT);
        if (ObsidianTasksWriteBack.lineLooksLikeObsidianTasks(updated)) {
            updated = ObsidianTasksWriteBack.rewriteObsidianDueDate(updated, nextDue.toLocalDate());
        }
        updated = replaceFunctionOrAppend(updated, formatSettings.dueKeywords(), formatDue(nextDue));
        String seriesId = seriesIdOverride == null || seriesIdOverride.trim().isEmpty()
                ? (task.hasStableSeriesId() ? task.getSeriesId() : ObsidianTask.newSeriesId())
                : seriesIdOverride.trim();
        updated = replaceFunctionOrAppend(updated, List.of(ID_KEYWORD), seriesId);
        return normalizeSpacing(updated);
    }

    public static String ensureSeriesId(String line, String preferredSeriesId) {
        return replaceFunctionOrAppend(
                line,
                List.of(ID_KEYWORD),
                preferredSeriesId == null || preferredSeriesId.trim().isEmpty()
                        ? ObsidianTask.newSeriesId()
                        : preferredSeriesId.trim()
        );
    }

    public static String resetOccurrenceLine(String line) {
        String updated = markActive(line);
        updated = removeMetadataMarker(updated, SKIPPED_MARKER);
        updated = removeMetadataMarker(updated, SNOOZED_COUNT);
        return normalizeSpacing(updated);
    }

    public static String buildFunction(String keyword, String value) {
        return "@" + keyword + "(" + value + ")";
    }

    public static String formatDuration(Duration duration) {
        if (duration == null || duration.isNegative() || duration.isZero()) {
            return "";
        }
        long minutes = duration.toMinutes();
        if (minutes % (24L * 60L) == 0L) {
            return (minutes / (24L * 60L)) + "d";
        }
        if (minutes % 60L == 0L) {
            return (minutes / 60L) + "h";
        }
        return minutes + "m";
    }

    public static String buildRepeatUntilDone(
            Duration explicitValue,
            Duration resolvedValue,
            boolean compactSyntax,
            boolean writeValue
    ) {
        if (!writeValue) {
            return "";
        }
        Duration duration = explicitValue != null ? explicitValue : resolvedValue;
        if (duration == null || duration.isZero() || duration.isNegative()) {
            return "";
        }
        return " @" + (compactSyntax ? "rud" : REPEAT_UNTIL_DONE_KEYWORD)
                + "(" + formatDuration(duration) + ")";
    }

    public static String buildGrace(
            Duration explicitValue,
            Duration resolvedValue,
            boolean compactSyntax,
            boolean writeValue
    ) {
        if (!writeValue) {
            return "";
        }
        Duration duration = explicitValue != null ? explicitValue : resolvedValue;
        if (duration == null || duration.isNegative()) {
            return "";
        }
        return " @" + (compactSyntax ? "g" : GRACE_KEYWORD)
                + "(" + formatDuration(duration) + ")";
    }

    public static String buildSnooze(Duration duration) {
        if (duration == null || duration.isNegative() || duration.isZero()) {
            return "";
        }
        return " @" + SNOOZE_KEYWORD + "(" + formatDuration(duration) + ")";
    }

    private static String formatDue(LocalDateTime dueAt) {
        return dueAt == null ? "" : ISO_DATE_TIME.format(dueAt);
    }

    private static String replaceFunctionOrAppend(String line, List<String> keywords, String value) {
        if (value == null || value.trim().isEmpty()) {
            return line;
        }
        Pattern pattern = Pattern.compile("(?iu)@(?:" + keywordAlternatives(keywords) + ")\\(\\s*[^)]*\\s*\\)");
        Matcher matcher = pattern.matcher(line);
        String replacement = buildFunction(keywords.get(0), value.trim());
        if (matcher.find()) {
            return matcher.replaceFirst(replacement);
        }
        return line + " " + replacement;
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
        return builder.length() == 0 ? Pattern.quote(DUE_KEYWORD) : builder.toString();
    }

    private static String removeMetadataMarker(String line, Pattern pattern) {
        String updated = pattern.matcher(line).replaceAll("");
        return normalizeSpacing(updated);
    }

    private static String markActive(String line) {
        Matcher activeMatcher = ACTIVE_TASK_MARKER.matcher(line);
        if (activeMatcher.find()) {
            return activeMatcher.group(1) + " " + activeMatcher.group(2);
        }
        Matcher doneMatcher = DONE_TASK_MARKER.matcher(line);
        if (doneMatcher.find()) {
            return doneMatcher.group(1) + " " + doneMatcher.group(2);
        }
        Matcher bulletMatcher = NON_CHECKBOX_BULLET_MARKER.matcher(line);
        if (bulletMatcher.find()) {
            return bulletMatcher.group(1) + "[ ] " + bulletMatcher.group(2);
        }
        return "- [ ] " + (line == null ? "" : line.trim());
    }

    private static String normalizeSpacing(String line) {
        return (line == null ? "" : line)
                .replaceAll("\\s{2,}", " ")
                .replaceAll("\\s+$", "")
                .trim();
    }
}
