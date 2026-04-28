package com.regstar.obsidiannotification.support;

import android.content.Context;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public final class ErrorLog {
    private static final String PREFS_NAME = "obsidian_notification_error_log";
    private static final String KEY_ENTRIES = "entries";
    private static final int MAX_ENTRIES = 40;
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

    private ErrorLog() {
    }

    public static void record(Context context, String message) {
        if (message == null || message.trim().isEmpty()) {
            return;
        }

        List<String> entries = getRecent(context, MAX_ENTRIES);
        entries.add(FORMATTER.format(LocalDateTime.now()) + " - " + sanitize(message));
        while (entries.size() > MAX_ENTRIES) {
            entries.remove(0);
        }

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_ENTRIES, join(entries))
                .apply();
    }

    public static void record(Context context, String message, Throwable throwable) {
        String details = throwable == null ? "" : throwable.getMessage();
        if (details == null || details.trim().isEmpty()) {
            record(context, message);
            return;
        }

        record(context, message + ": " + details);
    }

    public static List<String> getRecent(Context context, int limit) {
        String raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_ENTRIES, "");
        List<String> entries = new ArrayList<>();
        if (raw == null || raw.isEmpty()) {
            return entries;
        }

        String[] lines = raw.split("\\n");
        int start = limit <= 0 ? 0 : Math.max(0, lines.length - limit);
        for (int i = start; i < lines.length; i++) {
            if (!lines[i].trim().isEmpty()) {
                entries.add(lines[i]);
            }
        }
        return entries;
    }

    public static String latest(Context context) {
        List<String> entries = getRecent(context, 1);
        return entries.isEmpty() ? null : entries.get(0);
    }

    public static void clear(Context context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_ENTRIES)
                .apply();
    }

    private static String sanitize(String message) {
        return message.replace('\n', ' ').replace('\r', ' ').trim();
    }

    private static String join(List<String> entries) {
        StringBuilder builder = new StringBuilder();
        for (String entry : entries) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(entry);
        }
        return builder.toString();
    }
}
