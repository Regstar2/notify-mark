package com.regstar.obsidiannotification;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final int REQUEST_OPEN_NOTE = 1001;
    private static final String PREFS_NAME = "obsidian_notification_prefs";
    private static final String KEY_NOTE_URI = "note_uri";
    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private SharedPreferences preferences;
    private Uri noteUri;
    private TextView statusText;
    private Button refreshButton;
    private LinearLayout taskList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String savedUri = preferences.getString(KEY_NOTE_URI, null);
        if (savedUri != null) {
            noteUri = Uri.parse(savedUri);
        }

        buildUi();

        if (noteUri == null) {
            setStatus("Выберите markdown-файл с задачами Obsidian.");
            renderEmptyState("Задачи появятся здесь после выбора заметки.");
        } else {
            readAndRenderNote();
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_OPEN_NOTE || resultCode != RESULT_OK || data == null) {
            return;
        }

        Uri selectedUri = data.getData();
        if (selectedUri == null) {
            return;
        }

        try {
            getContentResolver().takePersistableUriPermission(
                    selectedUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
            );
        } catch (SecurityException ignored) {
            // Some providers grant temporary read access only. The current session can still read it.
        }

        noteUri = selectedUri;
        preferences.edit().putString(KEY_NOTE_URI, selectedUri.toString()).apply();
        readAndRenderNote();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(20));
        root.setBackgroundColor(getColor(R.color.background));

        TextView title = new TextView(this);
        title.setText("ObsidianNotification");
        title.setTextColor(getColor(R.color.text_primary));
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView subtitle = new TextView(this);
        subtitle.setText("Читает чекбоксы из markdown-заметки и показывает активные задачи.");
        subtitle.setTextColor(getColor(R.color.text_secondary));
        subtitle.setTextSize(15);
        subtitle.setPadding(0, dp(8), 0, dp(16));
        root.addView(subtitle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, 0, 0, dp(12));

        Button chooseButton = new Button(this);
        chooseButton.setText(R.string.choose_note);
        chooseButton.setAllCaps(false);
        chooseButton.setOnClickListener(view -> openNotePicker());
        actions.addView(chooseButton, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        ));

        refreshButton = new Button(this);
        refreshButton.setText(R.string.refresh_note);
        refreshButton.setAllCaps(false);
        refreshButton.setEnabled(noteUri != null);
        refreshButton.setOnClickListener(view -> readAndRenderNote());
        LinearLayout.LayoutParams refreshParams = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        );
        refreshParams.setMargins(dp(10), 0, 0, 0);
        actions.addView(refreshButton, refreshParams);

        root.addView(actions, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        statusText = new TextView(this);
        statusText.setTextColor(getColor(R.color.text_secondary));
        statusText.setTextSize(14);
        statusText.setPadding(0, 0, 0, dp(12));
        root.addView(statusText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        ScrollView scrollView = new ScrollView(this);
        taskList = new LinearLayout(this);
        taskList.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(taskList, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        root.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        setContentView(root);
    }

    @SuppressWarnings("deprecation")
    private void openNotePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "text/markdown",
                "text/plain",
                "application/octet-stream"
        });
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_OPEN_NOTE);
    }

    private void readAndRenderNote() {
        if (noteUri == null) {
            setStatus("Файл не выбран.");
            renderEmptyState("Нажмите «Выбрать заметку».");
            return;
        }

        refreshButton.setEnabled(true);

        try {
            String markdown = readUriText(noteUri);
            List<ObsidianTask> tasks = TaskParser.parse(markdown);
            renderTasks(tasks);
            setStatus(String.format(
                    Locale.getDefault(),
                    "Файл: %s\nАктивных задач: %d\nОбновлено: %s",
                    getDisplayName(noteUri),
                    tasks.size(),
                    DATE_TIME_FORMAT.format(LocalDateTime.now())
            ));
        } catch (IOException | SecurityException exception) {
            setStatus("Не удалось прочитать файл: " + exception.getMessage());
            renderEmptyState("Проверьте доступ к заметке или выберите файл заново.");
        }
    }

    private String readUriText(Uri uri) throws IOException {
        StringBuilder builder = new StringBuilder();
        try (InputStream stream = getContentResolver().openInputStream(uri)) {
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

    private void renderTasks(List<ObsidianTask> tasks) {
        taskList.removeAllViews();
        if (tasks.isEmpty()) {
            renderEmptyState("В заметке нет активных строк вида - [ ].");
            return;
        }

        for (ObsidianTask task : tasks) {
            taskList.addView(createTaskView(task));
        }
    }

    private View createTaskView(ObsidianTask task) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setPadding(dp(14), dp(12), dp(14), dp(12));
        item.setBackground(createCardBackground());

        TextView title = new TextView(this);
        title.setText(task.getTitle());
        title.setTextColor(getColor(R.color.text_primary));
        title.setTextSize(17);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        item.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView meta = new TextView(this);
        meta.setText(formatMeta(task));
        meta.setTextColor(getColor(R.color.text_secondary));
        meta.setTextSize(14);
        meta.setPadding(0, dp(6), 0, 0);
        item.addView(meta, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, dp(10));
        item.setLayoutParams(params);
        return item;
    }

    private GradientDrawable createCardBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(0xFFFFFFFF);
        drawable.setCornerRadius(dp(8));
        drawable.setStroke(dp(1), 0xFFE1E7E5);
        return drawable;
    }

    private String formatMeta(ObsidianTask task) {
        StringBuilder builder = new StringBuilder();
        builder.append("Строка ").append(task.getLineNumber());

        if (task.getReminderAt() != null) {
            builder.append(" · напомнить ").append(DATE_TIME_FORMAT.format(task.getReminderAt()));
        } else {
            builder.append(" · время не указано");
        }

        if (task.getRepeatInterval() != null) {
            builder.append(" · повтор ").append(formatDuration(task.getRepeatInterval()));
        }

        return builder.toString();
    }

    private String formatDuration(Duration duration) {
        long minutes = duration.toMinutes();
        if (minutes % (24 * 60) == 0) {
            long days = minutes / (24 * 60);
            return days + " д.";
        }
        if (minutes % 60 == 0) {
            long hours = minutes / 60;
            return hours + " ч.";
        }
        return minutes + " мин.";
    }

    private void renderEmptyState(String message) {
        taskList.removeAllViews();

        TextView empty = new TextView(this);
        empty.setText(message);
        empty.setTextColor(getColor(R.color.text_secondary));
        empty.setTextSize(15);
        empty.setPadding(0, dp(12), 0, 0);
        taskList.addView(empty, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
    }

    private String getDisplayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(
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
        } catch (SecurityException ignored) {
            return uri.toString();
        }
        return uri.toString();
    }

    private void setStatus(String message) {
        statusText.setText(message);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
