package com.regstar.obsidiannotification;

import android.content.Context;
import android.net.Uri;

import java.io.IOException;
import java.util.List;

/**
 * Single abstraction for the markdown source used by the app.
 *
 * <p>Every feature above this layer works with the same markdown documents and
 * task model regardless of whether files come from app-owned storage or from
 * an externally selected Obsidian folder.</p>
 */
interface TaskSource {
    TaskStorageMode getStorageMode();

    boolean isConfigured(Context context);

    Uri getPrimaryUri(Context context) throws IOException;

    List<NoteStore.NoteDocument> readDocuments(Context context) throws IOException;

    String getDisplayLabel(Context context);

    int getSourceCount(Context context);

    boolean canWrite(Context context);
}
