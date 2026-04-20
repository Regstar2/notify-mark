package com.regstar.obsidiannotification;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public final class TaskEditActivity extends Activity {
    public static final String EXTRA_TASK_KEY = "task_key";

    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DUE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private String taskKey;
    private String editMode;
    private ObsidianTask task;
    private NoteStore.TaskDocumentMatch taskMatch;
    private NoteStore.NoteDocument defaultDocument;
    private String loadError;

    private TextView statusText;
    private TextView previewText;
    private EditText markdownInput;
    private EditText titleInput;
    private EditText dateInput;
    private EditText timeInput;
    private EditText repeatInput;
    private EditText priorityInput;
    private EditText tagsInput;
    private CheckBox checkboxTaskInput;
    private CheckBox repeatUntilDoneInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemePreferences.apply(this);
        super.onCreate(savedInstanceState);

        taskKey = getIntent().getStringExtra(EXTRA_TASK_KEY);
        editMode = EditPreferences.getEditMode(this);
        loadTaskContext();
        buildUi();
        updatePreview();
    }

    private void loadTaskContext() {
        try {
            if (taskKey != null && !taskKey.trim().isEmpty()) {
                taskMatch = NoteStore.findTaskDocument(this, taskKey);
                if (taskMatch == null) {
                    loadError = "Задача не найдена. Возможно, заметка уже синхронизировалась.";
                    return;
                }
                task = taskMatch.getTask();
            } else {
                defaultDocument = NoteStore.findDefaultWriteDocument(this);
            }
        } catch (IOException | RuntimeException exception) {
            loadError = exception.getMessage();
            ErrorLog.record(this, "Не удалось открыть экран редактирования", exception);
        }
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
        title.setText(task == null ? "Новое уведомление" : "Редактирование уведомления");
        title.setTextSize(24);
        title.setTextColor(getColor(R.color.text_primary));
        root.addView(title, fullWidthWithBottomMargin());

        TextView source = new TextView(this);
        source.setText("Источник: " + sourceLabel());
        source.setTextSize(14);
        source.setTextColor(getColor(R.color.text_secondary));
        source.setPadding(0, 0, 0, dp(10));
        root.addView(source, fullWidth());

        addModeSwitch(root);

        if (EditPreferences.MODE_MARKDOWN.equals(editMode)) {
            addMarkdownEditor(root);
        } else {
            addUiEditor(root);
        }

        previewText = new TextView(this);
        previewText.setTextSize(14);
        previewText.setTextColor(getColor(R.color.text_primary));
        previewText.setTypeface(android.graphics.Typeface.MONOSPACE);
        previewText.setPadding(0, dp(10), 0, dp(10));
        root.addView(previewText, fullWidth());

        statusText = new TextView(this);
        statusText.setTextSize(14);
        statusText.setTextColor(getColor(R.color.text_secondary));
        statusText.setPadding(0, 0, 0, dp(10));
        root.addView(statusText, fullWidth());

        Button checkButton = createButton("Проверить");
        checkButton.setOnClickListener(view -> validateCandidate(currentMarkdownLine(), true));
        root.addView(checkButton, fullWidthWithBottomMargin());

        Button saveButton = createButton("Сохранить");
        saveButton.setOnClickListener(view -> saveTask());
        root.addView(saveButton, fullWidthWithBottomMargin());

        if (task != null) {
            Button deleteButton = createButton("Удалить уведомление");
            deleteButton.setOnClickListener(view -> confirmDelete());
            root.addView(deleteButton, fullWidthWithBottomMargin());
        }

        Button cancelButton = createButton("Отмена");
        cancelButton.setOnClickListener(view -> finish());
        root.addView(cancelButton, fullWidthWithBottomMargin());

        setContentView(scrollView);
    }

    private void addModeSwitch(LinearLayout root) {
        TextView modeTitle = new TextView(this);
        modeTitle.setText("Режим редактирования");
        modeTitle.setTextSize(16);
        modeTitle.setTextColor(getColor(R.color.text_primary));
        root.addView(modeTitle, fullWidth());

        LinearLayout modeRow = new LinearLayout(this);
        modeRow.setOrientation(LinearLayout.HORIZONTAL);

        Button uiButton = createButton(EditPreferences.MODE_UI.equals(editMode)
                ? "UI-режим выбран"
                : "UI-режим");
        uiButton.setOnClickListener(view -> switchMode(EditPreferences.MODE_UI));
        modeRow.addView(uiButton, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        ));

        Button markdownButton = createButton(EditPreferences.MODE_MARKDOWN.equals(editMode)
                ? "Markdown выбран"
                : "Markdown");
        markdownButton.setOnClickListener(view -> switchMode(EditPreferences.MODE_MARKDOWN));
        LinearLayout.LayoutParams markdownParams = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        );
        markdownParams.setMargins(dp(10), 0, 0, 0);
        modeRow.addView(markdownButton, markdownParams);
        root.addView(modeRow, fullWidthWithBottomMargin());
    }

    private void addMarkdownEditor(LinearLayout root) {
        addLabel(root, "Markdown-строка");
        markdownInput = createInput(defaultMarkdownLine());
        markdownInput.setSingleLine(false);
        markdownInput.setMinLines(3);
        markdownInput.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        markdownInput.addTextChangedListener(previewWatcher());
        root.addView(markdownInput, fullWidthWithBottomMargin());
    }

    private void addUiEditor(LinearLayout root) {
        checkboxTaskInput = new CheckBox(this);
        checkboxTaskInput.setText("Записать как checkbox-задачу");
        checkboxTaskInput.setTextColor(getColor(R.color.text_secondary));
        checkboxTaskInput.setChecked(task == null || isCheckboxTask(task.getRawLine()));
        checkboxTaskInput.setOnCheckedChangeListener((button, checked) -> updatePreview());
        root.addView(checkboxTaskInput, fullWidthWithBottomMargin());

        addLabel(root, "Текст");
        titleInput = createInput(task == null ? "Новое уведомление" : task.getTitle());
        titleInput.addTextChangedListener(previewWatcher());
        root.addView(titleInput, fullWidthWithBottomMargin());

        LocalDateTime due = task == null || task.getReminderAt() == null
                ? LocalDateTime.now().plusMinutes(10)
                : task.getReminderAt();

        addLabel(root, "Дата (yyyy-MM-dd)");
        dateInput = createInput(DATE.format(due.toLocalDate()));
        dateInput.addTextChangedListener(previewWatcher());
        root.addView(dateInput, fullWidthWithBottomMargin());

        addLabel(root, "Время (HH:mm)");
        timeInput = createInput(TIME.format(due.toLocalTime()));
        timeInput.addTextChangedListener(previewWatcher());
        root.addView(timeInput, fullWidthWithBottomMargin());

        addLabel(root, "Повтор, например 15m, 2h, 1d");
        repeatInput = createInput(durationToToken(task == null ? null : task.getRepeatInterval()));
        repeatInput.addTextChangedListener(previewWatcher());
        root.addView(repeatInput, fullWidthWithBottomMargin());

        repeatUntilDoneInput = new CheckBox(this);
        repeatUntilDoneInput.setText("Повторять, пока задача не будет выполнена");
        repeatUntilDoneInput.setTextColor(getColor(R.color.text_secondary));
        repeatUntilDoneInput.setChecked(task != null && task.getRepeatMode() == RepeatMode.UNTIL_DONE);
        repeatUntilDoneInput.setOnCheckedChangeListener((button, checked) -> updatePreview());
        root.addView(repeatUntilDoneInput, fullWidthWithBottomMargin());

        addLabel(root, "Приоритет: low, medium, high, urgent");
        priorityInput = createInput(priorityToToken(task == null ? TaskPriority.NONE : task.getPriority()));
        priorityInput.addTextChangedListener(previewWatcher());
        root.addView(priorityInput, fullWidthWithBottomMargin());

        addLabel(root, "Теги через пробел");
        tagsInput = createInput(tagsToText(task == null ? java.util.Collections.emptyList() : task.getTags()));
        tagsInput.addTextChangedListener(previewWatcher());
        root.addView(tagsInput, fullWidthWithBottomMargin());
    }

    private void switchMode(String mode) {
        EditPreferences.setEditMode(this, mode);
        recreate();
    }

    private void saveTask() {
        String candidate = currentMarkdownLine();
        if (!validateCandidate(candidate, false)) {
            return;
        }

        TaskEditResult result = task == null
                ? NoteStore.appendTaskLine(this, candidate)
                : NoteStore.replaceTaskLine(this, taskKey, candidate);
        handleWriteResult(result);
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle("Удалить уведомление?")
                .setMessage("Строка будет удалена из markdown-файла.")
                .setPositiveButton("Удалить", (dialog, which) ->
                        handleWriteResult(NoteStore.deleteTaskLine(this, taskKey)))
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void handleWriteResult(TaskEditResult result) {
        if (result.isUpdated()) {
            NoteChangeMonitor.syncNow(this, true);
            Toast.makeText(this, result.getMessage(), Toast.LENGTH_SHORT).show();
            setResult(RESULT_OK);
            finish();
            return;
        }

        String message = result.getMessage() == null ? "Не удалось записать файл" : result.getMessage();
        statusText.setText(message);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private boolean validateCandidate(String candidate, boolean showSuccess) {
        if (loadError != null && task == null && defaultDocument == null) {
            statusText.setText("Источник недоступен: " + loadError);
            return false;
        }
        if (candidate == null || candidate.trim().isEmpty()) {
            statusText.setText("Markdown-строка пустая.");
            return false;
        }
        if (candidate.contains("\n") || candidate.contains("\r")) {
            statusText.setText("Сейчас редактируется одна markdown-строка. Переносы строк уберите.");
            return false;
        }

        TaskParseResult result = TaskParser.parseDocument(
                candidate + "\n",
                LocalDate.now(),
                "preview.md",
                TaskFormatSettings.load(this)
        );
        if (!result.getErrors().isEmpty()) {
            statusText.setText(formatErrors(result.getErrors()));
            return false;
        }
        if (result.getTasks().isEmpty()) {
            statusText.setText("Строка не распознана как уведомление. Добавьте @due(...).");
            return false;
        }
        if (result.getTasks().get(0).getReminderAt() == null) {
            statusText.setText("Для уведомления нужно указать дату или время через @due(...).");
            return false;
        }

        if (showSuccess) {
            statusText.setText("Формат корректный.");
        }
        return true;
    }

    private String currentMarkdownLine() {
        if (EditPreferences.MODE_MARKDOWN.equals(editMode)) {
            return markdownInput == null ? "" : markdownInput.getText().toString().trim();
        }

        String title = titleInput == null ? "" : titleInput.getText().toString().trim();
        if (title.isEmpty()) {
            title = "Новое уведомление";
        }

        StringBuilder builder = new StringBuilder();
        if (checkboxTaskInput == null || checkboxTaskInput.isChecked()) {
            boolean completed = task != null && task.isCompleted();
            builder.append(completed ? "- [x] " : "- [ ] ");
        }
        builder.append(title);

        String dueValue = dueValue();
        if (!dueValue.isEmpty()) {
            builder.append(" @due(").append(dueValue).append(")");
        }

        String repeat = valueOf(repeatInput);
        if (!repeat.isEmpty()) {
            builder.append(repeatUntilDoneInput != null && repeatUntilDoneInput.isChecked()
                    ? " @repeatUntilDone("
                    : " @repeat(");
            builder.append(repeat).append(")");
        }

        String priority = valueOf(priorityInput);
        if (!priority.isEmpty()) {
            builder.append(" @priority(").append(priority).append(")");
        }

        String tags = valueOf(tagsInput);
        if (!tags.isEmpty()) {
            builder.append(" @tag(").append(tags).append(")");
        }

        return builder.toString().trim();
    }

    private String dueValue() {
        String date = valueOf(dateInput);
        String time = valueOf(timeInput);
        if (!date.isEmpty() && !time.isEmpty()) {
            return date + " " + time;
        }
        if (!date.isEmpty()) {
            return date;
        }
        return time;
    }

    private String defaultMarkdownLine() {
        if (task != null) {
            return task.getRawLine();
        }
        LocalDateTime due = LocalDateTime.now().plusMinutes(10);
        return "- [ ] Новое уведомление @due(" + DUE.format(due) + ")";
    }

    private void updatePreview() {
        if (previewText != null) {
            previewText.setText("Preview:\n" + currentMarkdownLine());
        }
        if (statusText != null && loadError != null) {
            statusText.setText("Источник недоступен: " + loadError);
        }
    }

    private TextWatcher previewWatcher() {
        return new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
                updatePreview();
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        };
    }

    private String sourceLabel() {
        if (taskMatch != null) {
            return taskMatch.getDisplayName() + ", строка " + task.getLineNumber();
        }
        if (defaultDocument != null) {
            return defaultDocument.getDisplayName();
        }
        return loadError == null ? NoteStore.sourceLabel(this) : loadError;
    }

    private String formatErrors(List<TaskParseError> errors) {
        StringBuilder builder = new StringBuilder("Ошибка формата:");
        int limit = Math.min(3, errors.size());
        for (int i = 0; i < limit; i++) {
            builder.append("\n").append(errors.get(i).format());
        }
        return builder.toString();
    }

    private boolean isCheckboxTask(String rawLine) {
        return rawLine != null && rawLine.trim().matches("^[-*+]\\s+\\[[ xX]\\].*");
    }

    private String durationToToken(Duration duration) {
        if (duration == null) {
            return "";
        }
        long minutes = duration.toMinutes();
        if (minutes % (24 * 60) == 0) {
            return (minutes / (24 * 60)) + "d";
        }
        if (minutes % 60 == 0) {
            return (minutes / 60) + "h";
        }
        return minutes + "m";
    }

    private String priorityToToken(TaskPriority priority) {
        return priority == null || priority == TaskPriority.NONE
                ? ""
                : priority.name().toLowerCase(Locale.ROOT);
    }

    private String tagsToText(List<String> tags) {
        StringBuilder builder = new StringBuilder();
        for (String tag : tags) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(tag);
        }
        return builder.toString();
    }

    private String valueOf(EditText input) {
        return input == null ? "" : input.getText().toString().trim();
    }

    private void addLabel(LinearLayout root, String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(14);
        label.setTextColor(getColor(R.color.text_secondary));
        root.addView(label, fullWidth());
    }

    private EditText createInput(String value) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(value == null ? "" : value);
        input.setSelectAllOnFocus(false);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        return input;
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
