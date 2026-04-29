package com.regstar.obsidiannotification.core.source;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;

import androidx.documentfile.provider.DocumentFile;

import com.regstar.obsidiannotification.R;

import java.io.File;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/**
 * Resolves human-readable labels for SAF-backed files and folders.
 *
 * <p>UI surfaces should call this helper instead of showing raw {@code content://}
 * URIs, encoded path segments, or internal app paths as source names.</p>
 */
public final class SourceDisplayNameResolver {
    private SourceDisplayNameResolver() {
    }

    /**
     * Builds the UI model for one saved external note or folder.
     */
    public static SourceItemModel describeExternalSource(Context context, NoteStore.NoteSource source) {
        boolean folder = NoteStore.SOURCE_FOLDER.equals(source.getType());
        String name = resolveDisplayName(context, source.getUri(), folder);
        AccessState accessState = resolveAccessState(context, source);
        String subtitle = context.getString(folder ? R.string.source_folder_label : R.string.source_file_label)
                + " · " + accessState.getLabel(context);
        String preview = shortPreview(source.getUri());
        return new SourceItemModel(
                name,
                subtitle,
                shouldShowPreview(name, preview) ? preview : null,
                folder,
                accessState
        );
    }

    /**
     * Aggregates a saved external source list into one summary card model.
     */
    public static ExternalSummaryModel summarizeExternalSources(
            Context context,
            List<NoteStore.NoteSource> sources
    ) {
        if (sources == null || sources.isEmpty()) {
            return new ExternalSummaryModel(
                    context.getString(R.string.source_not_selected),
                    context.getString(R.string.source_connect_markdown),
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
        String accessLabel = aggregateAccessLabel(context, sources.size(), readableCount, writableCount);
        if (sources.size() == 1 && firstModel != null) {
            headline = context.getString(
                    firstModel.isFolder() ? R.string.source_folder_title : R.string.source_file_title,
                    firstModel.getTitle()
            );
            subtitle = context.getString(firstModel.isFolder() ? R.string.source_folder_short : R.string.source_file_short)
                    + " · " + accessLabel;
        } else if (folderCount == 0 && fileCount == sources.size()) {
            headline = markdownFileCountLabel(context, sources.size());
            subtitle = markdownFileCountLabel(context, sources.size()) + " · " + accessLabel;
        } else {
            headline = externalSourceCountLabel(context, sources.size());
            subtitle = externalSourceCountLabel(context, sources.size()) + " · " + accessLabel;
        }

        return new ExternalSummaryModel(
                headline,
                subtitle,
                firstModel == null ? null : firstModel.getTechnicalPreview()
        );
    }

    /**
     * Resolves the best human-readable name for a file or folder URI.
     */
    public static String resolveDisplayName(Context context, Uri uri) {
        return resolveDisplayName(context, uri, false);
    }

    /**
     * Resolves the best human-readable name for a file or folder URI while honoring an explicit
     * folder hint.
     */
    public static String resolveDisplayName(Context context, Uri uri, boolean folderHint) {
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

        displayName = fallbackNameFromUri(context, uri, folderHint);
        if (!isEmpty(displayName)) {
            return displayName;
        }
        return unnamedFallback(context, folderHint);
    }

    public static String sourceCountLabel(Context context, int count) {
        return context.getResources().getQuantityString(R.plurals.source_count_label, count, count);
    }

    /**
     * Legacy fallback used by unit tests and non-Android callers.
     *
     * <p>Prefer {@link #sourceCountLabel(Context, int)} for UI.</p>
     */
    static String sourceCountLabel(int count) {
        return formatRussianPlural(count, "%d источник", "%d источника", "%d источников");
    }

    public static String externalSourceCountLabel(Context context, int count) {
        return context.getResources().getQuantityString(R.plurals.external_source_count_label, count, count);
    }

    /**
     * Legacy fallback used by unit tests and non-Android callers.
     *
     * <p>Prefer {@link #externalSourceCountLabel(Context, int)} for UI.</p>
     */
    static String externalSourceCountLabel(int count) {
        return formatRussianPlural(count, "%d внешний источник", "%d внешних источника", "%d внешних источников");
    }

    public static String markdownFileCountLabel(Context context, int count) {
        return context.getResources().getQuantityString(R.plurals.markdown_file_count_label, count, count);
    }

    /**
     * Legacy fallback used by unit tests and non-Android callers.
     *
     * <p>Prefer {@link #markdownFileCountLabel(Context, int)} for UI.</p>
     */
    static String markdownFileCountLabel(int count) {
        return formatRussianPlural(count, "%d markdown-файл", "%d markdown-файла", "%d markdown-файлов");
    }

    public static String fallbackNameFromDocumentId(Context context, String documentId, boolean folderHint) {
        String normalized = sanitizeNameCandidate(documentId);
        if (isEmpty(normalized)) {
            return unnamedFallback(context, folderHint);
        }
        return normalized;
    }

    /**
     * Legacy fallback used by unit tests and non-Android callers.
     *
     * <p>Prefer {@link #fallbackNameFromDocumentId(Context, String, boolean)} for UI.</p>
     */
    static String fallbackNameFromDocumentId(String documentId, boolean folderHint) {
        String normalized = sanitizeNameCandidate(documentId);
        if (!isEmpty(normalized)) {
            return normalized;
        }
        return folderHint ? "Безымянная папка" : "Безымянный файл";
    }

    public static String fallbackNameFromUri(Context context, Uri uri, boolean folderHint) {
        if (uri == null) {
            return unnamedFallback(context, folderHint);
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
            String fromDocumentId = fallbackNameFromDocumentId(context, documentId, folderHint);
            if (!isEmpty(fromDocumentId)) {
                return fromDocumentId;
            }
        }

        String fromLastSegment = sanitizeNameCandidate(uri.getLastPathSegment());
        if (!isEmpty(fromLastSegment)) {
            return fromLastSegment;
        }

        String fromUriString = fallbackNameFromRawUri(context, uri.toString(), folderHint);
        if (!isEmpty(fromUriString)) {
            return fromUriString;
        }
        return unnamedFallback(context, folderHint);
    }

    public static String fallbackNameFromRawUri(Context context, String rawUri, boolean folderHint) {
        String normalized = sanitizeNameCandidate(rawUri);
        if (!isEmpty(normalized)) {
            return normalized;
        }
        return context.getString(folderHint ? R.string.source_unnamed_folder : R.string.source_unnamed_source);
    }

    /**
     * Legacy fallback used by unit tests and non-Android callers.
     *
     * <p>Prefer {@link #fallbackNameFromRawUri(Context, String, boolean)} for UI.</p>
     */
    static String fallbackNameFromRawUri(String rawUri, boolean folderHint) {
        String normalized = sanitizeNameCandidate(rawUri);
        if (!isEmpty(normalized)) {
            return normalized;
        }
        return folderHint ? "Безымянная папка" : "Безымянный источник";
    }

    public static String shortPreview(Uri uri) {
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

    private static String aggregateAccessLabel(Context context, int totalCount, int readableCount, int writableCount) {
        if (readableCount <= 0) {
            return context.getString(R.string.source_access_lost);
        }
        if (readableCount < totalCount) {
            return context.getString(R.string.source_access_partial);
        }
        if (writableCount == totalCount) {
            return context.getString(R.string.source_access_writable);
        }
        return context.getString(R.string.source_access_read_only);
    }

    private static String unnamedFallback(Context context, boolean folderHint) {
        return context.getString(folderHint ? R.string.source_unnamed_folder : R.string.source_unnamed_file);
    }

    private static String formatRussianPlural(int count, String one, String few, String many) {
        int normalized = Math.abs(count) % 100;
        int lastDigit = normalized % 10;
        if (normalized >= 11 && normalized <= 14) {
            return String.format(Locale.ROOT, many, count);
        }
        if (lastDigit == 1) {
            return String.format(Locale.ROOT, one, count);
        }
        if (lastDigit >= 2 && lastDigit <= 4) {
            return String.format(Locale.ROOT, few, count);
        }
        return String.format(Locale.ROOT, many, count);
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
            return URLDecoder.decode(rawValue, StandardCharsets.UTF_8.name());
        } catch (IllegalArgumentException | java.io.UnsupportedEncodingException ignored) {
            return rawValue;
        }
    }

    public enum AccessState {
        WRITABLE(R.string.source_access_writable),
        READ_ONLY(R.string.source_access_read_only),
        LOST(R.string.source_access_lost);

        private final int labelResId;

        AccessState(int labelResId) {
            this.labelResId = labelResId;
        }

        public String getLabel(Context context) {
            return context.getString(labelResId);
        }
    }

    public static final class SourceItemModel {
        private final String title;
        private final String subtitle;
        private final String technicalPreview;
        private final boolean folder;
        private final AccessState accessState;

        public SourceItemModel(
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

        public String getTitle() {
            return title;
        }

        public String getSubtitle() {
            return subtitle;
        }

        public String getTechnicalPreview() {
            return technicalPreview;
        }

        public boolean isFolder() {
            return folder;
        }

        public AccessState getAccessState() {
            return accessState;
        }
    }

    public static final class ExternalSummaryModel {
        private final String headline;
        private final String subtitle;
        private final String technicalPreview;

        public ExternalSummaryModel(String headline, String subtitle, String technicalPreview) {
            this.headline = headline;
            this.subtitle = subtitle;
            this.technicalPreview = technicalPreview;
        }

        public String getHeadline() {
            return headline;
        }

        public String getSubtitle() {
            return subtitle;
        }

        public String getTechnicalPreview() {
            return technicalPreview;
        }
    }
}
