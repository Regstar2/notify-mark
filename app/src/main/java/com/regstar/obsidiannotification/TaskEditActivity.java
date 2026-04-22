package com.regstar.obsidiannotification;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
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
    public static final String EXTRA_DUE_DATE = "due_date";

    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private String taskKey;
    private LocalDate prefilledDate;
    private ObsidianTask task;
    private NoteStore.TaskDocumentMatch taskMatch;
    private NoteStore.NoteDocument defaultDocument;
    private String loadError;

    private TextView statusText;
    private TextView previewText;
    private EditText titleInput;
    private EditText dateInput;
    private EditText timeInput;
    private EditText repeatInput;
    private EditText priorityInput;
    private EditText tagsInput;
    private EditText groupInput;
    private CheckBox checkboxTaskInput;
    private CheckBox repeatUntilDoneInput;
    private Button saveBottomButton;
    private ImageButton saveTopButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemePreferences.apply(this);
        super.onCreate(savedInstanceState);

        taskKey = getIntent().getStringExtra(EXTRA_TASK_KEY);
        prefilledDate = parsePrefilledDate(getIntent().getStringExtra(EXTRA_DUE_DATE));
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

    private LocalDate parsePrefilledDate(String rawDate) {
        if (rawDate == null || rawDate.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(rawDate.trim(), DATE);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private LocalDateTime defaultDueDateTime() {
        LocalDate date = prefilledDate == null ? LocalDate.now() : prefilledDate;
        return LocalDateTime.of(date, LocalDateTime.now().plusMinutes(10).toLocalTime());
    }

    private void buildUi() {
        ScrollView scrollView = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(14), dp(16), dp(20));
        root.setBackgroundColor(getColor(R.color.background));
        scrollView.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        root.addView(createTopBar(), fullWidthWithBottomMargin());
        root.addView(createSourceCard(), fullWidthWithBottomMargin());

        addUiEditor(root);

        root.addView(createPreviewCard(), fullWidthWithBottomMargin());

        statusText = new TextView(this);
        statusText.setTextSize(14);
        statusText.setTextColor(getColor(R.color.text_secondary));
        statusText.setPadding(0, 0, 0, dp(10));
        root.addView(statusText, fullWidth());

        Button checkButton = createSecondaryButton("Проверить");
        checkButton.setOnClickListener(view -> validateCandidate(currentMarkdownLine(), true));
        root.addView(checkButton, fullWidthWithBottomMargin());

        LinearLayout bottomActions = new LinearLayout(this);
        bottomActions.setOrientation(LinearLayout.HORIZONTAL);
        Button cancelButton = createSecondaryButton("Отмена");
        cancelButton.setOnClickListener(view -> finish());
        bottomActions.addView(cancelButton, new LinearLayout.LayoutParams(
                0,
                dp(48),
                1
        ));

        saveBottomButton = createPrimaryButton("Сохранить");
        saveBottomButton.setOnClickListener(view -> saveTask());
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(
                0,
                dp(48),
                1
        );
        saveParams.setMargins(dp(10), 0, 0, 0);
        bottomActions.addView(saveBottomButton, saveParams);
        root.addView(bottomActions, fullWidthWithBottomMargin());

        if (task != null) {
            Button deleteButton = createSecondaryButton("Удалить уведомление");
            deleteButton.setOnClickListener(view -> confirmDelete());
            root.addView(deleteButton, fullWidthWithBottomMargin());
        }

        setContentView(scrollView);
    }

    private LinearLayout createTopBar() {
        LinearLayout appBar = new LinearLayout(this);
        appBar.setOrientation(LinearLayout.HORIZONTAL);
        appBar.setGravity(android.view.Gravity.CENTER_VERTICAL);

        ImageButton back = createIconButton(R.drawable.ic_arrow_back, "Назад");
        back.setOnClickListener(view -> finish());
        appBar.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));

        TextView title = createText(task == null ? "Новое уведомление" : "Редактирование", 22, R.color.text_primary, true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        );
        titleParams.setMargins(dp(8), 0, dp(8), 0);
        appBar.addView(title, titleParams);

        saveTopButton = createIconButton(R.drawable.ic_check, "Сохранить");
        saveTopButton.setOnClickListener(view -> saveTask());
        appBar.addView(saveTopButton, new LinearLayout.LayoutParams(dp(44), dp(44)));
        return appBar;
    }

    private LinearLayout createSourceCard() {
        LinearLayout card = createCardContainer();
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_file);
        icon.setColorFilter(getColor(R.color.text_secondary));
        row.addView(icon, new LinearLayout.LayoutParams(dp(26), dp(26)));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(createText(compactName(sourceLabel()), 15, R.color.text_primary, true), fullWidth());
        texts.addView(createText(task == null ? "Файл для записи" : "Исходная строка в заметке", 13, R.color.text_secondary, false), fullWidth());
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        );
        textParams.setMargins(dp(12), 0, 0, 0);
        row.addView(texts, textParams);
        card.addView(row, fullWidth());
        return card;
    }

    private LinearLayout createPreviewCard() {
        LinearLayout card = createCardContainer();
        card.addView(createText("Preview markdown", 14, R.color.text_secondary, false), fullWidth());
        previewText = createText("", 14, R.color.text_primary, false);
        previewText.setTypeface(Typeface.MONOSPACE);
        previewText.setPadding(0, dp(8), 0, 0);
        card.addView(previewText, fullWidth());
        return card;
    }

    private void addUiEditor(LinearLayout root) {
        root.addView(createSectionTitle("Основное"), fullWidth());

        addLabel(root, "Текст");
        titleInput = createInput(task == null ? "Новое уведомление" : task.getTitle());
        titleInput.addTextChangedListener(previewWatcher());
        root.addView(titleInput, fullWidthWithBottomMargin());

        LocalDateTime due = task == null || task.getReminderAt() == null
                ? defaultDueDateTime()
                : task.getReminderAt();

        addLabel(root, "Дата (yyyy-MM-dd)");
        dateInput = createInput(DATE.format(due.toLocalDate()));
        dateInput.setOnClickListener(view -> showDatePicker());
        dateInput.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus) {
                showDatePicker();
            }
        });
        dateInput.addTextChangedListener(previewWatcher());
        root.addView(dateInput, fullWidthWithBottomMargin());

        addLabel(root, "Время (HH:mm)");
        timeInput = createInput(TIME.format(due.toLocalTime()));
        timeInput.setOnClickListener(view -> showTimePicker());
        timeInput.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus) {
                showTimePicker();
            }
        });
        timeInput.addTextChangedListener(previewWatcher());
        root.addView(timeInput, fullWidthWithBottomMargin());

        root.addView(createSectionTitle("Повтор"), fullWidth());
        addLabel(root, "Интервал: 15m, 2h, 1d");
        repeatInput = createInput(durationToToken(task == null ? null : task.getRepeatInterval()));
        repeatInput.addTextChangedListener(previewWatcher());
        root.addView(repeatInput, fullWidthWithBottomMargin());
        addQuickRepeatRow(root);

        repeatUntilDoneInput = new CheckBox(this);
        repeatUntilDoneInput.setText("Повторять до выполнения");
        repeatUntilDoneInput.setTextColor(getColor(R.color.text_secondary));
        repeatUntilDoneInput.setChecked(task != null && task.getRepeatMode() == RepeatMode.UNTIL_DONE);
        repeatUntilDoneInput.setOnCheckedChangeListener((button, checked) -> updatePreview());
        root.addView(repeatUntilDoneInput, fullWidthWithBottomMargin());

        root.addView(createSectionTitle("Дополнительно"), fullWidth());
        addLabel(root, "Группа");
        groupInput = createInput(task == null ? "" : task.getGroup());
        groupInput.addTextChangedListener(previewWatcher());
        root.addView(groupInput, fullWidthWithBottomMargin());

        addLabel(root, "Приоритет");
        priorityInput = createInput(priorityToToken(task == null ? TaskPriority.NONE : task.getPriority()));
        priorityInput.addTextChangedListener(previewWatcher());
        root.addView(priorityInput, fullWidthWithBottomMargin());
        addPriorityRow(root);

        addLabel(root, "Теги через пробел, например #work #health");
        tagsInput = createInput(tagsToText(task == null ? java.util.Collections.emptyList() : task.getTags()));
        tagsInput.addTextChangedListener(previewWatcher());
        root.addView(tagsInput, fullWidthWithBottomMargin());

        checkboxTaskInput = new CheckBox(this);
        checkboxTaskInput.setText("Сохранить как задачу с чекбоксом");
        checkboxTaskInput.setTextColor(getColor(R.color.text_secondary));
        checkboxTaskInput.setChecked(task == null || isCheckboxTask(task.getRawLine()));
        checkboxTaskInput.setOnCheckedChangeListener((button, checked) -> updatePreview());
        root.addView(checkboxTaskInput, fullWidthWithBottomMargin());
    }

    private void addQuickRepeatRow(LinearLayout root) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        addSmallValueButton(row, "5m", "5m", 0);
        addSmallValueButton(row, "10m", "10m", dp(6));
        addSmallValueButton(row, "15m", "15m", dp(6));
        addSmallValueButton(row, "1h", "1h", dp(6));
        addSmallValueButton(row, "1d", "1d", dp(6));
        root.addView(row, fullWidthWithBottomMargin());
    }

    private void addPriorityRow(LinearLayout root) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        addPriorityButton(row, "Нет", "", 0);
        addPriorityButton(row, "Низкий", "low", dp(6));
        addPriorityButton(row, "Средний", "medium", dp(6));
        addPriorityButton(row, "Высокий", "high", dp(6));
        root.addView(row, fullWidthWithBottomMargin());
    }

    private void addSmallValueButton(LinearLayout row, String label, String value, int leftMargin) {
        Button button = createSegmentButton(label, value.equals(valueOf(repeatInput)));
        button.setOnClickListener(view -> {
            repeatInput.setText(value);
            updatePreview();
        });
        addCompactButton(row, button, leftMargin);
    }

    private void addPriorityButton(LinearLayout row, String label, String value, int leftMargin) {
        Button button = createSegmentButton(label, value.equalsIgnoreCase(valueOf(priorityInput)));
        button.setOnClickListener(view -> {
            priorityInput.setText(value);
            updatePreview();
        });
        addCompactButton(row, button, leftMargin);
    }

    private void addCompactButton(LinearLayout row, Button button, int leftMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0,
                dp(36),
                1
        );
        params.setMargins(leftMargin, 0, 0, 0);
        row.addView(button, params);
    }

    private void showDatePicker() {
        LocalDate initial;
        try {
            initial = LocalDate.parse(valueOf(dateInput), DATE);
        } catch (RuntimeException exception) {
            initial = LocalDate.now();
        }
        new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    dateInput.setText(DATE.format(LocalDate.of(year, month + 1, dayOfMonth)));
                    updatePreview();
                },
                initial.getYear(),
                initial.getMonthValue() - 1,
                initial.getDayOfMonth()
        ).show();
    }

    private void showTimePicker() {
        java.time.LocalTime initial;
        try {
            initial = java.time.LocalTime.parse(valueOf(timeInput), TIME);
        } catch (RuntimeException exception) {
            initial = java.time.LocalTime.now();
        }
        new TimePickerDialog(
                this,
                (view, hourOfDay, minute) -> {
                    timeInput.setText(TIME.format(java.time.LocalTime.of(hourOfDay, minute)));
                    updatePreview();
                },
                initial.getHour(),
                initial.getMinute(),
                true
        ).show();
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

        String group = valueOf(groupInput);
        if (!group.isEmpty() && !ObsidianTask.DEFAULT_GROUP.equals(group)) {
            builder.append(" @group(").append(group).append(")");
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

    private void updatePreview() {
        if (previewText != null) {
            previewText.setText(currentMarkdownLine());
        }
        boolean validEnough = isCandidateReady(currentMarkdownLine());
        if (saveBottomButton != null) {
            saveBottomButton.setEnabled(validEnough);
        }
        if (saveTopButton != null) {
            saveTopButton.setEnabled(validEnough);
        }
        if (statusText != null && loadError != null) {
            statusText.setText("Источник недоступен: " + loadError);
        }
    }

    private boolean isCandidateReady(String candidate) {
        if (loadError != null && task == null && defaultDocument == null) {
            return false;
        }
        if (candidate == null || candidate.trim().isEmpty()) {
            return false;
        }
        if (candidate.contains("\n") || candidate.contains("\r")) {
            return false;
        }
        TaskParseResult result = TaskParser.parseDocument(
                candidate + "\n",
                LocalDate.now(),
                "preview.md",
                TaskFormatSettings.load(this)
        );
        return result.getErrors().isEmpty()
                && !result.getTasks().isEmpty()
                && result.getTasks().get(0).getReminderAt() != null;
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

    private TextView createText(String text, int sizeSp, int colorRes, boolean bold) {
        TextView textView = new TextView(this);
        textView.setText(text);
        textView.setTextSize(sizeSp);
        textView.setTextColor(getColor(colorRes));
        textView.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        return textView;
    }

    private TextView createSectionTitle(String text) {
        TextView title = createText(text, 17, R.color.text_primary, true);
        title.setPadding(0, dp(12), 0, dp(6));
        return title;
    }

    private LinearLayout createCardContainer() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(createRoundedBackground(
                getColor(R.color.card_background),
                getColor(R.color.card_stroke),
                8
        ));
        return card;
    }

    private ImageButton createIconButton(int iconRes, String description) {
        ImageButton button = new ImageButton(this);
        button.setImageResource(iconRes);
        button.setContentDescription(description);
        button.setColorFilter(getColor(R.color.text_primary));
        button.setPadding(dp(10), dp(10), dp(10), dp(10));
        button.setBackground(createRoundedBackground(
                getColor(R.color.icon_button_background),
                0,
                18
        ));
        return button;
    }

    private Button createPrimaryButton(String text) {
        Button button = createButton(text);
        button.setTextColor(getColor(R.color.primary_button_text));
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setBackground(createRoundedBackground(
                getColor(R.color.primary_button_background),
                0,
                8
        ));
        return button;
    }

    private Button createSecondaryButton(String text) {
        Button button = createButton(text);
        button.setTextColor(getColor(R.color.secondary_button_text));
        button.setBackground(createRoundedBackground(
                getColor(R.color.secondary_button_background),
                getColor(R.color.card_stroke),
                8
        ));
        return button;
    }

    private Button createSegmentButton(String text, boolean selected) {
        Button button = createButton(text);
        button.setTextSize(12);
        button.setTextColor(getColor(selected ? R.color.chip_selected_text : R.color.chip_text));
        button.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
        button.setBackground(createRoundedBackground(
                getColor(selected ? R.color.chip_selected_background : R.color.chip_background),
                getColor(selected ? R.color.chip_selected_stroke : R.color.chip_stroke),
                8
        ));
        return button;
    }

    private Button createButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setMinHeight(0);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
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
            return "Источник не выбран";
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
