package com.regstar.obsidiannotification;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public final class SettingsActivity extends Activity {
    private static final int REQUEST_OPEN_NOTE = 3001;
    private static final int REQUEST_OPEN_FOLDER = 3002;

    private TextView statusText;
    private Button activeFilterButton;
    private EditText dueKeywordInput;
    private EditText repeatKeywordInput;
    private EditText repeatUntilDoneKeywordInput;
    private EditText tagKeywordInput;
    private EditText priorityKeywordInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        updateStatus();
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }

        Uri selectedUri = data.getData();
        persistReadPermission(data, selectedUri);

        if (requestCode == REQUEST_OPEN_NOTE) {
            NoteStore.saveNoteUri(this, selectedUri);
        } else if (requestCode == REQUEST_OPEN_FOLDER) {
            NoteStore.saveFolderUri(this, selectedUri);
        } else {
            return;
        }

        NoteChangeMonitor.ensureScheduled(this);
        rescheduleAll();
        updateStatus();
    }

    private void buildUi() {
        ScrollView scrollView = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(20));
        root.setBackgroundColor(getColor(R.color.background));
        scrollView.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView title = new TextView(this);
        title.setText("Настройки");
        title.setTextSize(24);
        title.setTextColor(getColor(R.color.text_primary));
        root.addView(title, fullWidth());

        statusText = new TextView(this);
        statusText.setTextSize(15);
        statusText.setTextColor(getColor(R.color.text_secondary));
        statusText.setPadding(0, dp(12), 0, dp(12));
        root.addView(statusText, fullWidth());

        Button chooseNoteButton = createButton("Выбрать одну заметку");
        chooseNoteButton.setOnClickListener(view -> openNotePicker());
        root.addView(chooseNoteButton, fullWidthWithBottomMargin());

        Button chooseFolderButton = createButton("Выбрать папку с заметками");
        chooseFolderButton.setOnClickListener(view -> openFolderPicker());
        root.addView(chooseFolderButton, fullWidthWithBottomMargin());

        activeFilterButton = createButton("");
        activeFilterButton.setOnClickListener(view -> {
            UserPreferences.setActiveOnly(this, !UserPreferences.isActiveOnly(this));
            updateActiveFilterButton();
            updateStatus();
        });
        updateActiveFilterButton();
        root.addView(activeFilterButton, fullWidthWithBottomMargin());

        addFormatSettings(root);

        Button rescheduleButton = createButton("Перепланировать все уведомления");
        rescheduleButton.setOnClickListener(view -> rescheduleAll());
        root.addView(rescheduleButton, fullWidthWithBottomMargin());

        Button testNotificationButton = createButton("Отправить тестовое уведомление");
        testNotificationButton.setOnClickListener(view -> sendTestNotification());
        root.addView(testNotificationButton, fullWidthWithBottomMargin());

        Button closeButton = createButton("Закрыть");
        closeButton.setOnClickListener(view -> finish());
        root.addView(closeButton, fullWidthWithBottomMargin());

        setContentView(scrollView);
    }

    private void addFormatSettings(LinearLayout root) {
        TextView formatTitle = new TextView(this);
        formatTitle.setText("Формат задач");
        formatTitle.setTextSize(18);
        formatTitle.setTextColor(getColor(R.color.text_primary));
        formatTitle.setPadding(0, dp(12), 0, dp(6));
        root.addView(formatTitle, fullWidth());

        TaskFormatSettings settings = TaskFormatSettings.load(this);
        dueKeywordInput = addKeywordInput(root, "Ключ времени", settings.getDueKeyword());
        repeatKeywordInput = addKeywordInput(root, "Ключ повтора", settings.getRepeatKeyword());
        repeatUntilDoneKeywordInput = addKeywordInput(
                root,
                "Ключ повтора до выполнения",
                settings.getRepeatUntilDoneKeyword()
        );
        tagKeywordInput = addKeywordInput(root, "Ключ тегов", settings.getTagKeyword());
        priorityKeywordInput = addKeywordInput(
                root,
                "Ключ приоритета",
                settings.getPriorityKeyword()
        );

        Button saveFormatButton = createButton("Сохранить ключевые слова");
        saveFormatButton.setOnClickListener(view -> saveFormatSettings());
        root.addView(saveFormatButton, fullWidthWithBottomMargin());

        Button resetFormatButton = createButton("Сбросить ключевые слова");
        resetFormatButton.setOnClickListener(view -> resetFormatSettings());
        root.addView(resetFormatButton, fullWidthWithBottomMargin());
    }

    private EditText addKeywordInput(LinearLayout root, String label, String value) {
        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextSize(14);
        labelView.setTextColor(getColor(R.color.text_secondary));
        root.addView(labelView, fullWidth());

        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(value);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setSelectAllOnFocus(true);
        root.addView(input, fullWidthWithBottomMargin());
        return input;
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

    @SuppressWarnings("deprecation")
    private void openFolderPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_OPEN_FOLDER);
    }

    private void persistReadPermission(Intent data, Uri selectedUri) {
        try {
            int persistableFlags = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
            if (persistableFlags == 0) {
                persistableFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION;
            }
            getContentResolver().takePersistableUriPermission(selectedUri, persistableFlags);
        } catch (SecurityException ignored) {
            // Some providers grant only temporary read access.
        }
    }

    private void rescheduleAll() {
        NoteChangeMonitor.NoteSyncResult result = NoteChangeMonitor.syncNow(this, true);
        NoteChangeMonitor.ensureScheduled(this);
        if (result.isSuccess()) {
            Toast.makeText(
                    this,
                    "Перепланировано задач: " + result.getTaskCount(),
                    Toast.LENGTH_SHORT
            ).show();
        } else {
            String message = result.isRestoredFromCache()
                    ? "Источник не прочитан, уведомления восстановлены из кэша"
                    : "Не удалось прочитать источник: " + result.getErrorMessage();
            Toast.makeText(
                    this,
                    message,
                    Toast.LENGTH_LONG
            ).show();
        }
        updateStatus();
    }

    private void saveFormatSettings() {
        TaskFormatSettings settings = TaskFormatSettings.fromValues(
                dueKeywordInput.getText().toString(),
                repeatKeywordInput.getText().toString(),
                repeatUntilDoneKeywordInput.getText().toString(),
                tagKeywordInput.getText().toString(),
                priorityKeywordInput.getText().toString()
        );
        if (settings.hasDuplicateKeywords()) {
            Toast.makeText(
                    this,
                    "Ключевые слова должны быть разными",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        TaskFormatSettings.save(this, settings);
        populateFormatInputs(settings);
        Toast.makeText(this, "Формат задач сохранен", Toast.LENGTH_SHORT).show();
        rescheduleAll();
    }

    private void resetFormatSettings() {
        TaskFormatSettings.reset(this);
        populateFormatInputs(TaskFormatSettings.defaults());
        Toast.makeText(this, "Формат задач сброшен", Toast.LENGTH_SHORT).show();
        rescheduleAll();
    }

    private void populateFormatInputs(TaskFormatSettings settings) {
        dueKeywordInput.setText(settings.getDueKeyword());
        repeatKeywordInput.setText(settings.getRepeatKeyword());
        repeatUntilDoneKeywordInput.setText(settings.getRepeatUntilDoneKeyword());
        tagKeywordInput.setText(settings.getTagKeyword());
        priorityKeywordInput.setText(settings.getPriorityKeyword());
    }

    private void sendTestNotification() {
        boolean sent = TestNotificationSender.send(this);
        Toast.makeText(
                this,
                sent ? "Тестовое уведомление отправлено" : "Нет разрешения на уведомления",
                Toast.LENGTH_SHORT
        ).show();
    }

    private void updateStatus() {
        String cachedAt = TaskCache.getSavedAt(this);
        String latestError = ErrorLog.latest(this);
        TaskFormatSettings formatSettings = TaskFormatSettings.load(this);
        StringBuilder status = new StringBuilder("Источник: " + NoteStore.sourceLabel(this)
                + "\nФильтр: " + (UserPreferences.isActiveOnly(this) ? "только активные" : "все задачи")
                + "\nТочные напоминания: "
                + (ReminderScheduler.canScheduleExactAlarms(this) ? "разрешены" : "не разрешены")
                + "\nФормат: " + formatSettings.formatForStatus()
                + "\nЛокальный кэш задач: " + TaskCache.getCachedTaskCount(this)
                + (cachedAt == null ? "" : "\nКэш обновлен: " + cachedAt));
        if (latestError != null) {
            status.append("\nПоследняя ошибка: ").append(latestError);
        }
        statusText.setText(status.toString());
    }

    private void updateActiveFilterButton() {
        activeFilterButton.setText(UserPreferences.isActiveOnly(this)
                ? "Показать все задачи"
                : "Показывать только активные");
    }

    private Button createButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        return button;
    }

    private LinearLayout.LayoutParams fullWidth() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    private LinearLayout.LayoutParams fullWidthWithBottomMargin() {
        LinearLayout.LayoutParams params = fullWidth();
        params.setMargins(0, 0, 0, dp(10));
        return params;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
