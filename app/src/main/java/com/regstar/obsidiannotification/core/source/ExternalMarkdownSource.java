package com.regstar.obsidiannotification.core.source;

import com.regstar.obsidiannotification.core.reminders.*;
import com.regstar.obsidiannotification.core.tasks.*;
import com.regstar.obsidiannotification.prefs.*;
import com.regstar.obsidiannotification.support.ErrorLog;

import android.content.Context;
import android.net.Uri;

import java.io.IOException;
import java.util.List;

final class ExternalMarkdownSource implements TaskSource {
    @Override
    public TaskStorageMode getStorageMode() {
        return TaskStorageMode.EXTERNAL_MARKDOWN_STORAGE;
    }

    @Override
    public boolean isConfigured(Context context) {
        return NoteStore.hasStoredExternalSources(context);
    }

    @Override
    public Uri getPrimaryUri(Context context) {
        return NoteStore.getStoredExternalPrimaryUri(context);
    }

    @Override
    public List<NoteStore.NoteDocument> readDocuments(Context context) throws IOException {
        return NoteStore.readStoredExternalDocuments(context);
    }

    @Override
    public String getDisplayLabel(Context context) {
        return SourceDisplayNameResolver
                .summarizeExternalSources(context, NoteStore.getStoredExternalSources(context))
                .getHeadline();
    }

    @Override
    public int getSourceCount(Context context) {
        return NoteStore.getStoredExternalSourceCount(context);
    }

    @Override
    public boolean canWrite(Context context) {
        return NoteStore.canWriteStoredExternalSources(context);
    }
}
