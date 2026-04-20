package com.regstar.obsidiannotification;

import android.content.Context;
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
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class NoteStore {
    public static final String SOURCE_NOTE = "note";
    public static final String SOURCE_FOLDER = "folder";

    private static final String PREFS_NAME = "obsidian_notification_prefs";
    private static final String KEY_NOTE_URI = "note_uri";
    private static final String KEY_SOURCE_URI = "source_uri";
    private static final String KEY_SOURCE_TYPE = "source_type";
    private static final Pattern ACTIVE_TASK_MARKER =
            Pattern.compile("^(\\s*[-*+]\\s+\\[)[ xX](\\].*)$");
    private static final Pattern SNOOZED_COUNT =
            Pattern.compile("(?iu)@snoozed\\(\\s*(\\d+)\\s*\\)");

    private NoteStore() {
    }

    public static Uri getSavedNoteUri(Context context) {
        if (!SOURCE_NOTE.equals(getSavedSourceType(context))) {
            return null;
        }
        return getSavedSourceUri(context);
    }

    public static Uri getSavedSourceUri(Context context) {
        String savedUri = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_SOURCE_URI, null);
        if (savedUri == null) {
            savedUri = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .getString(KEY_NOTE_URI, null);
        }
        return savedUri == null ? null : Uri.parse(savedUri);
    }

    public static String getSavedSourceType(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_SOURCE_TYPE, SOURCE_NOTE);
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
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_SOURCE_TYPE, SOURCE_NOTE)
                .putString(KEY_SOURCE_URI, uri.toString())
                .putString(KEY_NOTE_URI, uri.toString())
                .apply();
    }

    public static void saveFolderUri(Context context, Uri uri) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_SOURCE_TYPE, SOURCE_FOLDER)
                .putString(KEY_SOURCE_URI, uri.toString())
                .remove(KEY_NOTE_URI)
                .apply();
    }

    public static boolean canWriteSavedSource(Context context) {
        Uri sourceUri = getSavedSourceUri(context);
        if (sourceUri == null) {
            return false;
        }
        if ("file".equalsIgnoreCase(sourceUri.getScheme())) {
            return true;
        }

        for (UriPermission permission : context.getContentResolver().getPersistedUriPermissions()) {
            if (permission.getUri().equals(sourceUri) && permission.isWritePermission()) {
                return true;
            }
        }
        return false;
    }

    public static String readMarkdown(Context context, Uri uri) throws IOException {
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
        Uri sourceUri = requireSavedSourceUri(context);
        if (SOURCE_FOLDER.equals(getSavedSourceType(context))) {
            return readFolderDocuments(context, sourceUri);
        }

        List<NoteDocument> documents = new ArrayList<>();
        documents.add(new NoteDocument(displayName(context, sourceUri), sourceUri, readMarkdown(context, sourceUri)));
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
            results.add(TaskParser.parseDocument(
                    document.getMarkdown(),
                    java.time.LocalDate.now(),
                    document.getDisplayName(),
                    formatSettings
            ));
        }
        return new TaskSnapshot(TaskParseResult.merge(results), documentCount, totalCharacters);
    }

    public static List<ObsidianTask> readTasks(Context context) throws IOException {
        return readTaskParseResult(context).getActiveTasks();
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
            TaskParseResult result = TaskParser.parseDocument(
                    document.getMarkdown(),
                    LocalDate.now(),
                    document.getDisplayName(),
                    formatSettings
            );
            for (ObsidianTask task : result.getTasks()) {
                if (task.getTaskKey().equals(taskKey)) {
                    return new TaskDocumentMatch(document.getUri(), document.getDisplayName(), task);
                }
            }
        }
        return null;
    }

    public static TaskEditResult markTaskDone(Context context, String taskKey) {
        return editActiveTaskLine(context, taskKey, line -> {
            Matcher matcher = ACTIVE_TASK_MARKER.matcher(line);
            if (!matcher.find()) {
                return null;
            }
            return matcher.group(1) + "x" + matcher.group(2);
        });
    }

    public static TaskEditResult incrementSnoozeCount(Context context, String taskKey) {
        return editActiveTaskLine(context, taskKey, NoteStore::incrementSnoozedMarker);
    }

    public static String sourceLabel(Context context) {
        Uri sourceUri = getSavedSourceUri(context);
        if (sourceUri == null) {
            return "не выбрано";
        }

        String type = SOURCE_FOLDER.equals(getSavedSourceType(context)) ? "папка" : "заметка";
        return type + ": " + displayName(context, sourceUri);
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
                TaskParseResult result = TaskParser.parseDocument(
                        document.getMarkdown(),
                        LocalDate.now(),
                        document.getDisplayName(),
                        formatSettings
                );
                for (ObsidianTask task : result.getTasks()) {
                    if (!task.getTaskKey().equals(taskKey)) {
                        continue;
                    }
                    if (task.isCompleted()) {
                        return TaskEditResult.alreadyDone("задача уже выполнена");
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

    private static List<NoteDocument> readFolderDocuments(Context context, Uri treeUri) throws IOException {
        List<NoteDocument> documents = new ArrayList<>();
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                treeUri,
                DocumentsContract.getTreeDocumentId(treeUri)
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
                String documentId = cursor.getString(0);
                String displayName = cursor.getString(1);
                String mimeType = cursor.getString(2);
                if (!isMarkdownDocument(displayName, mimeType)) {
                    continue;
                }

                Uri documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId);
                documents.add(new NoteDocument(displayName, documentUri, readMarkdown(context, documentUri)));
            }
        } catch (SecurityException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new IOException("не удалось прочитать папку заметок: " + exception.getMessage(), exception);
        }

        if (documents.isEmpty()) {
            throw new IOException("в выбранной папке не найдено markdown-файлов");
        }

        return documents;
    }

    private static boolean isMarkdownDocument(String displayName, String mimeType) {
        String lowerName = displayName == null ? "" : displayName.toLowerCase(Locale.ROOT);
        if (lowerName.endsWith(".md") || lowerName.endsWith(".markdown")) {
            return true;
        }

        return "text/markdown".equals(mimeType) || "text/plain".equals(mimeType);
    }

    private static String displayName(Context context, Uri uri) {
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

    private interface TaskLineEditor {
        String edit(String line);
    }
}
