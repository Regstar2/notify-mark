package com.regstar.obsidiannotification;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class NoteScanSettings {
    public static final String DEFAULT_INCLUDE_PATTERNS = "*.md, *.markdown";
    public static final String DEFAULT_EXCLUDE_PATTERNS =
            ".obsidian/**, **/.obsidian/**, .trash/**, **/.trash/**, "
                    + "archive/**, archives/**, **/archive/**, **/archives/**, "
                    + "архив/**, **/архив/**, templates/**, **/templates/**, "
                    + "_templates/**, **/_templates/**, *.tmp, *.part, "
                    + "*.sync-conflict-*, *.conflict-*";
    public static final int DEFAULT_MAX_FILES = 500;
    public static final int MIN_MAX_FILES = 1;
    public static final int MAX_MAX_FILES = 10_000;

    private static final String PREFS_NAME = "obsidian_notification_note_scan";
    private static final String KEY_INCLUDE_PATTERNS = "include_patterns";
    private static final String KEY_EXCLUDE_PATTERNS = "exclude_patterns";
    private static final String KEY_MAX_FILES = "max_files";

    private final String includePatternsText;
    private final String excludePatternsText;
    private final int maxFiles;
    private final List<String> includePatterns;
    private final List<String> excludePatterns;

    private NoteScanSettings(
            String includePatternsText,
            String excludePatternsText,
            int maxFiles
    ) {
        this.includePatternsText = normalizePatterns(
                includePatternsText,
                DEFAULT_INCLUDE_PATTERNS
        );
        this.excludePatternsText = normalizePatterns(
                excludePatternsText,
                DEFAULT_EXCLUDE_PATTERNS
        );
        this.maxFiles = clampMaxFiles(maxFiles);
        this.includePatterns = splitPatterns(this.includePatternsText);
        this.excludePatterns = splitPatterns(this.excludePatternsText);
    }

    public static NoteScanSettings defaults() {
        return new NoteScanSettings(
                DEFAULT_INCLUDE_PATTERNS,
                DEFAULT_EXCLUDE_PATTERNS,
                DEFAULT_MAX_FILES
        );
    }

    public static NoteScanSettings fromValues(
            String includePatterns,
            String excludePatterns,
            int maxFiles
    ) {
        return new NoteScanSettings(includePatterns, excludePatterns, maxFiles);
    }

    public static NoteScanSettings load(Context context) {
        return fromValues(
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .getString(KEY_INCLUDE_PATTERNS, DEFAULT_INCLUDE_PATTERNS),
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .getString(KEY_EXCLUDE_PATTERNS, DEFAULT_EXCLUDE_PATTERNS),
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .getInt(KEY_MAX_FILES, DEFAULT_MAX_FILES)
        );
    }

    public static void save(Context context, NoteScanSettings settings) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_INCLUDE_PATTERNS, settings.getIncludePatternsText())
                .putString(KEY_EXCLUDE_PATTERNS, settings.getExcludePatternsText())
                .putInt(KEY_MAX_FILES, settings.getMaxFiles())
                .apply();
    }

    public static void reset(Context context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply();
    }

    public String getIncludePatternsText() {
        return includePatternsText;
    }

    public String getExcludePatternsText() {
        return excludePatternsText;
    }

    public int getMaxFiles() {
        return maxFiles;
    }

    public boolean shouldReadFile(String relativePath, String displayName) {
        String path = normalizePath(relativePath);
        String name = normalizePath(displayName);
        return matchesAny(includePatterns, path, name)
                && !matchesAny(excludePatterns, path, name);
    }

    public boolean shouldSkipDirectory(String relativePath) {
        String path = normalizePath(relativePath);
        String directoryPath = path.endsWith("/") ? path : path + "/";
        String name = lastPathSegment(path);
        return matchesAny(excludePatterns, directoryPath, name);
    }

    public String formatForStatus() {
        return "include: "
                + includePatternsText
                + " | exclude: "
                + excludePatternsText
                + " | max: "
                + maxFiles;
    }

    static boolean wildcardMatches(String pattern, String value) {
        String normalizedPattern = normalizePath(pattern);
        String normalizedValue = normalizePath(value);
        if (normalizedPattern.isEmpty() || normalizedValue.isEmpty()) {
            return false;
        }
        return Pattern.compile(wildcardToRegex(normalizedPattern))
                .matcher(normalizedValue)
                .matches();
    }

    private static boolean matchesAny(List<String> patterns, String path, String name) {
        for (String pattern : patterns) {
            if (wildcardMatches(pattern, path) || wildcardMatches(pattern, name)) {
                return true;
            }
        }
        return false;
    }

    private static String wildcardToRegex(String pattern) {
        StringBuilder regex = new StringBuilder("^");
        for (int i = 0; i < pattern.length(); i++) {
            char character = pattern.charAt(i);
            if (character == '*') {
                boolean doubleStar = i + 1 < pattern.length() && pattern.charAt(i + 1) == '*';
                if (doubleStar) {
                    regex.append(".*");
                    i++;
                } else {
                    regex.append("[^/]*");
                }
                continue;
            }
            if (character == '?') {
                regex.append("[^/]");
                continue;
            }
            if ("\\.[]{}()+-^$|".indexOf(character) >= 0) {
                regex.append('\\');
            }
            regex.append(character);
        }
        regex.append('$');
        return regex.toString();
    }

    private static String normalizePatterns(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return String.join(", ", splitPatterns(value));
    }

    private static List<String> splitPatterns(String rawPatterns) {
        List<String> patterns = new ArrayList<>();
        if (rawPatterns == null) {
            return patterns;
        }

        for (String rawPattern : rawPatterns.split("[,;\\n]+")) {
            String pattern = normalizePath(rawPattern);
            if (!pattern.isEmpty() && !patterns.contains(pattern)) {
                patterns.add(pattern);
            }
        }
        return patterns;
    }

    private static String normalizePath(String value) {
        if (value == null) {
            return "";
        }
        return value.trim()
                .replace('\\', '/')
                .replaceAll("/{2,}", "/")
                .toLowerCase(Locale.ROOT);
    }

    private static String lastPathSegment(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        String trimmed = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        int index = trimmed.lastIndexOf('/');
        return index >= 0 ? trimmed.substring(index + 1) : trimmed;
    }

    private static int clampMaxFiles(int maxFiles) {
        if (maxFiles < MIN_MAX_FILES) {
            return MIN_MAX_FILES;
        }
        if (maxFiles > MAX_MAX_FILES) {
            return MAX_MAX_FILES;
        }
        return maxFiles;
    }
}
