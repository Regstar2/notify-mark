package com.regstar.obsidiannotification.core.source;

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

    /**
     * Resolves the persisted storage mode or picks a safe default for first
     * launch and legacy installs.
     */
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

    /**
     * Switches the app into built-in markdown storage and ensures the internal
     * folder exists before the mode is persisted.
     */
    public static void useInternalStorage(Context context) throws IOException {
        INTERNAL_SOURCE.ensureInitialized(context);
        persistStorageMode(context, TaskStorageMode.INTERNAL_MARKDOWN_STORAGE);
    }

    /**
     * Switches the app to externally managed markdown sources.
     *
     * <p>This call only changes the active mode. It does not create or migrate
     * sources on its own.</p>
     */
    public static void useExternalStorage(Context context) {
        persistStorageMode(context, TaskStorageMode.EXTERNAL_MARKDOWN_STORAGE);
    }

    /**
     * Returns the currently active source implementation.
     */
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
            ErrorLog.record(context, "РќРµ СѓРґР°Р»РѕСЃСЊ РѕРїСЂРµРґРµР»РёС‚СЊ Р°РєС‚РёРІРЅС‹Р№ markdown-РёСЃС‚РѕС‡РЅРёРє", exception);
            return null;
        }
    }

    /**
     * Ensures the selected mode is ready for normal reads.
     *
     * <p>Today this mainly matters for built-in storage, which lazily creates
     * its default markdown file.</p>
     */
    public static void ensureReady(Context context) throws IOException {
        if (getStorageMode(context) == TaskStorageMode.INTERNAL_MARKDOWN_STORAGE) {
            INTERNAL_SOURCE.ensureInitialized(context);
        }
    }

    /**
     * Human-readable label for the active storage mode.
     */
    public static String storageModeLabel(Context context) {
        return getStorageMode(context) == TaskStorageMode.INTERNAL_MARKDOWN_STORAGE
                ? "Р’СЃС‚СЂРѕРµРЅРЅРѕРµ С…СЂР°РЅРёР»РёС‰Рµ"
                : "Р’РЅРµС€РЅРёРµ markdown-С„Р°Р№Р»С‹";
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

    /**
     * Warning shown when switching storage mode without moving user markdown
     * between modes.
     */
    public static String switchWithoutMigrationWarning(TaskStorageMode targetMode) {
        if (targetMode == TaskStorageMode.INTERNAL_MARKDOWN_STORAGE) {
            return "РџРµСЂРµРєР»СЋС‡РµРЅРёРµ РЅР° РІСЃС‚СЂРѕРµРЅРЅРѕРµ С…СЂР°РЅРёР»РёС‰Рµ РЅРµ РїРµСЂРµРЅРѕСЃРёС‚ Р·Р°РґР°С‡Рё Р°РІС‚РѕРјР°С‚РёС‡РµСЃРєРё. "
                    + "Р’РЅРµС€РЅРёРµ markdown-С„Р°Р№Р»С‹ РѕСЃС‚Р°РЅСѓС‚СЃСЏ Р±РµР· РёР·РјРµРЅРµРЅРёР№.";
        }
        return "РџРµСЂРµРєР»СЋС‡РµРЅРёРµ РЅР° РІРЅРµС€РЅРёРµ markdown-С„Р°Р№Р»С‹ РЅРµ РїРµСЂРµРЅРѕСЃРёС‚ РІСЃС‚СЂРѕРµРЅРЅС‹Рµ Р·Р°РґР°С‡Рё Р°РІС‚РѕРјР°С‚РёС‡РµСЃРєРё. "
                + "Р•СЃР»Рё РїРµСЂРµРЅРѕСЃ РЅСѓР¶РµРЅ, markdown-С„Р°Р№Р»С‹ РїРѕРєР° РЅСѓР¶РЅРѕ СЃРєРѕРїРёСЂРѕРІР°С‚СЊ РІСЂСѓС‡РЅСѓСЋ.";
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
