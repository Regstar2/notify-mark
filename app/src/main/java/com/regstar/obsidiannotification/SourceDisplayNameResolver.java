package com.regstar.obsidiannotification;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import androidx.documentfile.provider.DocumentFile;

import java.io.File;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

final class SourceDisplayNameResolver {
    private static final String FILE_FALLBACK = "Безымянный файл";
    private static final String FOLDER_FALLBACK = "Безымянная папка";
    private static final String SOURCE_FALLBACK = "Безымянный источник";

    private SourceDisplayNameResolver() {
    }

    static SourceItemModel describeExternalSource(Context context, NoteStore.NoteSource source) {
        boolean folder = NoteStore.SOURCE_FOLDER.equals(source.getType());
        String name = resolveDisplayName(context, source.getUri(), folder);
        AccessState accessState = resolveAccessState(context, source);
        String subtitle = (folder ? "Папка" : "Файл") + " · " + accessState.getLabel();
        String preview = shortPreview(source.getUri());
        return new SourceItemModel(
                name,
                subtitle,
                shouldShowPreview(name, preview) ? preview : null,
                folder,
                accessState
        );
    }

    static ExternalSummaryModel summarizeExternalSources(
            Context context,
            List<NoteStore.NoteSource> sources
    ) {
        if (sources == null || sources.isEmpty()) {
            return new ExternalSummaryModel(
                    "Внешний источник не выбран",
                    "Подключите markdown-файл или папку",
                    null
            );
        }

        int folderCount = 0;
        int fileCount = 0;
        int writableCount = 0;
        int readableCount = 0;
        SourceItemModel firstModel = null;

        for (NoteStore.NoteSource source : sources) {
            SourceItemModel model = describeExternalSource(context, source);
            if (firstModel == null) {
                firstModel = model;
            }
            if (model.isFolder()) {
                folderCount++;
            } else {
                fileCount++;
            }
            if (model.getAccessState() != AccessState.LOST) {
                readableCount++;
            }
            if (model.getAccessState() == AccessState.WRITABLE) {
                writableCount++;
            }
        }

        String headline;
        String subtitle;
        String accessLabel = aggregateAccessLabel(sources.size(), readableCount, writableCount);
        if (sources.size() == 1 && firstModel != null) {
            headline = (firstModel.isFolder() ? "Папка: " : "Файл: ") + firstModel.getTitle();
            subtitle = (firstModel.isFolder() ? "папка" : "файл") + " · " + accessLabel;
        } else if (folderCount == 0 && fileCount == sources.size()) {
            headline = markdownFileCountLabel(sources.size());
            subtitle = markdownFileCountLabel(sources.size()) + " · " + accessLabel;
        } else {
            headline = externalSourceCountLabel(sources.size());
            subtitle = externalSourceCountLabel(sources.size()) + " · " + accessLabel;
        }

        return new ExternalSummaryModel(
                headline,
                subtitle,
                firstModel == null ? null : firstModel.getTechnicalPreview()
        );
    }

    static String resolveDisplayName(Context context, Uri uri) {
        return resolveDisplayName(context, uri, false);
    }

    static String resolveDisplayName(Context context, Uri uri, boolean folderHint) {
        String displayName = tryDocumentFileName(context, uri, folderHint);
        if (!isEmpty(displayName)) {
            return displayName;
        }

        displayName = queryDisplayName(context, uri, OpenableColumns.DISPLAY_NAME);
        if (!isEmpty(displayName)) {
            return displayName;
        }

        displayName = queryDisplayName(context, uri, DocumentsContract.Document.COLUMN_DISPLAY_NAME);
        if (!isEmpty(displayName)) {
            return displayName;
        }

        displayName = queryTreeDisplayName(context, uri);
        if (!isEmpty(displayName)) {
            return displayName;
        }

        displayName = fallbackNameFromUri(uri, folderHint);
        if (!isEmpty(displayName)) {
            return displayName;
        }
        return unnamedFallback(folderHint);
    }

    static String sourceCountLabel(int count) {
        return count + " " + russianPlural(count, "источник", "источника", "источников");
    }

    static String externalSourceCountLabel(int count) {
        return count + " внешн" + (count % 10 == 1 && count % 100 != 11 ? "ий " : "их ")
                + russianPlural(count, "источник", "источника", "источников");
    }

    static String markdownFileCountLabel(int count) {
        return count + " " + russianPlural(count, "markdown-файл", "markdown-файла", "markdown-файлов");
    }

    static String fallbackNameFromDocumentId(String documentId, boolean folderHint) {
        String normalized = sanitizeNameCandidate(documentId);
        if (isEmpty(normalized)) {
            return unnamedFallback(folderHint);
        }
        return normalized;
    }

    static String fallbackNameFromUri(Uri uri, boolean folderHint) {
        if (uri == null) {
            return unnamedFallback(folderHint);
        }

        String documentId = null;
        try {
            if (DocumentsContract.isTreeUri(uri)) {
                documentId = DocumentsContract.getTreeDocumentId(uri);
            } else {
                documentId = DocumentsContract.getDocumentId(uri);
            }
        } catch (RuntimeException ignored) {
            // Not every content URI is a document URI.
        }
        if (!isEmpty(documentId)) {
            String fromDocumentId = fallbackNameFromDocumentId(documentId, folderHint);
            if (!isEmpty(fromDocumentId)) {
                return fromDocumentId;
            }
        }

        String fromLastSegment = sanitizeNameCandidate(uri.getLastPathSegment());
        if (!isEmpty(fromLastSegment)) {
            return fromLastSegment;
        }

        String fromUriString = fallbackNameFromRawUri(uri.toString(), folderHint);
        if (!isEmpty(fromUriString)) {
            return fromUriString;
        }
        return unnamedFallback(folderHint);
    }

    static String fallbackNameFromRawUri(String rawUri, boolean folderHint) {
        String normalized = sanitizeNameCandidate(rawUri);
        if (isEmpty(normalized)) {
            return unnamedFallback(folderHint);
        }
        return normalized;
    }

    static String shortPreview(Uri uri) {
        if (uri == null) {
            return null;
        }
        String raw = "file".equalsIgnoreCase(uri.getScheme())
                ? uri.getPath()
                : uri.toString();
        if (isEmpty(raw)) {
            return null;
        }
        return safeDecode(raw).replace('\n', ' ').trim();
    }

    private static String tryDocumentFileName(Context context, Uri uri, boolean folderHint) {
        if (context == null || uri == null) {
            return null;
        }
        try {
            DocumentFile documentFile;
            if (folderHint || DocumentsContract.isTreeUri(uri)) {
                documentFile = DocumentFile.fromTreeUri(context, uri);
            } else {
                documentFile = DocumentFile.fromSingleUri(context, uri);
            }
            if (documentFile == null) {
                return null;
            }
            String name = sanitizeNameCandidate(documentFile.getName());
            if (!isEmpty(name)) {
                return name;
            }
        } catch (RuntimeException ignored) {
            // Some providers expose a URI but reject metadata reads.
        }
        return null;
    }

    private static String queryTreeDisplayName(Context context, Uri treeUri) {
        if (context == null || treeUri == null || !DocumentsContract.isTreeUri(treeUri)) {
            return null;
        }
        try {
            String treeDocumentId = DocumentsContract.getTreeDocumentId(treeUri);
            Uri documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocumentId);
            return queryDisplayName(context, documentUri, DocumentsContract.Document.COLUMN_DISPLAY_NAME);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String queryDisplayName(Context context, Uri uri, String columnName) {
        if (context == null || uri == null || isEmpty(columnName)) {
            return null;
        }
        try (Cursor cursor = context.getContentResolver().query(
                uri,
                new String[]{columnName},
                null,
                null,
                null
        )) {
            if (cursor == null || !cursor.moveToFirst()) {
                return null;
            }
            int index = cursor.getColumnIndex(columnName);
            if (index < 0) {
                return null;
            }
            return sanitizeNameCandidate(cursor.getString(index));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static AccessState resolveAccessState(Context context, NoteStore.NoteSource source) {
        if (source == null || source.getUri() == null) {
            return AccessState.LOST;
        }
        boolean readable = canReadSource(context, source);
        boolean writable = readable && NoteStore.canWriteUri(context, source.getUri());
        if (!readable) {
            return AccessState.LOST;
        }
        return writable ? AccessState.WRITABLE : AccessState.READ_ONLY;
    }

    private static boolean canReadSource(Context context, NoteStore.NoteSource source) {
        Uri uri = source.getUri();
        if (uri == null) {
            return false;
        }
        if ("file".equalsIgnoreCase(uri.getScheme())) {
            String path = uri.getPath();
            return !isEmpty(path) && new File(path).canRead();
        }

        try {
            DocumentFile documentFile = NoteStore.SOURCE_FOLDER.equals(source.getType())
                    ? DocumentFile.fromTreeUri(context, uri)
                    : DocumentFile.fromSingleUri(context, uri);
            if (documentFile != null && documentFile.canRead()) {
                return true;
            }
        } catch (RuntimeException ignored) {
            // Fallback to metadata query below.
        }

        if (NoteStore.SOURCE_FOLDER.equals(source.getType()) && DocumentsContract.isTreeUri(uri)) {
            return !isEmpty(queryTreeDisplayName(context, uri));
        }
        return !isEmpty(queryDisplayName(context, uri, OpenableColumns.DISPLAY_NAME))
                || !isEmpty(queryDisplayName(context, uri, DocumentsContract.Document.COLUMN_DISPLAY_NAME));
    }

    private static boolean shouldShowPreview(String title, String preview) {
        if (isEmpty(preview)) {
            return false;
        }
        if (isEmpty(title)) {
            return true;
        }
        if (preview.startsWith("content://")) {
            return true;
        }
        String normalizedPreview = preview.toLowerCase(Locale.ROOT);
        String normalizedTitle = title.toLowerCase(Locale.ROOT);
        return !normalizedPreview.endsWith(normalizedTitle);
    }

    private static String aggregateAccessLabel(int totalCount, int readableCount, int writableCount) {
        if (readableCount <= 0) {
            return "доступ потерян";
        }
        if (readableCount < totalCount) {
            return "часть источников недоступна";
        }
        if (writableCount == totalCount) {
            return "запись доступна";
        }
        return "только чтение";
    }

    private static String unnamedFallback(boolean folderHint) {
        return folderHint ? FOLDER_FALLBACK : FILE_FALLBACK;
    }

    private static String sanitizeNameCandidate(String rawValue) {
        if (isEmpty(rawValue)) {
            return null;
        }
        String value = safeDecode(rawValue).trim();
        if (isEmpty(value)) {
            return null;
        }
        value = value.replace('\\', '/');
        int queryIndex = value.indexOf('?');
        if (queryIndex >= 0) {
            value = value.substring(0, queryIndex);
        }
        while (value.startsWith("/")) {
            value = value.substring(1);
        }
        int colonIndex = value.lastIndexOf(':');
        if (colonIndex >= 0 && colonIndex + 1 < value.length()) {
            value = value.substring(colonIndex + 1);
        }
        int slashIndex = value.lastIndexOf('/');
        if (slashIndex >= 0 && slashIndex + 1 < value.length()) {
            value = value.substring(slashIndex + 1);
        }
        value = value.trim();
        if (isEmpty(value) || value.startsWith("content://")) {
            return null;
        }
        return value;
    }

    private static boolean isEmpty(String value) {
        return value == null || value.isEmpty();
    }

    private static String safeDecode(String rawValue) {
        if (rawValue == null) {
            return null;
        }
        try {
            return URLDecoder.decode(rawValue, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            return rawValue;
        }
    }

    private static String russianPlural(int count, String one, String few, String many) {
        int mod100 = count % 100;
        int mod10 = count % 10;
        if (mod100 >= 11 && mod100 <= 14) {
            return many;
        }
        if (mod10 == 1) {
            return one;
        }
        if (mod10 >= 2 && mod10 <= 4) {
            return few;
        }
        return many;
    }

    enum AccessState {
        WRITABLE("запись доступна"),
        READ_ONLY("только чтение"),
        LOST("доступ потерян");

        private final String label;

        AccessState(String label) {
            this.label = label;
        }

        String getLabel() {
            return label;
        }
    }

    static final class SourceItemModel {
        private final String title;
        private final String subtitle;
        private final String technicalPreview;
        private final boolean folder;
        private final AccessState accessState;

        SourceItemModel(
                String title,
                String subtitle,
                String technicalPreview,
                boolean folder,
                AccessState accessState
        ) {
            this.title = title;
            this.subtitle = subtitle;
            this.technicalPreview = technicalPreview;
            this.folder = folder;
            this.accessState = accessState;
        }

        String getTitle() {
            return title;
        }

        String getSubtitle() {
            return subtitle;
        }

        String getTechnicalPreview() {
            return technicalPreview;
        }

        boolean isFolder() {
            return folder;
        }

        AccessState getAccessState() {
            return accessState;
        }
    }

    static final class ExternalSummaryModel {
        private final String headline;
        private final String subtitle;
        private final String technicalPreview;

        ExternalSummaryModel(String headline, String subtitle, String technicalPreview) {
            this.headline = headline;
            this.subtitle = subtitle;
            this.technicalPreview = technicalPreview;
        }

        String getHeadline() {
            return headline;
        }

        String getSubtitle() {
            return subtitle;
        }

        String getTechnicalPreview() {
            return technicalPreview;
        }
    }
}
