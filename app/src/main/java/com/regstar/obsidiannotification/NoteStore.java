package com.regstar.obsidiannotification;

import android.content.Context;
import android.net.Uri;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class NoteStore {
    private static final String PREFS_NAME = "obsidian_notification_prefs";
    private static final String KEY_NOTE_URI = "note_uri";

    private NoteStore() {
    }

    public static Uri getSavedNoteUri(Context context) {
        String savedUri = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_NOTE_URI, null);
        return savedUri == null ? null : Uri.parse(savedUri);
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
                .putString(KEY_NOTE_URI, uri.toString())
                .apply();
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

    public static List<ObsidianTask> readTasks(Context context) throws IOException {
        return TaskParser.parse(readMarkdown(context, requireSavedNoteUri(context)));
    }

    public static ObsidianTask findActiveTask(Context context, String taskKey) throws IOException {
        for (ObsidianTask task : readTasks(context)) {
            if (task.getTaskKey().equals(taskKey)) {
                return task;
            }
        }
        return null;
    }
}
