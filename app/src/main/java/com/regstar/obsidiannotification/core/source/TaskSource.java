package com.regstar.obsidiannotification.core.source;

import com.regstar.obsidiannotification.core.reminders.*;
import com.regstar.obsidiannotification.core.tasks.*;
import com.regstar.obsidiannotification.prefs.*;
import com.regstar.obsidiannotification.support.ErrorLog;

import android.content.Context;
import android.net.Uri;

import java.io.IOException;
import java.util.List;

/**
 * Single abstraction for the markdown source used by the app.
 *
 * <p>Every feature above this layer works with the same markdown documents and
 * task model regardless of whether files come from app-owned storage or from
 * externally selected markdown files and folders.</p>
 */
interface TaskSource {
    TaskStorageMode getStorageMode();

    boolean isConfigured(Context context);

    /**
     * Returns the primary source URI used for summaries and editor entry
     * points. Multi-source configurations still expose one representative URI
     * here for compatibility with existing flows.
     */
    Uri getPrimaryUri(Context context) throws IOException;

    /**
     * Reads markdown documents for the current source mode.
     */
    List<NoteStore.NoteDocument> readDocuments(Context context) throws IOException;

    String getDisplayLabel(Context context);

    int getSourceCount(Context context);

    boolean canWrite(Context context);
}
