package com.regstar.obsidiannotification.core.source;

import com.regstar.obsidiannotification.R;
import com.regstar.obsidiannotification.core.reminders.*;
import com.regstar.obsidiannotification.core.tasks.*;
import com.regstar.obsidiannotification.prefs.*;
import com.regstar.obsidiannotification.support.ErrorLog;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import java.io.IOException;

/**
 * Resolves and persists the currently active markdown storage mode.
 *
 * <p>The rest of the app should ask this manager for the active source instead
 * of assuming that tasks always come from externally selected SAF URIs.</p>
 */
public final class TaskSourceManager {
    private static final String PREFS_NAME = "obsidian_notification_task_source";
    private static final String KEY_STORAGE_MODE = "storage_mode";

    private static final InternalMarkdownSource INTERNAL_SOURCE = new InternalMarkdownSource();
    private static final ExternalMarkdownSource EXTERNAL_SOURCE = new ExternalMarkdownSource();

    private TaskSourceManager() {
    }

    public static TaskStorageMode getStorageMode(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        TaskStorageMode storedMode = TaskStorageMode.fromName(preferences.getString(KEY_STORAGE_MODE, null));
        return resolveInitialMode(storedMode, NoteStore.hasStoredExternalSources(context));
    }

    static TaskStorageMode resolveInitialMode(
            TaskStorageMode storedMode,
            boolean hasStoredExternalSources
    ) {
        if (storedMode != null) {
            return storedMode;
        }
        return hasStoredExternalSources
                ? TaskStorageMode.EXTERNAL_MARKDOWN_STORAGE
                : TaskStorageMode.INTERNAL_MARKDOWN_STORAGE;
    }

    public static void useInternalStorage(Context context) throws IOException {
        INTERNAL_SOURCE.ensureInitialized(context);
        persistStorageMode(context, TaskStorageMode.INTERNAL_MARKDOWN_STORAGE);
    }

    public static void useExternalStorage(Context context) {
        persistStorageMode(context, TaskStorageMode.EXTERNAL_MARKDOWN_STORAGE);
    }

    public static TaskSource currentSource(Context context) {
        return getStorageMode(context) == TaskStorageMode.EXTERNAL_MARKDOWN_STORAGE
                ? EXTERNAL_SOURCE
                : INTERNAL_SOURCE;
    }

    public static boolean hasReadableSource(Context context) {
        return currentSource(context).isConfigured(context);
    }

    public static Uri getActiveSourceUri(Context context) {
        try {
            return currentSource(context).getPrimaryUri(context);
        } catch (IOException exception) {
            ErrorLog.record(context, context.getString(R.string.source_active_lookup_error), exception);
            return null;
        }
    }

    public static void ensureReady(Context context) throws IOException {
        if (getStorageMode(context) == TaskStorageMode.INTERNAL_MARKDOWN_STORAGE) {
            INTERNAL_SOURCE.ensureInitialized(context);
        }
    }

    public static String storageModeLabel(Context context) {
        return getStorageMode(context) == TaskStorageMode.INTERNAL_MARKDOWN_STORAGE
                ? context.getString(R.string.storage_internal_label)
                : context.getString(R.string.storage_external_label);
    }

    public static String activeSourceLabel(Context context) {
        return currentSource(context).getDisplayLabel(context);
    }

    public static int activeSourceCount(Context context) {
        return currentSource(context).getSourceCount(context);
    }

    public static boolean canWriteActiveSource(Context context) {
        return currentSource(context).canWrite(context);
    }

    public static boolean hasExternalSources(Context context) {
        return NoteStore.hasStoredExternalSources(context);
    }

    public static String switchWithoutMigrationWarning(Context context, TaskStorageMode targetMode) {
        if (targetMode == TaskStorageMode.INTERNAL_MARKDOWN_STORAGE) {
            return context.getString(R.string.storage_switch_internal_warning);
        }
        return context.getString(R.string.storage_switch_external_warning);
    }

    public static Uri getInternalDefaultFileUri(Context context) throws IOException {
        return Uri.fromFile(INTERNAL_SOURCE.ensureDefaultFile(context));
    }

    public static String internalFolderSummary(Context context) {
        return INTERNAL_SOURCE.internalRoot(context).getAbsolutePath();
    }
    private static void persistStorageMode(Context context, TaskStorageMode mode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_STORAGE_MODE, mode.name())
                .apply();
    }
}
