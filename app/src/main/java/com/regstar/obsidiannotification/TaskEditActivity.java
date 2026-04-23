package com.regstar.obsidiannotification;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class TaskEditActivity extends Activity {
    public static final String EXTRA_TASK_KEY = "task_key";
    public static final String EXTRA_DUE_DATE = "due_date";

    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final List<SubtaskDraft> subtaskDrafts = new ArrayList<>();

    private String taskKey;
    private LocalDate prefilledDate;
    private ObsidianTask task;
    private NoteStore.TaskDocumentMatch taskMatch;
    private NoteStore.NoteDocument defaultDocument;
    private String loadError;
    private String initialMarkdownBlock = "";
    private boolean previewExpanded;
    private TaskPriority selectedPriority = TaskPriority.NONE;

    private TextView statusText;
    private TextView previewText;
    private TextView previewTitle;
    private TextView previewChevron;
    private LinearLayout subtaskList;
    private TextView subtaskSummaryText;
    private EditText titleInput;
    private EditText dateInput;
    private EditText timeInput;
    private EditText repeatInput;
    private EditText tagsInput;
    private EditText groupInput;
    private CheckBox checkboxTaskInput;
    private CheckBox repeatUntilDoneInput;
    private Button saveButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemePreferences.applySheet(this);
        super.onCreate(savedInstanceState);
        configureSheetWindow();

        taskKey = getIntent().getStringExtra(EXTRA_TASK_KEY);
        prefilledDate = parsePrefilledDate(getIntent().getStringExtra(EXTRA_DUE_DATE));
        loadTaskContext();
        selectedPriority = task == null ? TaskPriority.NONE : task.getPriority();
        buildUi();
        initialMarkdownBlock = currentMarkdownBlock();
        updatePreview();
    }

    @Override
    public void onBackPressed() {
        requestClose();
    }

    private void configureSheetWindow() {
        Window window = getWindow();
        if (window == null) {
            return;
        }
        window.setBackgroundDrawableResource(android.R.color.transparent);
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        WindowManager.LayoutParams params = window.getAttributes();
        params.dimAmount = 0.48f;
        window.setAttributes(params);
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
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
                for (ObsidianTask subtask : task.getSubtasks()) {
                    subtaskDrafts.add(SubtaskDraft.fromTask(subtask));
                }
            } else {
                defaultDocument = NoteStore.findDefaultWriteDocument(this);
            }
        } catch (IOException | RuntimeException exception) {
            loadError = exception.getMessage();
            ErrorLog.record(this, "Не удалось открыть редактор задачи", exception);
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
        LocalTime time = LocalDateTime.now().plusMinutes(10).toLocalTime().withSecond(0).withNano(0);
        return LocalDateTime.of(date, time);
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.TRANSPARENT);
        root.setOnClickListener(view -> requestClose());

        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setClickable(true);
        sheet.setBackground(createSheetBackground());
        sheet.setPadding(dp(16), dp(10), dp(16), dp(14));

        View handle = new View(this);
        handle.setBackground(createRoundedBackground(getColor(R.color.card_stroke), 0, 99));
        LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(dp(44), dp(4));
        handleParams.gravity = Gravity.CENTER_HORIZONTAL;
        handleParams.setMargins(0, 0, 0, dp(8));
        sheet.addView(handle, handleParams);
        sheet.addView(createHeader(), fullWidthWithBottomMargin(dp(8)));

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, 0, 0, dp(18));

        statusText = createText("", 13, R.color.text_secondary, false);
        statusText.setPadding(dp(2), 0, dp(2), dp(8));
        content.addView(statusText, fullWidth());
        content.addView(createBasicSection(), fullWidthWithBottomMargin(dp(10)));
        content.addView(createRepeatSection(), fullWidthWithBottomMargin(dp(10)));
        content.addView(createExtraSection(), fullWidthWithBottomMargin(dp(10)));
        content.addView(createSubtasksSection(), fullWidthWithBottomMargin(dp(10)));
        content.addView(createPreviewSection(), fullWidthWithBottomMargin(dp(10)));
        if (task != null) {
            content.addView(createDangerSection(), fullWidthWithBottomMargin(dp(10)));
        }

        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        sheet.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        FrameLayout.LayoutParams sheetParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.BOTTOM
        );
        sheetParams.setMargins(0, dp(68), 0, 0);
        root.addView(sheet, sheetParams);
        setContentView(root);
    }

    private LinearLayout createHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        ImageButton close = createIconButton(R.drawable.ic_close, "Закрыть");
        close.setOnClickListener(view -> requestClose());
        header.addView(close, new LinearLayout.LayoutParams(dp(42), dp(42)));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView title = createText(task == null ? "Новое уведомление" : "Редактирование", 20, R.color.text_primary, true);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(title, fullWidth());
        TextView subtitle = createText(sourceLabel(), 12, R.color.text_secondary, false);
        subtitle.setSingleLine(true);
        subtitle.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(subtitle, fullWidthWithTopMargin(dp(1)));

        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        );
        textParams.setMargins(dp(10), 0, dp(8), 0);
        header.addView(texts, textParams);

        saveButton = createPrimaryButton("Сохранить");
        saveButton.setOnClickListener(view -> saveTask());
        header.addView(saveButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(42)
        ));
        return header;
    }

    private LinearLayout createBasicSection() {
        LinearLayout card = createSectionCard("Основное", "Текст, дата и время напоминания.");
        titleInput = createInput(task == null ? "Новое уведомление" : task.getTitle());
        titleInput.setHint("Текст задачи");
        titleInput.addTextChangedListener(previewWatcher());
        card.addView(createInputBlock("Текст", titleInput), fullWidthWithBottomMargin(dp(8)));

        LocalDateTime due = task == null || task.getReminderAt() == null
                ? defaultDueDateTime()
                : task.getReminderAt();
        dateInput = createInput(DATE.format(due.toLocalDate()));
        dateInput.setOnClickListener(view -> showDatePicker());
        dateInput.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus) {
                showDatePicker();
            }
        });
        dateInput.addTextChangedListener(previewWatcher());

        timeInput = createInput(TIME.format(due.toLocalTime()));
        timeInput.setOnClickListener(view -> showTimePicker());
        timeInput.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus) {
                showTimePicker();
            }
        });
        timeInput.addTextChangedListener(previewWatcher());

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(createInputBlock("Дата", dateInput), new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        ));
        LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        );
        timeParams.setMargins(dp(8), 0, 0, 0);
        row.addView(createInputBlock("Время", timeInput), timeParams);
        card.addView(row, fullWidth());
        return card;
    }

    private LinearLayout createRepeatSection() {
        LinearLayout card = createSectionCard("Повтор", "Интервал можно выбрать быстро или ввести вручную.");
        repeatInput = createInput(durationToToken(task == null ? null : task.getRepeatInterval()));
        repeatInput.setHint("15m, 2h, 1d");
        repeatInput.addTextChangedListener(previewWatcher());
        card.addView(createInputBlock("Интервал", repeatInput), fullWidthWithBottomMargin(dp(8)));

        LinearLayout quickRow = new LinearLayout(this);
        quickRow.setOrientation(LinearLayout.HORIZONTAL);
        addSmallValueButton(quickRow, "5m", "5m", 0);
        addSmallValueButton(quickRow, "10m", "10m", dp(6));
        addSmallValueButton(quickRow, "15m", "15m", dp(6));
        addSmallValueButton(quickRow, "1h", "1h", dp(6));
        addSmallValueButton(quickRow, "1d", "1d", dp(6));
        card.addView(quickRow, fullWidthWithBottomMargin(dp(8)));

        repeatUntilDoneInput = new CheckBox(this);
        repeatUntilDoneInput.setText("Повторять до выполнения");
        repeatUntilDoneInput.setTextColor(getColor(R.color.text_secondary));
        repeatUntilDoneInput.setChecked(task != null && task.getRepeatMode() == RepeatMode.UNTIL_DONE);
        repeatUntilDoneInput.setOnCheckedChangeListener((button, checked) -> updatePreview());
        card.addView(repeatUntilDoneInput, fullWidth());
        return card;
    }

    private LinearLayout createExtraSection() {
        LinearLayout card = createSectionCard("Дополнительно", "Группа, приоритет, теги и тип markdown-записи.");
        groupInput = createInput(task == null ? "" : task.getGroup());
        groupInput.setHint(ObsidianTask.DEFAULT_GROUP);
        groupInput.addTextChangedListener(previewWatcher());
        card.addView(createInputBlock("Группа", groupInput), fullWidthWithBottomMargin(dp(8)));

        card.addView(createLabel("Приоритет"), fullWidthWithBottomMargin(dp(4)));
        LinearLayout priorityRow = new LinearLayout(this);
        priorityRow.setOrientation(LinearLayout.HORIZONTAL);
        rebuildPriorityRow(priorityRow);
        card.addView(priorityRow, fullWidthWithBottomMargin(dp(8)));

        tagsInput = createInput(tagsToText(task == null ? Collections.emptyList() : task.getTags()));
        tagsInput.setHint("#work #health");
        tagsInput.addTextChangedListener(previewWatcher());
        card.addView(createInputBlock("Теги", tagsInput), fullWidthWithBottomMargin(dp(8)));

        checkboxTaskInput = new CheckBox(this);
        checkboxTaskInput.setText("Сохранять как checkbox-задачу");
        checkboxTaskInput.setTextColor(getColor(R.color.text_secondary));
        checkboxTaskInput.setChecked(task == null || isCheckboxTask(task.getRawLine()));
        checkboxTaskInput.setOnCheckedChangeListener((button, checked) -> updatePreview());
        card.addView(checkboxTaskInput, fullWidth());
        return card;
    }

    private LinearLayout createSubtasksSection() {
        LinearLayout card = createSectionCard("Подзадачи", "Вложенные markdown checklist items под основной задачей.");

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        subtaskSummaryText = createText("", 13, R.color.text_secondary, false);
        header.addView(subtaskSummaryText, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        ));
        Button add = createSecondaryButton("+ Подзадача");
        add.setOnClickListener(view -> showSubtaskDialog(-1));
        header.addView(add, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(38)
        ));
        card.addView(header, fullWidthWithBottomMargin(dp(6)));

        subtaskList = new LinearLayout(this);
        subtaskList.setOrientation(LinearLayout.VERTICAL);
        card.addView(subtaskList, fullWidth());
        renderSubtasks();
        return card;
    }

    private LinearLayout createPreviewSection() {
        LinearLayout card = createCardContainer();
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.setOnClickListener(view -> {
            previewExpanded = !previewExpanded;
            updatePreview();
        });

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        previewTitle = createText("Markdown preview", 14, R.color.text_primary, true);
        row.addView(previewTitle, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        ));
        previewChevron = createText("", 18, R.color.text_secondary, true);
        previewChevron.setGravity(Gravity.CENTER);
        row.addView(previewChevron, new LinearLayout.LayoutParams(dp(28), dp(28)));
        card.addView(row, fullWidth());

        previewText = createText("", 13, R.color.text_primary, false);
        previewText.setTypeface(Typeface.MONOSPACE);
        previewText.setPadding(0, dp(8), 0, 0);
        card.addView(previewText, fullWidth());
        return card;
    }

    private LinearLayout createDangerSection() {
        LinearLayout card = createSectionCard("Danger zone", "Удаление уберет задачу и ее вложенные строки из markdown.");
        Button delete = createSecondaryButton("Удалить задачу");
        delete.setTextColor(getColor(R.color.error_text));
        delete.setOnClickListener(view -> confirmDelete());
        card.addView(delete, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(44)
        ));
        return card;
    }

    private LinearLayout createSectionCard(String title, String subtitle) {
        LinearLayout card = createCardContainer();
        card.addView(createText(title, 16, R.color.text_primary, true), fullWidth());
        TextView subtitleView = createText(subtitle, 12, R.color.text_secondary, false);
        subtitleView.setPadding(0, dp(2), 0, dp(10));
        card.addView(subtitleView, fullWidth());
        return card;
    }

    private LinearLayout createInputBlock(String label, EditText input) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.addView(createLabel(label), fullWidthWithBottomMargin(dp(4)));
        block.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(46)
        ));
        return block;
    }

    private TextView createLabel(String text) {
        return createText(text, 12, R.color.text_secondary, false);
    }

    private void addSmallValueButton(LinearLayout row, String label, String value, int leftMargin) {
        Button button = createSegmentButton(label, value.equals(valueOf(repeatInput)));
        button.setOnClickListener(view -> {
            repeatInput.setText(value);
            updatePreview();
        });
        addCompactButton(row, button, leftMargin);
    }

    private void addPriorityButton(LinearLayout row, String label, TaskPriority priority, int leftMargin) {
        Button button = createSegmentButton(label, selectedPriority == priority);
        button.setOnClickListener(view -> {
            selectedPriority = priority == null ? TaskPriority.NONE : priority;
            rebuildPriorityRow(row);
            updatePreview();
        });
        addCompactButton(row, button, leftMargin);
    }

    private void rebuildPriorityRow(LinearLayout row) {
        row.removeAllViews();
        addPriorityButton(row, "Нет", TaskPriority.NONE, 0);
        addPriorityButton(row, "Низкий", TaskPriority.LOW, dp(6));
        addPriorityButton(row, "Средний", TaskPriority.MEDIUM, dp(6));
        addPriorityButton(row, "Высокий", TaskPriority.HIGH, dp(6));
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
        LocalTime initial;
        try {
            initial = LocalTime.parse(valueOf(timeInput), TIME);
        } catch (RuntimeException exception) {
            initial = LocalTime.now();
        }
        new TimePickerDialog(
                this,
                (view, hourOfDay, minute) -> {
                    timeInput.setText(TIME.format(LocalTime.of(hourOfDay, minute)));
                    updatePreview();
                },
                initial.getHour(),
                initial.getMinute(),
                true
        ).show();
    }

    private void renderSubtasks() {
        if (subtaskList == null || subtaskSummaryText == null) {
            return;
        }
        subtaskList.removeAllViews();
        int completed = 0;
        for (SubtaskDraft draft : subtaskDrafts) {
            if (draft.completed) {
                completed++;
            }
        }
        subtaskSummaryText.setText(subtaskDrafts.isEmpty()
                ? "Подзадач пока нет"
                : completed + "/" + subtaskDrafts.size() + " выполнено");

        for (int i = 0; i < subtaskDrafts.size(); i++) {
            subtaskList.addView(createSubtaskRow(i), fullWidthWithBottomMargin(dp(6)));
        }
    }

    private LinearLayout createSubtaskRow(int index) {
        SubtaskDraft draft = subtaskDrafts.get(index);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(8), dp(6), dp(8));
        row.setBackground(createRoundedBackground(
                getColor(R.color.chip_background),
                getColor(R.color.chip_stroke),
                8
        ));

        TextView status = createText(draft.completed ? "✓" : "", 14, R.color.status_completed_text, true);
        status.setGravity(Gravity.CENTER);
        status.setBackground(createCircleOutlineBackground(
                draft.completed ? getColor(R.color.status_completed_background) : Color.TRANSPARENT,
                draft.completed ? getColor(R.color.status_completed_text) : getColor(R.color.text_secondary)
        ));
        row.addView(status, new LinearLayout.LayoutParams(dp(26), dp(26)));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView title = createText(draft.title.isEmpty() ? "Подзадача" : draft.title, 14, R.color.text_primary, true);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(title, fullWidth());
        String meta = draft.meta();
        if (!meta.isEmpty()) {
            TextView metaView = createText(meta, 12, R.color.text_secondary, false);
            metaView.setSingleLine(true);
            metaView.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(metaView, fullWidthWithTopMargin(dp(1)));
        }
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        );
        textParams.setMargins(dp(10), 0, dp(6), 0);
        row.addView(texts, textParams);

        ImageButton edit = createPlainIconButton(R.drawable.ic_edit, "Редактировать подзадачу");
        edit.setOnClickListener(view -> showSubtaskDialog(index));
        row.addView(edit, new LinearLayout.LayoutParams(dp(34), dp(34)));

        ImageButton delete = createPlainIconButton(R.drawable.ic_delete, "Удалить подзадачу");
        delete.setOnClickListener(view -> {
            subtaskDrafts.remove(index);
            renderSubtasks();
            updatePreview();
        });
        row.addView(delete, new LinearLayout.LayoutParams(dp(34), dp(34)));
        return row;
    }

    private void showSubtaskDialog(int index) {
        SubtaskDraft draft = index >= 0 ? new SubtaskDraft(subtaskDrafts.get(index)) : new SubtaskDraft();

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(4), dp(8), dp(4), 0);

        EditText title = createInput(draft.title);
        title.setHint("Текст подзадачи");
        form.addView(createInputBlock("Текст", title), fullWidthWithBottomMargin(dp(8)));

        LinearLayout dueRow = new LinearLayout(this);
        dueRow.setOrientation(LinearLayout.HORIZONTAL);
        EditText date = createInput(draft.date);
        date.setHint("yyyy-MM-dd");
        dueRow.addView(createInputBlock("Дата", date), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        EditText time = createInput(draft.time);
        time.setHint("HH:mm");
        LinearLayout.LayoutParams subTimeParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        subTimeParams.setMargins(dp(8), 0, 0, 0);
        dueRow.addView(createInputBlock("Время", time), subTimeParams);
        form.addView(dueRow, fullWidthWithBottomMargin(dp(8)));

        EditText repeat = createInput(draft.repeat);
        repeat.setHint("15m, 1h");
        form.addView(createInputBlock("Повтор", repeat), fullWidthWithBottomMargin(dp(8)));

        CheckBox repeatUntilDone = new CheckBox(this);
        repeatUntilDone.setText("Повторять до выполнения");
        repeatUntilDone.setTextColor(getColor(R.color.text_secondary));
        repeatUntilDone.setChecked(draft.repeatUntilDone);
        form.addView(repeatUntilDone, fullWidth());

        CheckBox completed = new CheckBox(this);
        completed.setText("Выполнена");
        completed.setTextColor(getColor(R.color.text_secondary));
        completed.setChecked(draft.completed);
        form.addView(completed, fullWidth());

        EditText priority = createInput(draft.priority);
        priority.setHint("low, medium, high");
        form.addView(createInputBlock("Приоритет", priority), fullWidthWithBottomMargin(dp(8)));

        EditText tags = createInput(draft.tags);
        tags.setHint("#work #health");
        form.addView(createInputBlock("Теги", tags), fullWidthWithBottomMargin(dp(8)));

        new AlertDialog.Builder(this)
                .setTitle(index >= 0 ? "Подзадача" : "Новая подзадача")
                .setView(form)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Сохранить", (dialog, which) -> {
                    draft.title = valueOf(title);
                    draft.date = valueOf(date);
                    draft.time = valueOf(time);
                    draft.repeat = valueOf(repeat);
                    draft.repeatUntilDone = repeatUntilDone.isChecked();
                    draft.completed = completed.isChecked();
                    draft.priority = valueOf(priority);
                    draft.tags = valueOf(tags);
                    if (index >= 0) {
                        subtaskDrafts.set(index, draft);
                    } else {
                        subtaskDrafts.add(draft);
                    }
                    renderSubtasks();
                    updatePreview();
                })
                .show();
    }

    private void saveTask() {
        String candidate = currentMarkdownBlock();
        if (!validateCandidate(candidate, false)) {
            return;
        }

        TaskEditResult result = task == null
                ? NoteStore.appendTaskBlock(this, candidate)
                : NoteStore.replaceTaskBlock(this, taskKey, candidate);
        handleWriteResult(result);
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle("Удалить задачу?")
                .setMessage("Задача и вложенные подзадачи будут удалены из markdown-файла.")
                .setPositiveButton("Удалить", (dialog, which) ->
                        handleWriteResult(NoteStore.deleteTaskBlock(this, taskKey)))
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void handleWriteResult(TaskEditResult result) {
        if (result.isUpdated()) {
            NoteChangeMonitor.syncNow(this, true);
            Toast.makeText(this, "Изменения сохранены", Toast.LENGTH_SHORT).show();
            setResult(RESULT_OK);
            finish();
            return;
        }

        String message = result.getMessage() == null ? "Не удалось записать файл" : result.getMessage();
        statusText.setText(message);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void requestClose() {
        if (!isDirty()) {
            finish();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Закрыть без сохранения?")
                .setMessage("Несохраненные изменения будут потеряны.")
                .setPositiveButton("Закрыть", (dialog, which) -> finish())
                .setNegativeButton("Остаться", null)
                .show();
    }

    private boolean isDirty() {
        return !currentMarkdownBlock().equals(initialMarkdownBlock);
    }

    private boolean validateCandidate(String candidate, boolean showSuccess) {
        String error = validationError(candidate);
        if (!error.isEmpty()) {
            statusText.setText(error);
            return false;
        }
        statusText.setText(showSuccess ? "Формат корректный." : "");
        return true;
    }

    private String validationError(String candidate) {
        if (loadError != null && task == null && defaultDocument == null) {
            return "Источник недоступен: " + loadError;
        }
        if (candidate == null || candidate.trim().isEmpty()) {
            return "Markdown-блок пустой.";
        }

        TaskParseResult result = TaskParser.parseDocument(
                candidate + "\n",
                LocalDate.now(),
                "preview.md",
                TaskFormatSettings.load(this)
        );
        if (!result.getErrors().isEmpty()) {
            return formatErrors(result.getErrors());
        }
        if (result.getTasks().isEmpty()) {
            return "Строка не распознана как задача.";
        }
        if (result.getTasks().get(0).getReminderAt() == null) {
            return "Для уведомления нужно указать дату или время через @due(...).";
        }
        return "";
    }

    private String currentMarkdownBlock() {
        StringBuilder builder = new StringBuilder(currentParentMarkdownLine());
        for (SubtaskDraft draft : subtaskDrafts) {
            String line = draft.toMarkdownLine();
            if (!line.isEmpty()) {
                builder.append('\n').append("  ").append(line);
            }
        }
        return builder.toString().trim();
    }

    private String currentParentMarkdownLine() {
        String title = valueOf(titleInput);
        if (title.isEmpty()) {
            title = "Новое уведомление";
        }

        StringBuilder builder = new StringBuilder();
        if (checkboxTaskInput == null || checkboxTaskInput.isChecked()) {
            boolean completed = task != null && task.isCompleted();
            builder.append(completed ? "- [x] " : "- [ ] ");
        }
        builder.append(title);

        appendDue(builder, dueValue());
        appendRepeat(builder, valueOf(repeatInput), repeatUntilDoneInput != null && repeatUntilDoneInput.isChecked());
        appendPriority(builder, selectedPriority);
        appendGroup(builder, valueOf(groupInput));
        appendTags(builder, valueOf(tagsInput));
        if (task != null && task.isSkipped()) {
            builder.append(" @skipped");
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

    private void appendDue(StringBuilder builder, String dueValue) {
        if (dueValue != null && !dueValue.trim().isEmpty()) {
            builder.append(" @due(").append(dueValue.trim()).append(")");
        }
    }

    private void appendRepeat(StringBuilder builder, String repeat, boolean untilDone) {
        if (repeat != null && !repeat.trim().isEmpty()) {
            builder.append(untilDone ? " @repeatUntilDone(" : " @repeat(")
                    .append(repeat.trim())
                    .append(")");
        }
    }

    private void appendPriority(StringBuilder builder, TaskPriority priority) {
        String token = priorityToToken(priority);
        if (!token.isEmpty()) {
            builder.append(" @priority(").append(token).append(")");
        }
    }

    private void appendGroup(StringBuilder builder, String group) {
        if (group != null && !group.trim().isEmpty()
                && !ObsidianTask.DEFAULT_GROUP.equals(group.trim())) {
            builder.append(" @group(").append(group.trim()).append(")");
        }
    }

    private void appendTags(StringBuilder builder, String tags) {
        if (tags != null && !tags.trim().isEmpty()) {
            builder.append(" @tag(").append(tags.trim()).append(")");
        }
    }

    private void updatePreview() {
        String candidate = currentMarkdownBlock();
        if (previewText != null) {
            previewText.setText(candidate);
            previewText.setVisibility(previewExpanded ? View.VISIBLE : View.GONE);
        }
        if (previewChevron != null) {
            previewChevron.setText(previewExpanded ? "−" : "+");
        }

        String error = validationError(candidate);
        boolean valid = error.isEmpty();
        if (saveButton != null) {
            saveButton.setEnabled(valid);
            saveButton.setAlpha(valid ? 1f : 0.55f);
        }
        if (statusText != null) {
            statusText.setText(error);
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
        if (taskMatch != null && task != null) {
            return compactName(taskMatch.getDisplayName()) + " · строка " + task.getLineNumber();
        }
        if (defaultDocument != null) {
            return compactName(defaultDocument.getDisplayName()) + " · файл для записи";
        }
        return loadError == null ? compactName(NoteStore.sourceLabel(this)) : loadError;
    }

    private String formatErrors(List<TaskParseError> errors) {
        StringBuilder builder = new StringBuilder("Ошибка формата:");
        int limit = Math.min(3, errors.size());
        for (int i = 0; i < limit; i++) {
            builder.append('\n').append(errors.get(i).format());
        }
        if (errors.size() > limit) {
            builder.append('\n').append("Еще ошибок: ").append(errors.size() - limit);
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

    private EditText createInput(String value) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(value == null ? "" : value);
        input.setSelectAllOnFocus(false);
        input.setTextColor(getColor(R.color.text_primary));
        input.setHintTextColor(getColor(R.color.text_secondary));
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
        ImageButton button = createPlainIconButton(iconRes, description);
        button.setBackground(createRoundedBackground(
                getColor(R.color.icon_button_background),
                0,
                18
        ));
        return button;
    }

    private ImageButton createPlainIconButton(int iconRes, String description) {
        ImageButton button = new ImageButton(this);
        button.setImageResource(iconRes);
        button.setContentDescription(description);
        button.setColorFilter(getColor(R.color.text_primary));
        button.setPadding(dp(9), dp(9), dp(9), dp(9));
        button.setBackgroundColor(Color.TRANSPARENT);
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

    private GradientDrawable createSheetBackground() {
        GradientDrawable drawable = createRoundedBackground(
                getColor(R.color.background),
                getColor(R.color.card_stroke),
                22
        );
        float radius = dp(22);
        drawable.setCornerRadii(new float[]{
                radius, radius,
                radius, radius,
                0f, 0f,
                0f, 0f
        });
        return drawable;
    }

    private GradientDrawable createCircleOutlineBackground(int color, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color);
        drawable.setStroke(dp(1), strokeColor);
        return drawable;
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

    private LinearLayout.LayoutParams fullWidthWithBottomMargin(int bottomMargin) {
        LinearLayout.LayoutParams params = fullWidth();
        params.setMargins(0, 0, 0, bottomMargin);
        return params;
    }

    private LinearLayout.LayoutParams fullWidthWithTopMargin(int topMargin) {
        LinearLayout.LayoutParams params = fullWidth();
        params.setMargins(0, topMargin, 0, 0);
        return params;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static final class SubtaskDraft {
        private String title = "";
        private String date = "";
        private String time = "";
        private String repeat = "";
        private String priority = "";
        private String tags = "";
        private boolean repeatUntilDone;
        private boolean completed;
        private boolean skipped;

        private SubtaskDraft() {
        }

        private SubtaskDraft(SubtaskDraft source) {
            this.title = source.title;
            this.date = source.date;
            this.time = source.time;
            this.repeat = source.repeat;
            this.priority = source.priority;
            this.tags = source.tags;
            this.repeatUntilDone = source.repeatUntilDone;
            this.completed = source.completed;
            this.skipped = source.skipped;
        }

        private static SubtaskDraft fromTask(ObsidianTask task) {
            SubtaskDraft draft = new SubtaskDraft();
            draft.title = task.getTitle();
            if (task.getReminderAt() != null) {
                draft.date = DATE.format(task.getReminderAt().toLocalDate());
                draft.time = TIME.format(task.getReminderAt().toLocalTime());
            }
            draft.repeat = durationToTokenStatic(task.getRepeatInterval());
            draft.repeatUntilDone = task.getRepeatMode() == RepeatMode.UNTIL_DONE;
            draft.completed = task.isCompleted();
            draft.skipped = task.isSkipped();
            draft.priority = priorityToTokenStatic(task.getPriority());
            draft.tags = tagsToTextStatic(task.getTags());
            return draft;
        }

        private String toMarkdownLine() {
            String normalizedTitle = title == null ? "" : title.trim();
            if (normalizedTitle.isEmpty()) {
                normalizedTitle = "Подзадача";
            }

            StringBuilder builder = new StringBuilder(completed ? "- [x] " : "- [ ] ");
            builder.append(normalizedTitle);

            String due = dueValue();
            if (!due.isEmpty()) {
                builder.append(" @due(").append(due).append(")");
            }
            if (repeat != null && !repeat.trim().isEmpty()) {
                builder.append(repeatUntilDone ? " @repeatUntilDone(" : " @repeat(")
                        .append(repeat.trim())
                        .append(")");
            }
            if (priority != null && !priority.trim().isEmpty()) {
                builder.append(" @priority(").append(priority.trim()).append(")");
            }
            if (tags != null && !tags.trim().isEmpty()) {
                builder.append(" @tag(").append(tags.trim()).append(")");
            }
            if (skipped) {
                builder.append(" @skipped");
            }
            return builder.toString().trim();
        }

        private String dueValue() {
            String cleanDate = date == null ? "" : date.trim();
            String cleanTime = time == null ? "" : time.trim();
            if (!cleanDate.isEmpty() && !cleanTime.isEmpty()) {
                return cleanDate + " " + cleanTime;
            }
            if (!cleanDate.isEmpty()) {
                return cleanDate;
            }
            return cleanTime;
        }

        private String meta() {
            List<String> parts = new ArrayList<>();
            String due = dueValue();
            if (!due.isEmpty()) {
                parts.add(due);
            }
            if (repeat != null && !repeat.trim().isEmpty()) {
                parts.add((repeatUntilDone ? "до выполнения " : "повтор ") + repeat.trim());
            }
            if (priority != null && !priority.trim().isEmpty()) {
                parts.add(priority.trim());
            }
            if (tags != null && !tags.trim().isEmpty()) {
                parts.add(tags.trim());
            }
            if (parts.isEmpty()) {
                return "";
            }

            StringBuilder builder = new StringBuilder();
            for (String part : parts) {
                if (builder.length() > 0) {
                    builder.append(" · ");
                }
                builder.append(part);
            }
            return builder.toString();
        }

        private static String durationToTokenStatic(Duration duration) {
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

        private static String priorityToTokenStatic(TaskPriority priority) {
            return priority == null || priority == TaskPriority.NONE
                    ? ""
                    : priority.name().toLowerCase(Locale.ROOT);
        }

        private static String tagsToTextStatic(List<String> tags) {
            StringBuilder builder = new StringBuilder();
            for (String tag : tags) {
                if (builder.length() > 0) {
                    builder.append(' ');
                }
                builder.append(tag);
            }
            return builder.toString();
        }
    }
}
