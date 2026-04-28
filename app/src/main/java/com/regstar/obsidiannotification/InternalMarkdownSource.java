package com.regstar.obsidiannotification;

import android.content.Context;
import android.net.Uri;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

final class InternalMarkdownSource implements TaskSource {
    static final String INTERNAL_FOLDER_NAME = "internal_markdown";
    static final String DEFAULT_FILE_NAME = "tasks.md";
    private static final String DEFAULT_FILE_TEMPLATE = "## Задачи\n\n";

    @Override
    public TaskStorageMode getStorageMode() {
        return TaskStorageMode.INTERNAL_MARKDOWN_STORAGE;
    }

    @Override
    public boolean isConfigured(Context context) {
        try {
            ensureInitialized(context);
            return true;
        } catch (IOException exception) {
            return false;
        }
    }

    @Override
    public Uri getPrimaryUri(Context context) throws IOException {
        return Uri.fromFile(ensureDefaultFile(context));
    }

    @Override
    public List<NoteStore.NoteDocument> readDocuments(Context context) throws IOException {
        ensureInitialized(context);
        NoteScanSettings scanSettings = NoteScanSettings.load(context);
        List<NoteStore.NoteDocument> documents = new ArrayList<>();
        scanDirectory(context, internalRoot(context), "", scanSettings, documents);
        if (documents.isEmpty()) {
            File defaultFile = ensureDefaultFile(context);
            documents.add(new NoteStore.NoteDocument(
                    defaultFile.getName(),
                    Uri.fromFile(defaultFile),
                    NoteStore.readMarkdown(context, Uri.fromFile(defaultFile))
            ));
        }
        return documents;
    }

    @Override
    public String getDisplayLabel(Context context) {
        return "Встроенное хранилище";
    }

    @Override
    public int getSourceCount(Context context) {
        return 1;
    }

    @Override
    public boolean canWrite(Context context) {
        return true;
    }

    File ensureDefaultFile(Context context) throws IOException {
        File root = internalRoot(context);
        if (!root.exists() && !root.mkdirs()) {
            throw new IOException("не удалось создать встроенную папку markdown");
        }

        File defaultFile = new File(root, DEFAULT_FILE_NAME);
        if (!defaultFile.exists()) {
            NoteStore.writeMarkdown(context, Uri.fromFile(defaultFile), DEFAULT_FILE_TEMPLATE);
        }
        return defaultFile;
    }

    void ensureInitialized(Context context) throws IOException {
        ensureDefaultFile(context);
    }

    File internalRoot(Context context) {
        return new File(context.getFilesDir(), INTERNAL_FOLDER_NAME);
    }

    private void scanDirectory(
            Context context,
            File directory,
            String parentPath,
            NoteScanSettings scanSettings,
            List<NoteStore.NoteDocument> documents
    ) throws IOException {
        if (documents.size() >= scanSettings.getMaxFiles()) {
            return;
        }
        File[] children = directory.listFiles();
        if (children == null) {
            return;
        }
        Arrays.sort(children, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        for (File child : children) {
            if (documents.size() >= scanSettings.getMaxFiles()) {
                return;
            }
            String relativePath = parentPath.isEmpty()
                    ? child.getName()
                    : parentPath + "/" + child.getName();
            if (child.isDirectory()) {
                if (!scanSettings.shouldSkipDirectory(relativePath)) {
                    scanDirectory(context, child, relativePath, scanSettings, documents);
                }
                continue;
            }
            if (!isMarkdownDocument(child.getName())
                    || !scanSettings.shouldReadFile(relativePath, child.getName())) {
                continue;
            }
            Uri uri = Uri.fromFile(child);
            documents.add(new NoteStore.NoteDocument(
                    relativePath,
                    uri,
                    NoteStore.readMarkdown(context, uri)
            ));
        }
    }

    private boolean isMarkdownDocument(String displayName) {
        String lowerName = displayName == null ? "" : displayName.toLowerCase(Locale.ROOT);
        return lowerName.endsWith(".md") || lowerName.endsWith(".markdown");
    }
}
