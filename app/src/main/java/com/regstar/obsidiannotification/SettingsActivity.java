package com.regstar.obsidiannotification;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public final class SettingsActivity extends Activity {
    private static final int REQUEST_REPLACE_NOTES = 3001;
    private static final int REQUEST_REPLACE_FOLDER = 3002;
    private static final int REQUEST_ADD_NOTES = 3003;
    private static final int REQUEST_ADD_FOLDER = 3004;

    private TextView statusText;
    private Button activeFilterButton;
    private EditText dueKeywordInput;
    private EditText repeatKeywordInput;
    private EditText repeatUntilDoneKeywordInput;
    private EditText tagKeywordInput;
    private EditText priorityKeywordInput;
    private EditText groupKeywordInput;
    private EditText snoozeMinutesInput;
    private EditText includePatternsInput;
    private EditText excludePatternsInput;
    private EditText maxFilesInput;
    private CheckBox recordSnoozeCountCheckbox;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemePreferences.apply(this);
        super.onCreate(savedInstanceState);
        buildUi();
        updateStatus();
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) {
            return;
        }

        List<Uri> selectedUris = selectedUris(data);
        if (selectedUris.isEmpty()) {
            return;
        }
        for (Uri selectedUri : selectedUris) {
            persistReadPermission(data, selectedUri);
        }

        if (requestCode == REQUEST_REPLACE_NOTES) {
            NoteStore.saveNoteUris(this, selectedUris);
        } else if (requestCode == REQUEST_REPLACE_FOLDER) {
            NoteStore.saveFolderUri(this, selectedUris.get(0));
        } else if (requestCode == REQUEST_ADD_NOTES) {
            NoteStore.addNoteUris(this, selectedUris);
        } else if (requestCode == REQUEST_ADD_FOLDER) {
            NoteStore.addFolderUri(this, selectedUris.get(0));
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

        Button chooseNoteButton = createButton("Выбрать заметку или несколько заметок");
        chooseNoteButton.setOnClickListener(view -> openNotePicker(REQUEST_REPLACE_NOTES));
        root.addView(chooseNoteButton, fullWidthWithBottomMargin());

        Button chooseFolderButton = createButton("Выбрать папку с заметками");
        chooseFolderButton.setOnClickListener(view -> openFolderPicker(REQUEST_REPLACE_FOLDER));
        root.addView(chooseFolderButton, fullWidthWithBottomMargin());

        Button addNoteButton = createButton("Добавить заметку");
        addNoteButton.setOnClickListener(view -> openNotePicker(REQUEST_ADD_NOTES));
        root.addView(addNoteButton, fullWidthWithBottomMargin());

        Button addFolderButton = createButton("Добавить папку");
        addFolderButton.setOnClickListener(view -> openFolderPicker(REQUEST_ADD_FOLDER));
        root.addView(addFolderButton, fullWidthWithBottomMargin());

        Button clearSourcesButton = createButton("Очистить источники");
        clearSourcesButton.setOnClickListener(view -> clearSources());
        root.addView(clearSourcesButton, fullWidthWithBottomMargin());

        activeFilterButton = createButton("");
        activeFilterButton.setOnClickListener(view -> {
            UserPreferences.setActiveOnly(this, !UserPreferences.isActiveOnly(this));
            updateActiveFilterButton();
            updateStatus();
        });
        updateActiveFilterButton();
        root.addView(activeFilterButton, fullWidthWithBottomMargin());

        addEditingSettings(root);
        addAppearanceSettings(root);
        addFormatSettings(root);
        addScanSettings(root);
        addNotificationActionSettings(root);
        addDebugSettings(root);

        Button closeButton = createButton("Закрыть");
        closeButton.setOnClickListener(view -> finish());
        root.addView(closeButton, fullWidthWithBottomMargin());

        setContentView(scrollView);
    }

    private void addEditingSettings(LinearLayout root) {
        TextView editTitle = new TextView(this);
        editTitle.setText("Поведение");
        editTitle.setTextSize(18);
        editTitle.setTextColor(getColor(R.color.text_primary));
        editTitle.setPadding(0, dp(12), 0, dp(6));
        root.addView(editTitle, fullWidth());

        TextView editDescription = new TextView(this);
        editDescription.setText("Режим редактирования задач внутри приложения. В UI-режиме preview markdown-строки остается видимым.");
        editDescription.setTextSize(14);
        editDescription.setTextColor(getColor(R.color.text_secondary));
        editDescription.setPadding(0, 0, 0, dp(8));
        root.addView(editDescription, fullWidth());

        Button uiModeButton = createButton(EditPreferences.MODE_UI.equals(EditPreferences.getEditMode(this))
                ? "Режим: UI с записью в markdown"
                : "Выбрать UI-редактирование");
        uiModeButton.setOnClickListener(view -> setEditMode(EditPreferences.MODE_UI));
        root.addView(uiModeButton, fullWidthWithBottomMargin());

        Button markdownModeButton = createButton(EditPreferences.MODE_MARKDOWN.equals(EditPreferences.getEditMode(this))
                ? "Режим: прямое редактирование markdown"
                : "Выбрать markdown-редактирование");
        markdownModeButton.setOnClickListener(view -> setEditMode(EditPreferences.MODE_MARKDOWN));
        root.addView(markdownModeButton, fullWidthWithBottomMargin());
    }

    private void addAppearanceSettings(LinearLayout root) {
        TextView appearanceTitle = new TextView(this);
        appearanceTitle.setText("Внешний вид");
        appearanceTitle.setTextSize(18);
        appearanceTitle.setTextColor(getColor(R.color.text_primary));
        appearanceTitle.setPadding(0, dp(12), 0, dp(6));
        root.addView(appearanceTitle, fullWidth());

        TextView appearanceDescription = new TextView(this);
        appearanceDescription.setText("Тема приложения: системная, светлая или темная.");
        appearanceDescription.setTextSize(14);
        appearanceDescription.setTextColor(getColor(R.color.text_secondary));
        appearanceDescription.setPadding(0, 0, 0, dp(8));
        root.addView(appearanceDescription, fullWidth());

        Button systemThemeButton = createButton(themeButtonText(ThemePreferences.MODE_SYSTEM, "Системная тема"));
        systemThemeButton.setOnClickListener(view -> setThemeMode(ThemePreferences.MODE_SYSTEM));
        root.addView(systemThemeButton, fullWidthWithBottomMargin());

        Button lightThemeButton = createButton(themeButtonText(ThemePreferences.MODE_LIGHT, "Светлая тема"));
        lightThemeButton.setOnClickListener(view -> setThemeMode(ThemePreferences.MODE_LIGHT));
        root.addView(lightThemeButton, fullWidthWithBottomMargin());

        Button darkThemeButton = createButton(themeButtonText(ThemePreferences.MODE_DARK, "Темная тема"));
        darkThemeButton.setOnClickListener(view -> setThemeMode(ThemePreferences.MODE_DARK));
        root.addView(darkThemeButton, fullWidthWithBottomMargin());
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
        groupKeywordInput = addKeywordInput(root, "Ключ группы", settings.getGroupKeyword());

        Button saveFormatButton = createButton("Сохранить");
        saveFormatButton.setOnClickListener(view -> saveFormatSettings());
        root.addView(saveFormatButton, fullWidthWithBottomMargin());

        Button resetFormatButton = createButton("Сбросить");
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

    private void addScanSettings(LinearLayout root) {
        TextView scanTitle = new TextView(this);
        scanTitle.setText("Поиск задач в файлах");
        scanTitle.setTextSize(18);
        scanTitle.setTextColor(getColor(R.color.text_primary));
        scanTitle.setPadding(0, dp(12), 0, dp(6));
        root.addView(scanTitle, fullWidth());

        NoteScanSettings settings = NoteScanSettings.load(this);
        includePatternsInput = addKeywordInput(
                root,
                "Искать файлы",
                settings.getIncludePatternsText()
        );
        excludePatternsInput = addKeywordInput(
                root,
                "Исключать файлы и папки",
                settings.getExcludePatternsText()
        );
        maxFilesInput = addKeywordInput(
                root,
                "Максимум файлов за сканирование",
                String.valueOf(settings.getMaxFiles())
        );
        maxFilesInput.setInputType(InputType.TYPE_CLASS_NUMBER);

        Button saveScanButton = createButton("Сохранить поиск задач");
        saveScanButton.setOnClickListener(view -> saveScanSettings());
        root.addView(saveScanButton, fullWidthWithBottomMargin());

        Button resetScanButton = createButton("Сбросить поиск задач");
        resetScanButton.setOnClickListener(view -> resetScanSettings());
        root.addView(resetScanButton, fullWidthWithBottomMargin());
    }

    private void addNotificationActionSettings(LinearLayout root) {
        TextView actionTitle = new TextView(this);
        actionTitle.setText("Действия из уведомления");
        actionTitle.setTextSize(18);
        actionTitle.setTextColor(getColor(R.color.text_primary));
        actionTitle.setPadding(0, dp(12), 0, dp(6));
        root.addView(actionTitle, fullWidth());

        snoozeMinutesInput = addKeywordInput(
                root,
                "Отложить на минут",
                String.valueOf(ActionPreferences.getSnoozeMinutes(this))
        );
        snoozeMinutesInput.setInputType(InputType.TYPE_CLASS_NUMBER);

        recordSnoozeCountCheckbox = new CheckBox(this);
        recordSnoozeCountCheckbox.setText("Записывать количество отложений в заметку");
        recordSnoozeCountCheckbox.setTextColor(getColor(R.color.text_secondary));
        recordSnoozeCountCheckbox.setChecked(ActionPreferences.shouldRecordSnoozeCount(this));
        root.addView(recordSnoozeCountCheckbox, fullWidthWithBottomMargin());

        Button saveActionSettingsButton = createButton("Сохранить действия уведомления");
        saveActionSettingsButton.setOnClickListener(view -> saveActionSettings());
        root.addView(saveActionSettingsButton, fullWidthWithBottomMargin());
    }

    private void addDebugSettings(LinearLayout root) {
        TextView debugTitle = new TextView(this);
        debugTitle.setText("Отладка");
        debugTitle.setTextSize(18);
        debugTitle.setTextColor(getColor(R.color.text_primary));
        debugTitle.setPadding(0, dp(12), 0, dp(6));
        root.addView(debugTitle, fullWidth());

        TextView debugDescription = new TextView(this);
        debugDescription.setText("Кнопки работают с первой активной задачей в выбранной заметке.");
        debugDescription.setTextSize(14);
        debugDescription.setTextColor(getColor(R.color.text_secondary));
        debugDescription.setPadding(0, 0, 0, dp(8));
        root.addView(debugDescription, fullWidth());

        Button debugActionsButton = createButton("Открыть отладочные действия");
        debugActionsButton.setOnClickListener(view -> showDebugActions());
        root.addView(debugActionsButton, fullWidthWithBottomMargin());
    }

    private void showDebugActions() {
        String[] actions = new String[]{
                "Уведомление сейчас",
                "Выполнить первую задачу",
                "Отложить первую задачу",
                "Перепланировать все",
                "Тестовое уведомление"
        };
        new android.app.AlertDialog.Builder(this)
                .setTitle("Отладка")
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) {
                        runDebugAction(DebugReminderActions.showImmediateReminder(this));
                    } else if (which == 1) {
                        runDebugAction(DebugReminderActions.markFirstTaskDone(this));
                    } else if (which == 2) {
                        runDebugAction(DebugReminderActions.snoozeFirstTask(this));
                    } else if (which == 3) {
                        rescheduleAll();
                    } else if (which == 4) {
                        sendTestNotification();
                    }
                })
                .show();
    }

    @SuppressWarnings("deprecation")
    private void openNotePicker(int requestCode) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "text/markdown",
                "text/plain",
                "application/octet-stream"
        });
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(intent, requestCode);
    }

    @SuppressWarnings("deprecation")
    private void openFolderPicker(int requestCode) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        startActivityForResult(intent, requestCode);
    }

    private void persistReadPermission(Intent data, Uri selectedUri) {
        try {
            int persistableFlags = data.getFlags()
                    & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            if (persistableFlags == 0) {
                persistableFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
            }
            getContentResolver().takePersistableUriPermission(selectedUri, persistableFlags);
        } catch (SecurityException ignored) {
            // Some providers grant only temporary read access.
        }
    }

    private List<Uri> selectedUris(Intent data) {
        List<Uri> uris = new ArrayList<>();
        ClipData clipData = data.getClipData();
        if (clipData != null) {
            for (int i = 0; i < clipData.getItemCount(); i++) {
                Uri uri = clipData.getItemAt(i).getUri();
                if (uri != null) {
                    uris.add(uri);
                }
            }
        }

        Uri dataUri = data.getData();
        if (dataUri != null && !uris.contains(dataUri)) {
            uris.add(dataUri);
        }
        return uris;
    }

    private void clearSources() {
        NoteStore.clearSources(this);
        ReminderScheduler.cancelScheduled(this);
        NoteChangeMonitor.cancel(this);
        Toast.makeText(this, "Источники очищены", Toast.LENGTH_SHORT).show();
        updateStatus();
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

    private void saveActionSettings() {
        int snoozeMinutes;
        try {
            snoozeMinutes = Integer.parseInt(snoozeMinutesInput.getText().toString().trim());
        } catch (NumberFormatException exception) {
            snoozeMinutes = ActionPreferences.getSnoozeMinutes(this);
        }

        ActionPreferences.setSnoozeMinutes(this, snoozeMinutes);
        ActionPreferences.setRecordSnoozeCount(this, recordSnoozeCountCheckbox.isChecked());
        snoozeMinutesInput.setText(String.valueOf(ActionPreferences.getSnoozeMinutes(this)));
        Toast.makeText(this, "Действия уведомления сохранены", Toast.LENGTH_SHORT).show();
        updateStatus();
    }

    private void setEditMode(String mode) {
        EditPreferences.setEditMode(this, mode);
        Toast.makeText(this, "Режим редактирования сохранен", Toast.LENGTH_SHORT).show();
        rebuild();
    }

    private void setThemeMode(String mode) {
        ThemePreferences.setThemeMode(this, mode);
        Toast.makeText(this, "Тема сохранена", Toast.LENGTH_SHORT).show();
        recreate();
    }

    private String themeButtonText(String mode, String label) {
        return mode.equals(ThemePreferences.getThemeMode(this)) ? label + " выбрана" : label;
    }

    private void rebuild() {
        buildUi();
        updateStatus();
    }

    private void saveFormatSettings() {
        TaskFormatSettings settings = TaskFormatSettings.fromValues(
                dueKeywordInput.getText().toString(),
                repeatKeywordInput.getText().toString(),
                repeatUntilDoneKeywordInput.getText().toString(),
                tagKeywordInput.getText().toString(),
                priorityKeywordInput.getText().toString(),
                groupKeywordInput.getText().toString()
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

    private void saveScanSettings() {
        int maxFiles;
        try {
            maxFiles = Integer.parseInt(maxFilesInput.getText().toString().trim());
        } catch (NumberFormatException exception) {
            maxFiles = NoteScanSettings.DEFAULT_MAX_FILES;
        }

        NoteScanSettings settings = NoteScanSettings.fromValues(
                includePatternsInput.getText().toString(),
                excludePatternsInput.getText().toString(),
                maxFiles
        );
        NoteScanSettings.save(this, settings);
        populateScanInputs(settings);
        Toast.makeText(this, "Поиск задач сохранен", Toast.LENGTH_SHORT).show();
        rescheduleAll();
    }

    private void resetScanSettings() {
        NoteScanSettings.reset(this);
        populateScanInputs(NoteScanSettings.defaults());
        Toast.makeText(this, "Поиск задач сброшен", Toast.LENGTH_SHORT).show();
        rescheduleAll();
    }

    private void populateFormatInputs(TaskFormatSettings settings) {
        dueKeywordInput.setText(settings.getDueKeyword());
        repeatKeywordInput.setText(settings.getRepeatKeyword());
        repeatUntilDoneKeywordInput.setText(settings.getRepeatUntilDoneKeyword());
        tagKeywordInput.setText(settings.getTagKeyword());
        priorityKeywordInput.setText(settings.getPriorityKeyword());
        groupKeywordInput.setText(settings.getGroupKeyword());
    }

    private void populateScanInputs(NoteScanSettings settings) {
        includePatternsInput.setText(settings.getIncludePatternsText());
        excludePatternsInput.setText(settings.getExcludePatternsText());
        maxFilesInput.setText(String.valueOf(settings.getMaxFiles()));
    }

    private void sendTestNotification() {
        boolean sent = TestNotificationSender.send(this);
        Toast.makeText(
                this,
                sent ? "Тестовое уведомление отправлено" : "Нет разрешения на уведомления",
                Toast.LENGTH_SHORT
        ).show();
    }

    private void runDebugAction(DebugActionResult result) {
        Toast.makeText(
                this,
                result.getMessage(),
                result.isSuccess() ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG
        ).show();
        updateStatus();
    }

    private String formatEditMode(String mode) {
        return EditPreferences.MODE_MARKDOWN.equals(mode)
                ? "прямой markdown"
                : "UI с preview markdown";
    }

    private String formatThemeMode(String mode) {
        if (ThemePreferences.MODE_LIGHT.equals(mode)) {
            return "светлая";
        }
        if (ThemePreferences.MODE_DARK.equals(mode)) {
            return "темная";
        }
        return "системная";
    }

    private void updateStatus() {
        String cachedAt = TaskCache.getSavedAt(this);
        String latestError = ErrorLog.latest(this);
        TaskFormatSettings formatSettings = TaskFormatSettings.load(this);
        NoteScanSettings scanSettings = NoteScanSettings.load(this);
        StringBuilder status = new StringBuilder("Источник: " + compactName(NoteStore.sourceLabel(this))
                + "\nИсточников: " + NoteStore.getSavedSourceCount(this)
                + " · фильтр: " + UserPreferences.getTaskFilterLabel(this)
                + "\nТочные напоминания: "
                + (ReminderScheduler.canScheduleExactAlarms(this) ? "да" : "нет")
                + " · запись: " + (NoteStore.canWriteSavedSource(this) ? "да" : "нужно выбрать источник")
                + "\nОтложить: " + ActionPreferences.getSnoozeMinutes(this) + " мин."
                + " · счетчик: " + (ActionPreferences.shouldRecordSnoozeCount(this) ? "вкл." : "выкл.")
                + "\nРедактирование: " + formatEditMode(EditPreferences.getEditMode(this))
                + " · тема: " + formatThemeMode(ThemePreferences.getThemeMode(this))
                + "\nФормат: " + formatSettings.formatForStatus()
                + "\nПоиск: " + scanSettings.formatForStatus()
                + "\nКэш задач: " + TaskCache.getCachedTaskCount(this)
                + (cachedAt == null ? "" : " · " + cachedAt));
        if (latestError != null) {
            status.append("\nПоследняя ошибка записана в лог.");
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
        button.setTextColor(getColor(R.color.secondary_button_text));
        button.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
        button.setBackground(createRoundedBackground(
                getColor(R.color.secondary_button_background),
                getColor(R.color.card_stroke),
                8
        ));
        return button;
    }

    private GradientDrawable createRoundedBackground(int color, int strokeColor, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        if (strokeColor != 0) {
            drawable.setStroke(dp(1), strokeColor);
        }
        return drawable;
    }

    private String compactName(String rawName) {
        if (rawName == null || rawName.trim().isEmpty()) {
            return "не выбран";
        }
        String value = rawName.trim();
        int queryIndex = value.indexOf('?');
        if (queryIndex >= 0) {
            value = value.substring(0, queryIndex);
        }
        int encodedSlash = Math.max(value.lastIndexOf("%2F"), value.lastIndexOf("%2f"));
        if (encodedSlash >= 0 && encodedSlash + 3 < value.length()) {
            value = value.substring(encodedSlash + 3);
        }
        int slash = Math.max(value.lastIndexOf('/'), value.lastIndexOf('\\'));
        if (slash >= 0 && slash + 1 < value.length()) {
            value = value.substring(slash + 1);
        }
        int colon = value.lastIndexOf(':');
        if (colon >= 0 && colon + 1 < value.length()) {
            value = value.substring(colon + 1);
        }
        return value.isEmpty() ? rawName : value;
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
