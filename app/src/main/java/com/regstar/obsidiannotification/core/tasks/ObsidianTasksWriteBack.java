package com.regstar.obsidiannotification.core.tasks;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Write-back helpers for Obsidian Tasks emoji task lines.
 */
public final class ObsidianTasksWriteBack {
    private static final DateTimeFormatter ISO_DAY = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final String E_DUE = "\uD83D\uDCC5";
    private static final String E_DONE = "\u2705";
    private static final String E_CANCEL = "\u274C";

    private static final Pattern BULLET_CHECKBOX =
            Pattern.compile("^(\\s*(?:(?:\\d+)\\.\\s+|[-*+]\\s+))\\[([^\\]]+)\\](\\s+.*)?$");
    private static final Pattern DONE_DATE =
            Pattern.compile(Pattern.quote(E_DONE) + "\\s+(\\d{4}-\\d{2}-\\d{2})");
    private static final Pattern CANCEL_DATE =
            Pattern.compile(Pattern.quote(E_CANCEL) + "\\s+(\\d{4}-\\d{2}-\\d{2})");

    private ObsidianTasksWriteBack() {
    }

    public static boolean shouldPreferObsidianWrite(
            TaskFormatCompatibilityMode mode,
            TaskLineMetadata metadata,
            String rawLine
    ) {
        if (mode == TaskFormatCompatibilityMode.NATIVE) {
            return false;
        }
        if (metadata != null && metadata.getSyntaxStyle() == TaskSyntaxStyle.NATIVE) {
            return false;
        }
        if (metadata != null
                && (metadata.getSyntaxStyle() == TaskSyntaxStyle.OBSIDIAN_TASKS
                || metadata.getSyntaxStyle() == TaskSyntaxStyle.MIXED)) {
            return true;
        }
        return lineLooksLikeObsidianTasks(rawLine);
    }

    static boolean lineLooksLikeObsidianTasks(String rawLine) {
        if (rawLine == null) {
            return false;
        }
        return rawLine.contains(E_DUE)
                || rawLine.contains("\u23F0")
                || rawLine.contains("\uD83D\uDD01")
                || rawLine.contains(E_DONE)
                || rawLine.contains(E_CANCEL)
                || rawLine.contains("\u2795")
                || rawLine.contains("\uD83D\uDEEB")
                || rawLine.contains("\u23F3")
                || rawLine.contains("\uD83C\uDD94")
                || rawLine.contains("\u26D4");
    }

    /**
     * Marks an Obsidian Tasks line complete: checkbox {@code [x]}, ensures a single {@code ✅ YYYY-MM-DD}.
     */
    public static String applyComplete(String line, LocalDate completionDay) {
        if (line == null || completionDay == null) {
            return line;
        }
        String day = ISO_DAY.format(completionDay);
        Matcher m = BULLET_CHECKBOX.matcher(line);
        if (!m.find()) {
            return line;
        }
        String prefix = m.group(1);
        String rest = m.group(3) == null ? "" : m.group(3);
        String updated = prefix + "[x]" + rest;
        updated = upsertDoneDate(updated, day);
        return normalizeSpaces(updated);
    }

    /**
     * Marks skip/cancel using {@code [-]} and {@code ❌ YYYY-MM-DD} when line already uses Obsidian Tasks markers.
     */
    public static String applyCancelled(String line, LocalDate cancelDay) {
        if (line == null || cancelDay == null) {
            return line;
        }
        String day = ISO_DAY.format(cancelDay);
        Matcher m = BULLET_CHECKBOX.matcher(line);
        if (!m.find()) {
            return line;
        }
        String prefix = m.group(1);
        String rest = m.group(3) == null ? "" : m.group(3);
        String updated = prefix + "[-]" + rest;
        updated = upsertCancelDate(updated, day);
        return normalizeSpaces(updated);
    }

    public static String rewriteObsidianDueDate(String line, LocalDate newDue) {
        if (line == null || newDue == null) {
            return line;
        }
        String day = ISO_DAY.format(newDue);
        Pattern p = Pattern.compile(Pattern.quote(E_DUE) + "\\s+\\d{4}-\\d{2}-\\d{2}");
        Matcher m = p.matcher(line);
        if (m.find()) {
            return normalizeSpaces(m.replaceFirst(E_DUE + " " + day));
        }
        return line;
    }

    private static String upsertDoneDate(String line, String day) {
        Matcher existing = DONE_DATE.matcher(line);
        if (existing.find()) {
            return DONE_DATE.matcher(line).replaceFirst(E_DONE + " " + day);
        }
        return line + " " + E_DONE + " " + day;
    }

    private static String upsertCancelDate(String line, String day) {
        Matcher existing = CANCEL_DATE.matcher(line);
        if (existing.find()) {
            return CANCEL_DATE.matcher(line).replaceFirst(E_CANCEL + " " + day);
        }
        return line + " " + E_CANCEL + " " + day;
    }

    private static String normalizeSpaces(String line) {
        return line.replaceAll("\\s{2,}", " ").replaceAll("\\s+$", "").trim();
    }
}
