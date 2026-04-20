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
    private static final Pattern CHECKBOX_TASK_MARKER =
            Pattern.compile("(?m)^\\s*[-*+]\\s+\\[[ xX]\\]");
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
        List<NoteSource> sources = getSavedSources(context);
        return sources.isEmpty() ? null : sources.get(0).getUri();
    }

    public static String getSavedSourceType(Context context) {
        List<NoteSource> sources = getSavedSources(context);
        return sources.isEmpty() ? SOURCE_NOTE : sources.get(0).getType();
    }

    public static boolean hasSavedSources(Context context) {
        return !getSavedSources(context).isEmpty();
    }

    public static int getSavedSourceCount(Context context) {
        return getSavedSources(context).size();
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
            if (!looksLikeTaskDocument(document.getMarkdown())) {
                continue;
            }
            results.add(TaskParser.parseDocument(
                    document.getMarkdown(),
                    java.time.LocalDate.now(),
                    document.getDisplayName(),
                    formatSettings
            ));
        }
        return new TaskSnapshot(TaskParseResult.merge(results), documentCount, totalCharacters);
    }

    private static boolean looksLikeTaskDocument(String markdown) {
        return markdown != null && CHECKBOX_TASK_MARKER.matcher(markdown).find();
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
