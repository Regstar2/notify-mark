package com.regstar.obsidiannotification;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.UriPermission;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class NoteStore {
    public static final String SOURCE_NOTE = "note";
    public static final String SOURCE_FOLDER = "folder";

    private static final String PREFS_NAME = "obsidian_notification_prefs";
    private static final String KEY_NOTE_URI = "note_uri";
    private static final String KEY_SOURCE_URI = "source_uri";
    private static final String KEY_SOURCE_TYPE = "source_type";
    private static final String KEY_SOURCES = "sources";
    private static final Pattern ACTIVE_TASK_MARKER =
            Pattern.compile("^(\\s*[-*+]\\s+\\[)[ xX](\\].*)$");
    private static final Pattern DONE_TASK_MARKER =
            Pattern.compile("^(\\s*[-*+]\\s+\\[)[xX](\\].*)$");
    private static final Pattern NON_CHECKBOX_BULLET_MARKER =
            Pattern.compile("^(\\s*[-*+]\\s+)(?!\\[[ xX]\\]\\s+)(.+)$");
    private static final Pattern SNOOZED_COUNT =
            Pattern.compile("(?iu)@snoozed\\(\\s*(\\d+)\\s*\\)");
    private static final Pattern SKIPPED_MARKER =
            Pattern.compile("(?iu)(?:@skipped\\b|@skip\\b|@status\\(\\s*skipped\\s*\\))");

    private NoteStore() {
    }

    public static Uri getSavedNoteUri(Context context) {
        if (!SOURCE_NOTE.equals(getSavedSourceType(context))) {
            return null;
        }
        return getSavedSourceUri(context);
    }

    public static Uri getSavedSourceUri(Context context) {
        List<NoteSource> sources = getSavedSources(context);
        return sources.isEmpty() ? null : sources.get(0).getUri();
    }

    static Uri getStoredExternalPrimaryUri(Context context) {
        return getSavedSourceUri(context);
    }

    public static String getSavedSourceType(Context context) {
        List<NoteSource> sources = getSavedSources(context);
        return sources.isEmpty() ? SOURCE_NOTE : sources.get(0).getType();
    }

    public static boolean hasSavedSources(Context context) {
        return !getSavedSources(context).isEmpty();
    }

    static boolean hasStoredExternalSources(Context context) {
        return hasSavedSources(context);
    }

    public static int getSavedSourceCount(Context context) {
        return getSavedSources(context).size();
    }

    static int getStoredExternalSourceCount(Context context) {
        return getSavedSourceCount(context);
    }

    public static List<NoteSource> getSavedSources(Context context) {
        SharedPreferences preferences =
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        List<NoteSource> sources = decodeSources(preferences.getString(KEY_SOURCES, null));
        if (!sources.isEmpty()) {
            return sources;
        }

        Uri legacyUri = getLegacySavedSourceUri(preferences);
        if (legacyUri == null) {
            return new ArrayList<>();
        }
        String legacyType = preferences.getString(KEY_SOURCE_TYPE, SOURCE_NOTE);
        List<NoteSource> legacySources = new ArrayList<>();
        legacySources.add(new NoteSource(legacyType, legacyUri));
        return legacySources;
    }

    static List<NoteSource> getStoredExternalSources(Context context) {
        return getSavedSources(context);
    }

    public static Uri requireSavedSourceUri(Context context) throws IOException {
        Uri sourceUri = getSavedSourceUri(context);
        if (sourceUri == null) {
            throw new IOException("заметка или папка не выбрана");
        }
        return sourceUri;
    }

    public static Uri requireSavedNoteUri(Context context) throws IOException {
        Uri noteUri = getSavedNoteUri(context);
        if (noteUri == null) {
            throw new IOException("файл заметки не выбран");
        }
        return noteUri;
    }

    public static void saveNoteUri(Context context, Uri uri) {
        List<NoteSource> sources = new ArrayList<>();
        sources.add(new NoteSource(SOURCE_NOTE, uri));
        saveSources(context, sources);
    }

    public static void saveFolderUri(Context context, Uri uri) {
        List<NoteSource> sources = new ArrayList<>();
        sources.add(new NoteSource(SOURCE_FOLDER, uri));
        saveSources(context, sources);
    }

    public static void saveNoteUris(Context context, List<Uri> uris) {
        List<NoteSource> sources = new ArrayList<>();
        for (Uri uri : uris) {
            sources.add(new NoteSource(SOURCE_NOTE, uri));
        }
        saveSources(context, sources);
    }

    public static void addNoteUris(Context context, List<Uri> uris) {
        List<NoteSource> sources = getSavedSources(context);
        for (Uri uri : uris) {
            sources.add(new NoteSource(SOURCE_NOTE, uri));
        }
        saveSources(context, sources);
    }

    public static void addFolderUri(Context context, Uri uri) {
        List<NoteSource> sources = getSavedSources(context);
        sources.add(new NoteSource(SOURCE_FOLDER, uri));
        saveSources(context, sources);
    }

    public static void clearSources(Context context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_SOURCES)
                .remove(KEY_SOURCE_URI)
                .remove(KEY_SOURCE_TYPE)
                .remove(KEY_NOTE_URI)
                .apply();
    }

    public static boolean canWriteSavedSource(Context context) {
        return canWriteStoredExternalSources(context);
    }

    static boolean canWriteStoredExternalSources(Context context) {
        List<NoteSource> sources = getSavedSources(context);
        if (sources.isEmpty()) {
            return false;
        }

        for (NoteSource source : sources) {
            if (!canWriteUri(context, source.getUri())) {
                return false;
            }
        }
        return true;
    }

    public static String sourceDisplayName(Context context, Uri uri) {
        return displayName(context, uri);
    }

    public static boolean canWriteUri(Context context, Uri uri) {
        if (uri == null) {
            return false;
        }
        if ("file".equalsIgnoreCase(uri.getScheme())) {
            return true;
        }
        for (UriPermission permission : context.getContentResolver().getPersistedUriPermissions()) {
            if (!permission.isWritePermission()) {
                continue;
            }
            if (permission.getUri().equals(uri)
                    || uri.toString().startsWith(permission.getUri().toString())) {
                return true;
            }
        }
        return false;
    }

    private static void saveSources(Context context, List<NoteSource> rawSources) {
        List<NoteSource> sources = dedupeSources(rawSources);
        SharedPreferences.Editor editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_SOURCES, encodeSources(sources));

        if (sources.isEmpty()) {
            editor.remove(KEY_SOURCE_TYPE)
                    .remove(KEY_SOURCE_URI)
                    .remove(KEY_NOTE_URI);
        } else {
            NoteSource firstSource = sources.get(0);
            editor.putString(KEY_SOURCE_TYPE, firstSource.getType())
                    .putString(KEY_SOURCE_URI, firstSource.getUri().toString());
            if (SOURCE_NOTE.equals(firstSource.getType())) {
                editor.putString(KEY_NOTE_URI, firstSource.getUri().toString());
            } else {
                editor.remove(KEY_NOTE_URI);
            }
        }
        editor.apply();
    }

    private static List<NoteSource> dedupeSources(List<NoteSource> rawSources) {
        Map<String, NoteSource> uniqueSources = new LinkedHashMap<>();
        if (rawSources == null) {
            return new ArrayList<>();
        }

        for (NoteSource source : rawSources) {
            if (source == null || source.getUri() == null) {
                continue;
            }
            uniqueSources.put(source.getType() + "|" + source.getUri(), source);
        }
        return new ArrayList<>(uniqueSources.values());
    }

    private static String encodeSources(List<NoteSource> sources) {
        StringBuilder builder = new StringBuilder();
        for (NoteSource source : sources) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(source.getType())
                    .append('|')
                    .append(Base64.getUrlEncoder().withoutPadding().encodeToString(
                            source.getUri().toString().getBytes(StandardCharsets.UTF_8)
                    ));
        }
        return builder.toString();
    }

    private static List<NoteSource> decodeSources(String encodedSources) {
        List<NoteSource> sources = new ArrayList<>();
        if (encodedSources == null || encodedSources.trim().isEmpty()) {
            return sources;
        }

        for (String line : encodedSources.split("\\n")) {
            String[] parts = line.split("\\|", 2);
            if (parts.length != 2) {
                continue;
            }
            try {
                String type = normalizeSourceType(parts[0]);
                String uri = new String(
                        Base64.getUrlDecoder().decode(parts[1]),
                        StandardCharsets.UTF_8
                );
                sources.add(new NoteSource(type, Uri.parse(uri)));
            } catch (RuntimeException ignored) {
                // Ignore malformed stored source entries.
            }
        }
        return dedupeSources(sources);
    }

    private static Uri getLegacySavedSourceUri(SharedPreferences preferences) {
        String savedUri = preferences.getString(KEY_SOURCE_URI, null);
        if (savedUri == null) {
            savedUri = preferences.getString(KEY_NOTE_URI, null);
        }
        return savedUri == null ? null : Uri.parse(savedUri);
    }

    private static String normalizeSourceType(String sourceType) {
        return SOURCE_FOLDER.equals(sourceType) ? SOURCE_FOLDER : SOURCE_NOTE;
    }

    public static String readMarkdown(Context context, Uri uri) throws IOException {
        if ("file".equalsIgnoreCase(uri.getScheme())) {
            return new String(Files.readAllBytes(Paths.get(uri.getPath())), StandardCharsets.UTF_8);
        }
        StringBuilder builder = new StringBuilder();
        try (InputStream stream = context.getContentResolver().openInputStream(uri)) {
            if (stream == null) {
                throw new IOException("провайдер не вернул поток данных");
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    stream,
                    StandardCharsets.UTF_8
            ))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    builder.append(line).append('\n');
                }
            }
        }
        return builder.toString();
    }

    public static void writeMarkdown(Context context, Uri uri, String markdown) throws IOException {
        if ("file".equalsIgnoreCase(uri.getScheme())) {
            Files.write(Paths.get(uri.getPath()), markdown.getBytes(StandardCharsets.UTF_8));
            return;
        }

        try (OutputStream stream = context.getContentResolver().openOutputStream(uri, "wt")) {
            if (stream == null) {
                throw new IOException("провайдер не вернул поток записи");
            }
            stream.write(markdown.getBytes(StandardCharsets.UTF_8));
        } catch (SecurityException exception) {
            throw new IOException("нет доступа на запись. Выберите заметку или папку заново", exception);
        }
    }

    public static List<NoteDocument> readDocuments(Context context) throws IOException {
        return TaskSourceManager.currentSource(context).readDocuments(context);
    }

    static List<NoteDocument> readStoredExternalDocuments(Context context) throws IOException {
        List<NoteSource> sources = getSavedSources(context);
        if (sources.isEmpty()) {
            throw new IOException("заметка или папка не выбрана");
        }

        List<NoteDocument> documents = new ArrayList<>();
        NoteScanSettings scanSettings = NoteScanSettings.load(context);
        for (NoteSource source : sources) {
            if (SOURCE_FOLDER.equals(source.getType())) {
                documents.addAll(readFolderDocuments(context, source.getUri(), scanSettings));
            } else {
                documents.add(new NoteDocument(
                        displayName(context, source.getUri()),
                        source.getUri(),
                        readMarkdown(context, source.getUri())
                ));
            }
            if (documents.size() >= scanSettings.getMaxFiles()) {
                break;
            }
        }

        if (documents.isEmpty()) {
            throw new IOException("не найдено markdown-файлов по текущим источникам и шаблонам поиска");
        }
        return documents;
    }

    public static TaskParseResult readTaskParseResult(Context context) throws IOException {
        return readTaskSnapshot(context).getParseResult();
    }

    public static TaskSnapshot readTaskSnapshot(Context context) throws IOException {
        List<TaskParseResult> results = new ArrayList<>();
        int documentCount = 0;
        int totalCharacters = 0;
        TaskFormatSettings formatSettings = TaskFormatSettings.load(context);
        for (NoteDocument document : readDocuments(context)) {
            documentCount++;
            totalCharacters += document.getMarkdown().length();
            results.add(parseDocument(context, document, formatSettings));
        }
        return new TaskSnapshot(TaskParseResult.merge(results), documentCount, totalCharacters);
    }

    public static List<ObsidianTask> readTasks(Context context) throws IOException {
        return readTaskParseResult(context).getActiveTasks();
    }

    private static TaskParseResult parseDocument(
            Context context,
            NoteDocument document,
            TaskFormatSettings formatSettings
    ) {
        TaskParseResult rawResult = TaskParser.parseDocument(
                document.getMarkdown(),
                java.time.LocalDate.now(),
                document.getDisplayName(),
                formatSettings
        );
        return rawResult.withTasks(TaskDefaultsResolver.resolve(context, rawResult.getTasks()));
    }

    public static ObsidianTask findActiveTask(Context context, String taskKey) throws IOException {
        for (ObsidianTask task : readTasks(context)) {
            if (task.getTaskKey().equals(taskKey)) {
                return task;
            }
        }
        return null;
    }

    public static TaskDocumentMatch findTaskDocument(Context context, String taskKey) throws IOException {
        TaskFormatSettings formatSettings = TaskFormatSettings.load(context);
        for (NoteDocument document : readDocuments(context)) {
            TaskParseResult result = parseDocument(context, document, formatSettings);
            for (ObsidianTask task : result.getTasks()) {
                if (task.getTaskKey().equals(taskKey)) {
                    return new TaskDocumentMatch(document.getUri(), document.getDisplayName(), task);
                }
            }
        }
        return null;
    }

    public static TaskBlockSnapshot captureTaskBlockSnapshot(Context context, String taskKey) throws IOException {
        if (taskKey == null || taskKey.trim().isEmpty()) {
            return null;
        }

        TaskFormatSettings formatSettings = TaskFormatSettings.load(context);
        for (NoteDocument document : readDocuments(context)) {
            TaskParseResult result = parseDocument(context, document, formatSettings);
            for (ObsidianTask task : result.getTasks()) {
                if (!task.getTaskKey().equals(taskKey)) {
                    continue;
                }
                String[] lines = document.getMarkdown().split("\n", -1);
                int startIndex = task.getLineNumber() - 1;
                if (startIndex < 0 || startIndex >= lines.length) {
                    return null;
                }
                int endExclusive = taskBlockEnd(lines, startIndex);
                String block = joinLineRange(lines, startIndex, endExclusive);
                String markdownAfterDelete = replaceLineRange(lines, startIndex, endExclusive, "");
                return new TaskBlockSnapshot(
                        document.getUri(),
                        document.getDisplayName(),
                        task.getTaskKey(),
                        document.getMarkdown(),
                        markdownAfterDelete,
                        block
                );
            }
        }
        return null;
    }

    public static TaskEditResult restoreTaskBlock(Context context, TaskBlockSnapshot snapshot) {
        if (snapshot == null || snapshot.getUri() == null) {
            return TaskEditResult.notFound("снимок удаленной задачи отсутствует");
        }

        try {
            String latestMarkdown = readMarkdown(context, snapshot.getUri());
            if (sameMarkdownContent(latestMarkdown, snapshot.getOriginalMarkdown())) {
                return TaskEditResult.updated("удаление отменено");
            }
            if (!sameMarkdownContent(latestMarkdown, snapshot.getMarkdownAfterDelete())) {
                return TaskEditResult.conflict("файл изменился, откат удаления невозможен");
            }
            writeMarkdown(context, snapshot.getUri(), snapshot.getOriginalMarkdown());
            return TaskEditResult.updated("удаление отменено");
        } catch (IOException | RuntimeException exception) {
            ErrorLog.record(context, "Не удалось откатить удаление markdown-задачи", exception);
            return TaskEditResult.writeFailed(exception.getMessage());
        }
    }

    public static TaskEditResult markTaskDone(Context context, String taskKey) {
        TaskEditResult seriesResult = advanceRepeatSeriesIfNeeded(context, taskKey, OccurrenceStatus.COMPLETED);
        if (seriesResult != null) {
            return seriesResult;
        }
        return editActiveTaskLine(context, taskKey, NoteStore::markDoneLine);
    }

    public static TaskEditResult unmarkTaskDone(Context context, String taskKey) {
        return editTaskLine(
                context,
                taskKey,
                true,
                line -> {
                    String updatedLine = unmarkDoneLine(line);
                    return updatedLine == null ? null : TaskLineMutation.replace(updatedLine);
                }
        );
    }

    public static TaskEditResult incrementSnoozeCount(Context context, String taskKey) {
        return editActiveTaskLine(context, taskKey, NoteStore::incrementSnoozedMarker);
    }

    public static TaskEditResult markTaskSkipped(Context context, String taskKey) {
        TaskEditResult seriesResult = advanceRepeatSeriesIfNeeded(context, taskKey, OccurrenceStatus.SKIPPED);
        if (seriesResult != null) {
            return seriesResult;
        }
        return editActiveTaskLine(context, taskKey, NoteStore::appendSkippedMarker);
    }

    public static TaskEditResult unmarkTaskSkipped(Context context, String taskKey) {
        return editTaskLine(
                context,
                taskKey,
                true,
                line -> {
                    String updatedLine = removeSkippedMarker(line);
                    return updatedLine == null ? null : TaskLineMutation.replace(updatedLine);
                }
        );
    }

    public static NoteDocument findDefaultWriteDocument(Context context) throws IOException {
        List<NoteDocument> documents = readDocuments(context);
        if (documents.isEmpty()) {
            throw new IOException("не найден markdown-файл для записи");
        }
        return documents.get(0);
    }

    public static MarkdownDocument findMarkdownDocument(Context context, String taskKey) throws IOException {
        if (taskKey != null && !taskKey.trim().isEmpty()) {
            TaskFormatSettings formatSettings = TaskFormatSettings.load(context);
            for (NoteDocument document : readDocuments(context)) {
                TaskParseResult result = parseDocument(context, document, formatSettings);
                for (ObsidianTask task : result.getTasks()) {
                    if (task.getTaskKey().equals(taskKey)) {
                        return new MarkdownDocument(
                                document.getDisplayName(),
                                document.getUri(),
                                document.getMarkdown(),
                                task.getLineNumber()
                        );
                    }
                }
            }
            throw new IOException("задача не найдена или уже изменилась");
        }

        NoteDocument document = findDefaultWriteDocument(context);
        return new MarkdownDocument(
                document.getDisplayName(),
                document.getUri(),
                document.getMarkdown(),
                1
        );
    }

    public static TaskEditResult replaceMarkdownDocument(
            Context context,
            Uri uri,
            String originalMarkdown,
            String markdown,
            boolean force
    ) {
        if (uri == null) {
            return TaskEditResult.notFound("markdown-файл не найден");
        }

        try {
            String latestMarkdown = readMarkdown(context, uri);
            if (!force && originalMarkdown != null && !latestMarkdown.equals(originalMarkdown)) {
                return TaskEditResult.conflict("файл изменился после открытия редактора");
            }
            writeMarkdown(context, uri, markdown == null ? "" : markdown);
            return TaskEditResult.updated("markdown-файл сохранен");
        } catch (IOException | RuntimeException exception) {
            ErrorLog.record(context, "Не удалось сохранить markdown-файл", exception);
            return TaskEditResult.writeFailed(exception.getMessage());
        }
    }

    public static TaskEditResult appendTaskLine(Context context, String rawLine) {
        String safeLine = sanitizeSingleLine(rawLine);
        if (safeLine.isEmpty()) {
            return TaskEditResult.conflict("строка уведомления пустая");
        }

        try {
            NoteDocument document = findDefaultWriteDocument(context);
            String latestMarkdown = readMarkdown(context, document.getUri());
            String separator = latestMarkdown.isEmpty() || latestMarkdown.endsWith("\n")
                    ? ""
                    : "\n";
            writeMarkdown(context, document.getUri(), latestMarkdown + separator + safeLine + "\n");
            return TaskEditResult.updated("уведомление добавлено");
        } catch (IOException | RuntimeException exception) {
            ErrorLog.record(context, "Не удалось добавить markdown-уведомление", exception);
            return TaskEditResult.writeFailed(exception.getMessage());
        }
    }

    public static TaskEditResult appendTaskBlock(Context context, String rawBlock) {
        String safeBlock = sanitizeMarkdownBlock(rawBlock);
        if (safeBlock.isEmpty()) {
            return TaskEditResult.conflict("блок уведомления пустой");
        }

        try {
            NoteDocument document = findDefaultWriteDocument(context);
            String latestMarkdown = readMarkdown(context, document.getUri());
            String separator = latestMarkdown.isEmpty() || latestMarkdown.endsWith("\n")
                    ? ""
                    : "\n";
            writeMarkdown(context, document.getUri(), latestMarkdown + separator + safeBlock + "\n");
            return TaskEditResult.updated("изменения сохранены");
        } catch (IOException | RuntimeException exception) {
            ErrorLog.record(context, "Не удалось добавить markdown-блок уведомления", exception);
            return TaskEditResult.writeFailed(exception.getMessage());
        }
    }

    public static TaskEditResult replaceTaskLine(Context context, String taskKey, String rawLine) {
        String safeLine = sanitizeSingleLine(rawLine);
        if (safeLine.isEmpty()) {
            return TaskEditResult.conflict("строка уведомления пустая");
        }
        return editTaskLine(
                context,
                taskKey,
                true,
                line -> TaskLineMutation.replace(safeLine)
        );
    }

    public static TaskEditResult replaceTaskBlock(Context context, String taskKey, String rawBlock) {
        String safeBlock = sanitizeMarkdownBlock(rawBlock);
        if (safeBlock.isEmpty()) {
            return TaskEditResult.conflict("блок уведомления пустой");
        }
        return editTaskBlock(context, taskKey, safeBlock);
    }

    public static TaskEditResult deleteTaskLine(Context context, String taskKey) {
        return deleteTaskBlock(context, taskKey);
    }

    public static TaskEditResult deleteTaskBlock(Context context, String taskKey) {
        return editTaskBlock(context, taskKey, null);
    }

    public static BulkEditResult markTasksDone(Context context, List<String> taskKeys) {
        return editTaskLinesBulk(
                context,
                taskKeys,
                false,
                false,
                line -> {
                    String updatedLine = markDoneLine(line);
                    return updatedLine == null ? null : TaskLineMutation.replace(updatedLine);
                },
                "Не удалось массово отметить markdown-задачи выполненными"
        );
    }

    public static BulkEditResult markTasksSkipped(Context context, List<String> taskKeys) {
        return editTaskLinesBulk(
                context,
                taskKeys,
                false,
                false,
                line -> TaskLineMutation.replace(appendSkippedMarker(line)),
                "Не удалось массово пропустить markdown-задачи"
        );
    }

    public static BulkEditResult deleteTaskLines(Context context, List<String> taskKeys) {
        return editTaskLinesBulk(
                context,
                taskKeys,
                true,
                true,
                line -> TaskLineMutation.delete(),
                "Не удалось массово удалить markdown-задачи"
        );
    }

    public static BulkEditResult incrementSnoozeCounts(Context context, List<String> taskKeys) {
        return editTaskLinesBulk(
                context,
                taskKeys,
                false,
                false,
                line -> TaskLineMutation.replace(incrementSnoozedMarker(line)),
                "Не удалось массово записать счетчик отложений"
        );
    }

    public static String sourceLabel(Context context) {
        return TaskSourceManager.activeSourceLabel(context);
    }

    static String externalSourceLabel(Context context) {
        List<NoteSource> sources = getSavedSources(context);
        if (sources.isEmpty()) {
            return "не выбрано";
        }

        if (sources.size() == 1) {
            NoteSource source = sources.get(0);
            String type = SOURCE_FOLDER.equals(source.getType()) ? "папка" : "заметка";
            return type + ": " + displayName(context, source.getUri());
        }

        StringBuilder builder = new StringBuilder("источников: ").append(sources.size());
        int limit = Math.min(3, sources.size());
        for (int i = 0; i < limit; i++) {
            builder.append(i == 0 ? " (" : ", ");
            builder.append(displayName(context, sources.get(i).getUri()));
        }
        if (sources.size() > limit) {
            builder.append(", ...");
        }
        builder.append(')');
        return builder.toString();
    }

    private static TaskEditResult editActiveTaskLine(
            Context context,
            String taskKey,
            TaskLineEditor editor
    ) {
        if (taskKey == null || taskKey.trim().isEmpty()) {
            return TaskEditResult.notFound("ключ задачи пустой");
        }

        try {
            TaskFormatSettings formatSettings = TaskFormatSettings.load(context);
            for (NoteDocument document : readDocuments(context)) {
                TaskParseResult result = parseDocument(context, document, formatSettings);
                for (ObsidianTask task : result.getTasks()) {
                    if (!task.getTaskKey().equals(taskKey)) {
                        continue;
                    }
                    if (task.isCompleted()) {
                        return TaskEditResult.alreadyDone("задача уже выполнена");
                    }
                    if (task.isSkipped()) {
                        return TaskEditResult.alreadyDone("уведомление уже пропущено");
                    }

                    String[] lines = document.getMarkdown().split("\n", -1);
                    int index = task.getLineNumber() - 1;
                    if (index < 0 || index >= lines.length) {
                        return TaskEditResult.conflict("строка задачи изменилась");
                    }

                    String updatedLine = editor.edit(lines[index]);
                    if (updatedLine == null || updatedLine.equals(lines[index])) {
                        return TaskEditResult.conflict("строка задачи больше не похожа на активный чекбокс");
                    }

                    String latestMarkdown = readMarkdown(context, document.getUri());
                    if (!latestMarkdown.equals(document.getMarkdown())) {
                        return TaskEditResult.conflict("файл изменился во время записи");
                    }

                    lines[index] = updatedLine;
                    writeMarkdown(context, document.getUri(), joinLines(lines));
                    return TaskEditResult.updated("задача обновлена");
                }
            }
            return TaskEditResult.notFound("задача не найдена или уже изменилась");
        } catch (IOException | RuntimeException exception) {
            ErrorLog.record(context, "Не удалось обновить markdown-задачу", exception);
            return TaskEditResult.writeFailed(exception.getMessage());
        }
    }

    private static TaskEditResult advanceRepeatSeriesIfNeeded(
            Context context,
            String taskKey,
            OccurrenceStatus resolutionStatus
    ) {
        if (taskKey == null || taskKey.trim().isEmpty()) {
            return null;
        }
        try {
            TaskDocumentMatch match = findTaskDocument(context, taskKey);
            if (match == null || match.getTask() == null || !match.getTask().hasRepeatSchedule()) {
                return null;
            }
            return RepeatSeriesManager.advance(context, taskKey, resolutionStatus);
        } catch (IOException | RuntimeException exception) {
            ErrorLog.record(context, "Не удалось продвинуть repeat-серию", exception);
            return TaskEditResult.writeFailed(exception.getMessage());
        }
    }

    private static BulkEditResult editTaskLinesBulk(
            Context context,
            List<String> taskKeys,
            boolean allowCompleted,
            boolean allowSkipped,
            TaskLineMutationEditor editor,
            String errorMessage
    ) {
        Map<String, Boolean> remainingTaskKeys = new LinkedHashMap<>();
        if (taskKeys != null) {
            for (String taskKey : taskKeys) {
                if (taskKey != null && !taskKey.trim().isEmpty()) {
                    remainingTaskKeys.put(taskKey, Boolean.TRUE);
                }
            }
        }

        int totalCount = remainingTaskKeys.size();
        if (totalCount == 0) {
            return new BulkEditResult(0, 0, 0, 0, 0, 0, new ArrayList<>());
        }

        int updatedCount = 0;
        int skippedCount = 0;
        int failedCount = 0;
        int touchedFileCount = 0;
        List<String> updatedTaskKeys = new ArrayList<>();

        try {
            TaskFormatSettings formatSettings = TaskFormatSettings.load(context);
            for (NoteDocument document : readDocuments(context)) {
                if (remainingTaskKeys.isEmpty()) {
                    break;
                }

                String[] lines = document.getMarkdown().split("\n", -1);
                TaskParseResult result = parseDocument(context, document, formatSettings);
                Map<Integer, TaskLineMutation> mutations = new LinkedHashMap<>();
                List<String> documentUpdatedTaskKeys = new ArrayList<>();

                for (ObsidianTask task : result.getTasks()) {
                    String taskKey = task.getTaskKey();
                    if (!remainingTaskKeys.containsKey(taskKey)) {
                        continue;
                    }

                    remainingTaskKeys.remove(taskKey);
                    if (task.isCompleted() && !allowCompleted) {
                        skippedCount++;
                        continue;
                    }
                    if (task.isSkipped() && !allowSkipped) {
                        skippedCount++;
                        continue;
                    }

                    int index = task.getLineNumber() - 1;
                    if (index < 0 || index >= lines.length) {
                        failedCount++;
                        continue;
                    }

                    TaskLineMutation mutation = editor.edit(lines[index]);
                    if (mutation == null) {
                        failedCount++;
                        continue;
                    }
                    if (!mutation.isDelete()
                            && mutation.getLine() != null
                            && mutation.getLine().equals(lines[index])) {
                        skippedCount++;
                        continue;
                    }

                    mutations.put(index, mutation);
                    documentUpdatedTaskKeys.add(taskKey);
                }

                if (mutations.isEmpty()) {
                    continue;
                }

                try {
                    String latestMarkdown = readMarkdown(context, document.getUri());
                    if (!latestMarkdown.equals(document.getMarkdown())) {
                        failedCount += documentUpdatedTaskKeys.size();
                        continue;
                    }

                    writeMarkdown(context, document.getUri(), applyLineMutations(lines, mutations));
                    updatedCount += documentUpdatedTaskKeys.size();
                    updatedTaskKeys.addAll(documentUpdatedTaskKeys);
                    touchedFileCount++;
                } catch (IOException | RuntimeException exception) {
                    failedCount += documentUpdatedTaskKeys.size();
                    ErrorLog.record(context, errorMessage, exception);
                }
            }
        } catch (IOException | RuntimeException exception) {
            failedCount += remainingTaskKeys.size();
            remainingTaskKeys.clear();
            ErrorLog.record(context, errorMessage, exception);
        }

        int notFoundCount = remainingTaskKeys.size();
        return new BulkEditResult(
                totalCount,
                updatedCount,
                skippedCount,
                failedCount,
                notFoundCount,
                touchedFileCount,
                updatedTaskKeys
        );
    }

    private static TaskEditResult editTaskLine(
            Context context,
            String taskKey,
            boolean allowCompleted,
            TaskLineMutationEditor editor
    ) {
        if (taskKey == null || taskKey.trim().isEmpty()) {
            return TaskEditResult.notFound("ключ задачи пустой");
        }

        try {
            TaskFormatSettings formatSettings = TaskFormatSettings.load(context);
            for (NoteDocument document : readDocuments(context)) {
                TaskParseResult result = parseDocument(context, document, formatSettings);
                for (ObsidianTask task : result.getTasks()) {
                    if (!task.getTaskKey().equals(taskKey)) {
                        continue;
                    }
                    if (task.isCompleted() && !allowCompleted) {
                        return TaskEditResult.alreadyDone("задача уже выполнена");
                    }

                    String[] lines = document.getMarkdown().split("\n", -1);
                    int index = task.getLineNumber() - 1;
                    if (index < 0 || index >= lines.length) {
                        return TaskEditResult.conflict("строка задачи изменилась");
                    }

                    TaskLineMutation mutation = editor.edit(lines[index]);
                    if (mutation == null) {
                        return TaskEditResult.conflict("строка задачи больше не подходит для изменения");
                    }

                    String latestMarkdown = readMarkdown(context, document.getUri());
                    if (!latestMarkdown.equals(document.getMarkdown())) {
                        return TaskEditResult.conflict("файл изменился во время записи");
                    }

                    String updatedMarkdown = mutation.isDelete()
                            ? removeLine(lines, index)
                            : replaceLine(lines, index, mutation.getLine());
                    writeMarkdown(context, document.getUri(), updatedMarkdown);
                    return TaskEditResult.updated(mutation.isDelete()
                            ? "уведомление удалено"
                            : "уведомление обновлено");
                }
            }
            return TaskEditResult.notFound("задача не найдена или уже изменилась");
        } catch (IOException | RuntimeException exception) {
            ErrorLog.record(context, "Не удалось изменить markdown-уведомление", exception);
            return TaskEditResult.writeFailed(exception.getMessage());
        }
    }

    private static TaskEditResult editTaskBlock(
            Context context,
            String taskKey,
            String block
    ) {
        if (taskKey == null || taskKey.trim().isEmpty()) {
            return TaskEditResult.notFound("ключ задачи пустой");
        }

        try {
            TaskFormatSettings formatSettings = TaskFormatSettings.load(context);
            for (NoteDocument document : readDocuments(context)) {
                TaskParseResult result = parseDocument(context, document, formatSettings);
                for (ObsidianTask task : result.getTasks()) {
                    if (!task.getTaskKey().equals(taskKey)) {
                        continue;
                    }

                    String[] lines = document.getMarkdown().split("\n", -1);
                    int index = task.getLineNumber() - 1;
                    if (index < 0 || index >= lines.length) {
                        return TaskEditResult.conflict("строка задачи изменилась");
                    }

                    String latestMarkdown = readMarkdown(context, document.getUri());
                    if (!latestMarkdown.equals(document.getMarkdown())) {
                        return TaskEditResult.conflict("файл изменился во время записи");
                    }

                    int endExclusive = taskBlockEnd(lines, index);
                    String updatedMarkdown = block == null
                            ? replaceLineRange(lines, index, endExclusive, "")
                            : replaceLineRange(lines, index, endExclusive, block);
                    writeMarkdown(context, document.getUri(), updatedMarkdown);
                    return TaskEditResult.updated(block == null
                            ? "уведомление удалено"
                            : "изменения сохранены");
                }
            }
            return TaskEditResult.notFound("задача не найдена или уже изменилась");
        } catch (IOException | RuntimeException exception) {
            ErrorLog.record(context, "Не удалось изменить markdown-блок уведомления", exception);
            return TaskEditResult.writeFailed(exception.getMessage());
        }
    }

    private static String incrementSnoozedMarker(String line) {
        Matcher matcher = SNOOZED_COUNT.matcher(line);
        if (matcher.find()) {
            int count;
            try {
                count = Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException ignored) {
                count = 0;
            }
            return matcher.replaceFirst("@snoozed(" + (count + 1) + ")");
        }
        return line + " @snoozed(1)";
    }

    private static String markDoneLine(String line) {
        Matcher matcher = ACTIVE_TASK_MARKER.matcher(line);
        if (matcher.find()) {
            return matcher.group(1) + "x" + matcher.group(2);
        }

        Matcher bulletMatcher = NON_CHECKBOX_BULLET_MARKER.matcher(line);
        if (bulletMatcher.find()) {
            return bulletMatcher.group(1) + "[x] " + bulletMatcher.group(2);
        }

        String trimmed = line == null ? "" : line.trim();
        return trimmed.isEmpty() ? null : "- [x] " + trimmed;
    }

    private static String unmarkDoneLine(String line) {
        if (line == null) {
            return null;
        }
        Matcher matcher = DONE_TASK_MARKER.matcher(line);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(1) + " " + matcher.group(2);
    }

    private static String appendSkippedMarker(String line) {
        String safeLine = line == null ? "" : line.trim();
        if (safeLine.isEmpty() || SKIPPED_MARKER.matcher(line).find()) {
            return line;
        }
        return line + " @skipped";
    }

    private static String removeSkippedMarker(String line) {
        if (line == null || !SKIPPED_MARKER.matcher(line).find()) {
            return null;
        }
        String updated = SKIPPED_MARKER.matcher(line).replaceAll("");
        updated = updated.replaceAll("(?<=\\S)[ \\t]{2,}(?=\\S)", " ");
        updated = updated.replaceAll("[ \\t]+$", "");
        return updated.equals(line) ? null : updated;
    }

    private static String joinLines(String[] lines) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                builder.append('\n');
            }
            builder.append(lines[i]);
        }
        return builder.toString();
    }

    private static boolean sameMarkdownContent(String first, String second) {
        return normalizeMarkdownForComparison(first).equals(normalizeMarkdownForComparison(second));
    }

    private static String normalizeMarkdownForComparison(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return "";
        }
        String normalized = markdown
                .replace("\r\n", "\n")
                .replace('\r', '\n');
        return normalized.endsWith("\n") ? normalized : normalized + '\n';
    }

    private static String replaceLine(String[] lines, int index, String updatedLine) {
        lines[index] = updatedLine;
        return joinLines(lines);
    }

    private static String removeLine(String[] lines, int index) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i == index) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(lines[i]);
        }
        return builder.toString();
    }

    private static String applyLineMutations(
            String[] lines,
            Map<Integer, TaskLineMutation> mutations
    ) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            TaskLineMutation mutation = mutations.get(i);
            if (mutation != null && mutation.isDelete()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(mutation == null ? lines[i] : mutation.getLine());
        }
        return builder.toString();
    }

    private static int taskBlockEnd(String[] lines, int startIndex) {
        int parentIndent = leadingIndentLevel(lines[startIndex]);
        int end = startIndex + 1;
        while (end < lines.length) {
            String line = lines[end];
            if (line == null || line.trim().isEmpty()) {
                end++;
                continue;
            }
            if (leadingIndentLevel(line) <= parentIndent) {
                break;
            }
            end++;
        }
        return end;
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

    private static String replaceLineRange(
            String[] lines,
            int startIndex,
            int endExclusive,
            String replacementBlock
    ) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i == startIndex) {
                if (replacementBlock != null && !replacementBlock.isEmpty()) {
                    appendWithSeparator(builder, replacementBlock);
                }
            }
            if (i >= startIndex && i < endExclusive) {
                continue;
            }
            appendWithSeparator(builder, lines[i]);
        }
        return builder.toString();
    }

    private static String joinLineRange(String[] lines, int startIndex, int endExclusive) {
        StringBuilder builder = new StringBuilder();
        for (int i = startIndex; i < endExclusive && i < lines.length; i++) {
            appendWithSeparator(builder, lines[i]);
        }
        return builder.toString();
    }

    private static void appendWithSeparator(StringBuilder builder, String value) {
        if (builder.length() > 0) {
            builder.append('\n');
        }
        builder.append(value == null ? "" : value);
    }

    private static String sanitizeSingleLine(String rawLine) {
        return rawLine == null
                ? ""
                : rawLine.replace('\r', ' ').replace('\n', ' ').trim();
    }

    private static String sanitizeMarkdownBlock(String rawBlock) {
        if (rawBlock == null) {
            return "";
        }
        String normalized = rawBlock.replace("\r\n", "\n").replace('\r', '\n').trim();
        String[] lines = normalized.split("\n", -1);
        StringBuilder builder = new StringBuilder();
        for (String line : lines) {
            String cleanLine = line.replaceAll("[ \t]+$", "");
            if (cleanLine.trim().isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(cleanLine);
        }
        return builder.toString();
    }

    private static List<NoteDocument> readFolderDocuments(
            Context context,
            Uri treeUri,
            NoteScanSettings scanSettings
    ) throws IOException {
        List<NoteDocument> documents = new ArrayList<>();
        scanFolderDocuments(
                context,
                treeUri,
                DocumentsContract.getTreeDocumentId(treeUri),
                "",
                scanSettings,
                documents
        );
        return documents;
    }

    private static void scanFolderDocuments(
            Context context,
            Uri treeUri,
            String documentId,
            String parentPath,
            NoteScanSettings scanSettings,
            List<NoteDocument> documents
    ) throws IOException {
        if (documents.size() >= scanSettings.getMaxFiles()) {
            return;
        }

        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                treeUri,
                documentId
        );

        try (Cursor cursor = context.getContentResolver().query(
                childrenUri,
                new String[]{
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE
                },
                null,
                null,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME + " ASC"
        )) {
            if (cursor == null) {
                throw new IOException("провайдер не вернул список файлов папки");
            }

            while (cursor.moveToNext()) {
                String childDocumentId = cursor.getString(0);
                String displayName = cursor.getString(1);
                String mimeType = cursor.getString(2);
                String relativePath = parentPath.isEmpty()
                        ? displayName
                        : parentPath + "/" + displayName;
                Uri documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childDocumentId);

                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mimeType)) {
                    if (!scanSettings.shouldSkipDirectory(relativePath)) {
                        scanFolderDocuments(
                                context,
                                treeUri,
                                childDocumentId,
                                relativePath,
                                scanSettings,
                                documents
                        );
                    }
                    if (documents.size() >= scanSettings.getMaxFiles()) {
                        return;
                    }
                    continue;
                }

                if (!isMarkdownDocument(displayName, mimeType)
                        || !scanSettings.shouldReadFile(relativePath, displayName)) {
                    continue;
                }

                documents.add(new NoteDocument(
                        relativePath,
                        documentUri,
                        readMarkdown(context, documentUri)
                ));
                if (documents.size() >= scanSettings.getMaxFiles()) {
                    return;
                }
            }
        } catch (SecurityException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new IOException("не удалось прочитать папку заметок: " + exception.getMessage(), exception);
        }
    }

    private static boolean isMarkdownDocument(String displayName, String mimeType) {
        String lowerName = displayName == null ? "" : displayName.toLowerCase(Locale.ROOT);
        if (lowerName.endsWith(".md") || lowerName.endsWith(".markdown")) {
            return true;
        }

        return "text/markdown".equals(mimeType) || "text/plain".equals(mimeType);
    }

    private static String displayName(Context context, Uri uri) {
        if (uri == null) {
            return "";
        }
        if ("file".equalsIgnoreCase(uri.getScheme())) {
            String path = uri.getPath();
            if (path == null || path.trim().isEmpty()) {
                return uri.toString();
            }
            return java.nio.file.Paths.get(path).getFileName().toString();
        }
        try (Cursor cursor = context.getContentResolver().query(
                uri,
                new String[]{OpenableColumns.DISPLAY_NAME},
                null,
                null,
                null
        )) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    return cursor.getString(index);
                }
            }
        } catch (RuntimeException ignored) {
            return uri.toString();
        }
        return uri.toString();
    }

    public static final class BulkEditResult {
        private final int totalCount;
        private final int updatedCount;
        private final int skippedCount;
        private final int failedCount;
        private final int notFoundCount;
        private final int touchedFileCount;
        private final List<String> updatedTaskKeys;

        private BulkEditResult(
                int totalCount,
                int updatedCount,
                int skippedCount,
                int failedCount,
                int notFoundCount,
                int touchedFileCount,
                List<String> updatedTaskKeys
        ) {
            this.totalCount = totalCount;
            this.updatedCount = updatedCount;
            this.skippedCount = skippedCount;
            this.failedCount = failedCount;
            this.notFoundCount = notFoundCount;
            this.touchedFileCount = touchedFileCount;
            this.updatedTaskKeys = new ArrayList<>(updatedTaskKeys == null
                    ? new ArrayList<>()
                    : updatedTaskKeys);
        }

        public int getTotalCount() {
            return totalCount;
        }

        public int getUpdatedCount() {
            return updatedCount;
        }

        public int getSkippedCount() {
            return skippedCount;
        }

        public int getFailedCount() {
            return failedCount;
        }

        public int getNotFoundCount() {
            return notFoundCount;
        }

        public int getTouchedFileCount() {
            return touchedFileCount;
        }

        public List<String> getUpdatedTaskKeys() {
            return new ArrayList<>(updatedTaskKeys);
        }

        public boolean hasFailures() {
            return failedCount > 0 || notFoundCount > 0;
        }

        public boolean hasUpdates() {
            return updatedCount > 0;
        }
    }

    public static final class NoteSource {
        private final String type;
        private final Uri uri;

        public NoteSource(String type, Uri uri) {
            this.type = normalizeSourceType(type);
            this.uri = uri;
        }

        public String getType() {
            return type;
        }

        public Uri getUri() {
            return uri;
        }
    }

    public static final class NoteDocument {
        private final String displayName;
        private final Uri uri;
        private final String markdown;

        public NoteDocument(String displayName, Uri uri, String markdown) {
            this.displayName = displayName == null ? uri.toString() : displayName;
            this.uri = uri;
            this.markdown = markdown;
        }

        public String getDisplayName() {
            return displayName;
        }

        public Uri getUri() {
            return uri;
        }

        public String getMarkdown() {
            return markdown;
        }
    }

    public static final class MarkdownDocument {
        private final String displayName;
        private final Uri uri;
        private final String markdown;
        private final int targetLineNumber;

        public MarkdownDocument(String displayName, Uri uri, String markdown, int targetLineNumber) {
            this.displayName = displayName == null ? uri.toString() : displayName;
            this.uri = uri;
            this.markdown = markdown == null ? "" : markdown;
            this.targetLineNumber = Math.max(1, targetLineNumber);
        }

        public String getDisplayName() {
            return displayName;
        }

        public Uri getUri() {
            return uri;
        }

        public String getMarkdown() {
            return markdown;
        }

        public int getTargetLineNumber() {
            return targetLineNumber;
        }
    }

    public static final class TaskSnapshot {
        private final TaskParseResult parseResult;
        private final int documentCount;
        private final int totalCharacters;

        public TaskSnapshot(
                TaskParseResult parseResult,
                int documentCount,
                int totalCharacters
        ) {
            this.parseResult = parseResult;
            this.documentCount = documentCount;
            this.totalCharacters = totalCharacters;
        }

        public TaskParseResult getParseResult() {
            return parseResult;
        }

        public int getDocumentCount() {
            return documentCount;
        }

        public int getTotalCharacters() {
            return totalCharacters;
        }
    }

    public static final class TaskDocumentMatch {
        private final Uri uri;
        private final String displayName;
        private final ObsidianTask task;

        public TaskDocumentMatch(Uri uri, String displayName, ObsidianTask task) {
            this.uri = uri;
            this.displayName = displayName;
            this.task = task;
        }

        public Uri getUri() {
            return uri;
        }

        public String getDisplayName() {
            return displayName;
        }

        public ObsidianTask getTask() {
            return task;
        }
    }

    public static final class TaskBlockSnapshot {
        private final Uri uri;
        private final String displayName;
        private final String taskKey;
        private final String originalMarkdown;
        private final String markdownAfterDelete;
        private final String deletedBlock;

        public TaskBlockSnapshot(
                Uri uri,
                String displayName,
                String taskKey,
                String originalMarkdown,
                String markdownAfterDelete,
                String deletedBlock
        ) {
            this.uri = uri;
            this.displayName = displayName;
            this.taskKey = taskKey;
            this.originalMarkdown = originalMarkdown == null ? "" : originalMarkdown;
            this.markdownAfterDelete = markdownAfterDelete == null ? "" : markdownAfterDelete;
            this.deletedBlock = deletedBlock == null ? "" : deletedBlock;
        }

        public Uri getUri() {
            return uri;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getTaskKey() {
            return taskKey;
        }

        public String getOriginalMarkdown() {
            return originalMarkdown;
        }

        public String getMarkdownAfterDelete() {
            return markdownAfterDelete;
        }

        public String getDeletedBlock() {
            return deletedBlock;
        }
    }

    private interface TaskLineEditor {
        String edit(String line);
    }

    private interface TaskLineMutationEditor {
        TaskLineMutation edit(String line);
    }

    private static final class TaskLineMutation {
        private final String line;
        private final boolean delete;

        private TaskLineMutation(String line, boolean delete) {
            this.line = line;
            this.delete = delete;
        }

        private static TaskLineMutation replace(String line) {
            return new TaskLineMutation(line, false);
        }

        private static TaskLineMutation delete() {
            return new TaskLineMutation(null, true);
        }

        private String getLine() {
            return line;
        }

        private boolean isDelete() {
            return delete;
        }
    }
}
