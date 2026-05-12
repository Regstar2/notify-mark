package com.regstar.obsidiannotification.ui;

import com.regstar.obsidiannotification.R;

import com.regstar.obsidiannotification.core.reminders.*;
import com.regstar.obsidiannotification.core.source.*;
import com.regstar.obsidiannotification.core.tasks.*;
import com.regstar.obsidiannotification.debug.*;
import com.regstar.obsidiannotification.prefs.*;
import com.regstar.obsidiannotification.support.ErrorLog;

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
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.ViewConfiguration;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sheet-style editor for creating and updating one task or subtask.
 *
 * <p>This activity edits the markdown-backed task model directly and generates
 * preview/write-back content through the shared parser and writer classes
 * instead of maintaining a separate "form only" task format.</p>
 */
public final class TaskEditActivity extends Activity {
    public static final String EXTRA_TASK_KEY = "task_key";
    public static final String EXTRA_DUE_DATE = "due_date";

    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private enum RepeatEditorMode {
        NONE,
        INTERVAL,
        WEEKLY,
        MONTHLY,
        CUSTOM
    }

    private enum MonthRepeatMode {
        SAME_DAY,
        FIXED_DAY,
        LAST_DAY
    }

    private final List<SubtaskDraft> subtaskDrafts = new ArrayList<>();

    private String taskKey;
    private LocalDate prefilledDate;
    private ObsidianTask task;
    private NoteStore.TaskDocumentMatch taskMatch;
    private NoteStore.NoteDocument defaultDocument;
    private String loadError;
    private String initialMarkdownBlock = "";
    private boolean previewExpanded;
    private boolean extraExpanded;
    private TaskPriority selectedPriority = TaskPriority.NONE;

    private FrameLayout rootContainer;
    private LinearLayout sheetContainer;
    private TextView statusText;
    private TextView previewText;
    private TextView previewChevron;
    private LinearLayout subtaskList;
    private TextView subtaskSummaryText;
    private LinearLayout extraContent;
    private TextView extraChevron;
    private TextView extraSummaryText;
    private EditText titleInput;
    private EditText dateInput;
    private EditText timeInput;
    private EditText repeatInput;
    private EditText repeatUntilDoneIntervalInput;
    private EditText overdueGraceInput;
    private EditText snoozeInput;
    private EditText tagsInput;
    private AutoCompleteTextView groupInput;
    private CheckBox checkboxTaskInput;
    private CheckBox repeatUntilDoneInput;
    private CheckBox overdueGraceEnabledInput;
    private LinearLayout repeatUntilDoneIntervalRow;
    private LinearLayout overdueGraceRow;
    private ImageButton saveButton;
    private SubtaskEditorSheet activeSubtaskEditor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemePreferences.applySheet(this);
        super.onCreate(savedInstanceState);
        configureSheetWindow();

        taskKey = getIntent().getStringExtra(EXTRA_TASK_KEY);
        prefilledDate = parsePrefilledDate(getIntent().getStringExtra(EXTRA_DUE_DATE));
        loadTaskContext();
        selectedPriority = task == null ? TaskPriority.NONE : task.getPriority();
        extraExpanded = task == null && valueForNewTaskNeedsExtraExpanded();
        buildUi();
        initialMarkdownBlock = currentMarkdownBlock();
        updatePreview();
    }

    @Override
    public void onBackPressed() {
        if (activeSubtaskEditor != null) {
            activeSubtaskEditor.requestClose();
            return;
        }
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
        params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        window.setAttributes(params);
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private void loadTaskContext() {
        try {
            if (taskKey != null && !taskKey.trim().isEmpty()) {
                taskMatch = NoteStore.findTaskDocument(this, taskKey);
                if (taskMatch == null) {
                    loadError = getString(R.string.task_edit_load_task_missing);
                    return;
                }
                task = taskMatch.getTask();
                TaskFormatSettings formatSettings = TaskFormatSettings.load(this);
                for (ObsidianTask subtask : task.getSubtasks()) {
                    subtaskDrafts.add(SubtaskDraft.fromTask(subtask, formatSettings));
                }
            } else {
                defaultDocument = NoteStore.findDefaultWriteDocument(this);
            }
        } catch (IOException | RuntimeException exception) {
            loadError = exception.getMessage();
            ErrorLog.record(this, getString(R.string.task_edit_open_error), exception);
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
        rootContainer = new FrameLayout(this);
        rootContainer.setBackgroundColor(Color.TRANSPARENT);
        rootContainer.setOnClickListener(view -> requestClose());

        sheetContainer = new LinearLayout(this);
        sheetContainer.setOrientation(LinearLayout.VERTICAL);
        sheetContainer.setClickable(true);
        sheetContainer.setBackground(createSheetBackground());
        sheetContainer.setPadding(dp(12), dp(8), dp(12), dp(8));

        View handle = new View(this);
        handle.setBackground(createRoundedBackground(getColor(R.color.card_stroke), 0, 99));
        LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(dp(44), dp(4));
        handleParams.gravity = Gravity.CENTER_HORIZONTAL;
        handleParams.setMargins(0, 0, 0, dp(4));
        sheetContainer.addView(handle, handleParams);

        LinearLayout header = createHeader();
        sheetContainer.addView(header, fullWidthWithBottomMargin(dp(4)));
        attachSheetDismissGesture(handle, header);

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, 0, 0, dp(18));

        statusText = createText("", 13, R.color.error_text, false);
        statusText.setPadding(dp(2), 0, dp(2), dp(6));
        statusText.setVisibility(View.GONE);
        content.addView(statusText, fullWidth());
        content.addView(createBasicSection(), fullWidthWithBottomMargin(dp(8)));
        content.addView(createSubtasksSection(), fullWidthWithBottomMargin(dp(8)));
        content.addView(createExtraSection(), fullWidthWithBottomMargin(dp(8)));
        content.addView(createPreviewSection(), fullWidthWithBottomMargin(dp(8)));
        if (task != null) {
            content.addView(createDangerSection(), fullWidthWithBottomMargin(dp(8)));
        }

        scroll.setFillViewport(true);
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        sheetContainer.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        FrameLayout.LayoutParams sheetParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.BOTTOM
        );
        sheetParams.setMargins(0, dp(56), 0, 0);
        rootContainer.addView(sheetContainer, sheetParams);
        setContentView(rootContainer);
    }

    private LinearLayout createHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, 0, 0, dp(2));

        ImageButton close = createIconButton(R.drawable.ic_close, getString(R.string.common_close));
        close.setOnClickListener(view -> requestClose());
        header.addView(close, new LinearLayout.LayoutParams(dp(42), dp(42)));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView title = createText(
                task == null
                        ? getString(R.string.task_edit_title_new)
                        : getString(R.string.task_edit_title_edit),
                18,
                R.color.text_primary,
                true
        );
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

        saveButton = createPrimaryIconButton(R.drawable.ic_check, getString(R.string.common_save));
        saveButton.setOnClickListener(view -> saveTask());
        header.addView(saveButton, new LinearLayout.LayoutParams(dp(42), dp(42)));
        return header;
    }

    private LinearLayout createBasicSection() {
        LinearLayout card = createSectionCard(getString(R.string.task_edit_section_basic), null);
        titleInput = createInput(task == null ? getString(R.string.task_edit_title_new) : task.getTitle());
        titleInput.setHint(getString(R.string.task_edit_title_hint));
        titleInput.addTextChangedListener(previewWatcher());
        card.addView(createInputBlock(getString(R.string.task_edit_field_title), titleInput), fullWidthWithBottomMargin(dp(8)));

        LocalDateTime due = task == null || task.getReminderAt() == null
                ? defaultDueDateTime()
                : task.getReminderAt();
        dateInput = createInput(initialDateValue(due));
        dateInput.setHint(getString(R.string.task_edit_hint_date_example));
        dateInput.setOnClickListener(view -> showDatePicker());
        dateInput.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus) {
                showDatePicker();
            }
        });
        dateInput.addTextChangedListener(previewWatcher());
        timeInput = createInput(initialTimeValue(due));
        timeInput.setHint(getString(R.string.task_edit_hint_time_example));
        timeInput.setOnClickListener(view -> showTimePicker());
        timeInput.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus) {
                showTimePicker();
            }
        });
        timeInput.addTextChangedListener(previewWatcher());
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(createInputBlock(getString(R.string.task_edit_field_date), dateInput), new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        ));
        LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        );
        timeParams.setMargins(dp(6), 0, 0, 0);
        row.addView(createInputBlock(getString(R.string.task_edit_field_time), timeInput), timeParams);
        card.addView(row, fullWidthWithBottomMargin(dp(8)));

        repeatInput = createInput(initialRepeatToken());
        repeatInput.addTextChangedListener(previewWatcher());
        card.addView(
                new RepeatEditorController(repeatInput, this::updatePreview).createView(),
                fullWidthWithBottomMargin(dp(8))
        );

        repeatUntilDoneInput = new CheckBox(this);
        repeatUntilDoneInput.setText(getString(R.string.task_edit_repeat_until_done_toggle));
        repeatUntilDoneInput.setTextColor(getColor(R.color.text_secondary));
        repeatUntilDoneInput.setChecked(!initialRepeatUntilDoneToken().isEmpty());
        repeatUntilDoneInput.setOnCheckedChangeListener((button, checked) -> {
            updateRepeatUntilDoneUi();
            updatePreview();
        });
        card.addView(repeatUntilDoneInput, fullWidthWithBottomMargin(dp(6)));

        repeatUntilDoneIntervalInput = createInput(initialRepeatUntilDoneToken());
        repeatUntilDoneIntervalInput.setHint(getString(R.string.task_edit_hint_repeat_until_done_example));
        repeatUntilDoneIntervalInput.addTextChangedListener(previewWatcher());
        repeatUntilDoneIntervalRow = createInputBlock(
                getString(R.string.task_edit_repeat_until_done_interval),
                repeatUntilDoneIntervalInput
        );
        card.addView(repeatUntilDoneIntervalRow, fullWidthWithBottomMargin(dp(8)));

        overdueGraceEnabledInput = new CheckBox(this);
        overdueGraceEnabledInput.setText(getString(R.string.task_edit_overdue_grace_toggle));
        overdueGraceEnabledInput.setTextColor(getColor(R.color.text_secondary));
        overdueGraceEnabledInput.setChecked(!initialOverdueGraceToken().isEmpty());
        overdueGraceEnabledInput.setOnCheckedChangeListener((buttonView, checked) -> {
            updateOverdueGraceUi();
            updatePreview();
        });
        card.addView(overdueGraceEnabledInput, fullWidthWithBottomMargin(dp(6)));

        overdueGraceInput = createInput(initialOverdueGraceToken());
        overdueGraceInput.setHint(getString(R.string.task_edit_hint_grace_example));
        overdueGraceInput.addTextChangedListener(previewWatcher());
        overdueGraceRow = createInputBlock(
                getString(R.string.task_edit_overdue_grace_label),
                overdueGraceInput
        );
        card.addView(overdueGraceRow, fullWidth());

        updateRepeatUntilDoneUi();
        updateOverdueGraceUi();
        return card;
    }
    private LinearLayout createExtraSection() {
        LinearLayout card = createCardContainer();
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setClickable(true);
        header.setOnClickListener(view -> {
            extraExpanded = !extraExpanded;
            updateExtraSection();
        });
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(createText(getString(R.string.task_edit_section_extra), 15, R.color.text_primary, true), fullWidth());
        extraSummaryText = createText("", 12, R.color.text_secondary, false);
        texts.addView(extraSummaryText, fullWidthWithTopMargin(dp(2)));
        header.addView(texts, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        ));
        extraChevron = createText("", 18, R.color.text_secondary, true);
        extraChevron.setGravity(Gravity.CENTER);
        header.addView(extraChevron, new LinearLayout.LayoutParams(dp(28), dp(28)));
        card.addView(header, fullWidthWithBottomMargin(dp(8)));
        extraContent = new LinearLayout(this);
        extraContent.setOrientation(LinearLayout.VERTICAL);
        List<String> groupSuggestions = TaskGroupSuggestionProvider.distinctSortedGroups(
                TaskCache.loadActiveTasks(this));
        groupInput = createGroupAutocomplete(task == null ? "" : task.getGroup(), groupSuggestions);
        groupInput.setHint(getString(R.string.task_edit_hint_group_example));
        groupInput.addTextChangedListener(previewWatcher());
        extraContent.addView(createInputBlock(
                getString(R.string.task_edit_field_group),
                groupInput
        ), fullWidthWithBottomMargin(dp(4)));
        extraContent.addView(
                createGroupSuggestionChipRow(groupSuggestions),
                fullWidthWithBottomMargin(dp(8))
        );
        extraContent.addView(createLabel(getString(R.string.task_edit_field_priority)), fullWidthWithBottomMargin(dp(4)));
        LinearLayout priorityRow = new LinearLayout(this);
        priorityRow.setOrientation(LinearLayout.HORIZONTAL);
        rebuildPriorityRow(priorityRow);
        extraContent.addView(priorityRow, fullWidthWithBottomMargin(dp(8)));
        tagsInput = createInput(tagsToText(task == null ? Collections.emptyList() : task.getTags()));
        tagsInput.setHint(getString(R.string.task_edit_hint_tags_example));
        tagsInput.addTextChangedListener(previewWatcher());
        extraContent.addView(createInputBlock(getString(R.string.task_edit_field_tags), tagsInput),
                fullWidthWithBottomMargin(dp(8)));
        snoozeInput = createInput(initialSnoozeToken());
        snoozeInput.setHint(defaultSnoozeToken());
        snoozeInput.addTextChangedListener(previewWatcher());
        extraContent.addView(createInputBlock(
                getString(R.string.task_edit_field_snooze),
                snoozeInput
        ), fullWidthWithBottomMargin(dp(8)));
        checkboxTaskInput = new CheckBox(this);
        checkboxTaskInput.setText(getString(R.string.task_edit_checkbox_task));
        checkboxTaskInput.setTextColor(getColor(R.color.text_secondary));
        checkboxTaskInput.setChecked(task == null || isCheckboxTask(task.getRawLine()));
        checkboxTaskInput.setOnCheckedChangeListener((button, checked) -> updatePreview());
        extraContent.addView(checkboxTaskInput, fullWidth());
        card.addView(extraContent, fullWidth());
        updateExtraSection();
        return card;
    }
    private LinearLayout createSubtasksSection() {
        LinearLayout card = createCardContainer();

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(createText(getString(R.string.task_edit_section_subtasks), 15, R.color.text_primary, true), fullWidth());
        subtaskSummaryText = createText("", 12, R.color.text_secondary, false);
        texts.addView(subtaskSummaryText, fullWidthWithTopMargin(dp(2)));
        header.addView(texts, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        ));

        Button add = createPrimaryButton("+");
        add.setTextSize(22);
        add.setGravity(Gravity.CENTER);
        add.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        add.setContentDescription(getString(R.string.task_edit_add_subtask));
        add.setOnClickListener(view -> showSubtaskDialog(-1));
        header.addView(add, new LinearLayout.LayoutParams(
                dp(48),
                dp(40)
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
        card.setOnClickListener(view -> {
            previewExpanded = !previewExpanded;
            updatePreview();
        });

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView previewTitle = createText(getString(R.string.task_edit_section_preview), 14, R.color.text_primary, true);
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

    private void attachSheetDismissGesture(View... dragTargets) {
        if (sheetContainer == null || dragTargets == null) {
            return;
        }
        int touchSlop = ViewConfiguration.get(this).getScaledTouchSlop();
        View.OnTouchListener listener = new View.OnTouchListener() {
            private float downX;
            private float downY;
            private boolean dragging;

            @Override
            public boolean onTouch(View view, MotionEvent event) {
                if (sheetContainer == null) {
                    return false;
                }
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = event.getRawX();
                        downY = event.getRawY();
                        dragging = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - downX;
                        float dy = event.getRawY() - downY;
                        if (!dragging) {
                            if (dy <= touchSlop || dy <= Math.abs(dx)) {
                                return true;
                            }
                            dragging = true;
                        }
                        float translation = Math.max(0f, dy);
                        sheetContainer.setTranslationY(translation);
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        if (dragging) {
                            animateSheetBack();
                            return true;
                        }
                        return false;
                    case MotionEvent.ACTION_UP:
                        if (!dragging) {
                            return false;
                        }
                        if (sheetContainer.getTranslationY() > dp(120)) {
                            if (isDirty()) {
                                animateSheetBack();
                                requestClose();
                            } else {
                                animateSheetDismiss();
                            }
                        } else {
                            animateSheetBack();
                        }
                        return true;
                    default:
                        return false;
                }
            }
        };
        for (View dragTarget : dragTargets) {
            if (dragTarget != null) {
                dragTarget.setOnTouchListener(listener);
            }
        }
    }

    private void animateSheetBack() {
        if (sheetContainer == null) {
            return;
        }
        sheetContainer.animate()
                .translationY(0f)
                .setDuration(180L)
                .start();
    }

    private void animateSheetDismiss() {
        if (sheetContainer == null || rootContainer == null) {
            finish();
            return;
        }
        float target = Math.max(rootContainer.getHeight(), dp(640));
        sheetContainer.animate()
                .translationY(target)
                .setDuration(180L)
                .withEndAction(this::finish)
                .start();
    }

    private boolean valueForNewTaskNeedsExtraExpanded() {
        return false;
    }

    private void updateRepeatUntilDoneUi() {
        if (repeatUntilDoneIntervalRow == null || repeatUntilDoneInput == null) {
            return;
        }
        boolean visible = repeatUntilDoneInput.isChecked();
        repeatUntilDoneIntervalRow.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (visible && valueOf(repeatUntilDoneIntervalInput).isEmpty()) {
            repeatUntilDoneIntervalInput.setText(defaultRepeatUntilDoneToken());
        }
    }

    private void updateOverdueGraceUi() {
        if (overdueGraceRow == null || overdueGraceEnabledInput == null) {
            return;
        }
        boolean visible = overdueGraceEnabledInput.isChecked();
        overdueGraceRow.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (visible && valueOf(overdueGraceInput).isEmpty()) {
            overdueGraceInput.setText(defaultOverdueGraceToken());
        }
    }

    private String initialRepeatToken() {
        if (task != null && task.getRepeatRule() != null) {
            return task.getRepeatRule().formatForUi();
        }
        String rawRepeat = extractFunctionValue(
                task == null ? null : task.getRawLine(),
                TaskFormatSettings.load(this).repeatKeywords()
        );
        if (!rawRepeat.isEmpty()) {
            return rawRepeat;
        }
        return task != null && task.getRepeatMode() == RepeatMode.ALWAYS
                ? durationToToken(task.getRepeatInterval())
                : "";
    }

    private String initialDateValue(LocalDateTime fallback) {
        String rawDue = extractFunctionValue(
                task == null ? null : task.getRawLine(),
                TaskFormatSettings.load(this).dueKeywords()
        );
        if (!rawDue.isEmpty()) {
            int split = rawDue.indexOf(' ');
            if (split > 0) {
                return rawDue.substring(0, split).trim();
            }
            if (rawDue.matches("\\d{4}-\\d{2}-\\d{2}")) {
                return rawDue;
            }
            return "";
        }
        return DATE.format(fallback.toLocalDate());
    }

    private String initialTimeValue(LocalDateTime fallback) {
        String rawDue = extractFunctionValue(
                task == null ? null : task.getRawLine(),
                TaskFormatSettings.load(this).dueKeywords()
        );
        if (!rawDue.isEmpty()) {
            int split = rawDue.indexOf(' ');
            if (split > 0 && split + 1 < rawDue.length()) {
                return rawDue.substring(split + 1).trim();
            }
            if (rawDue.matches("\\d{2}:\\d{2}")) {
                return rawDue;
            }
            return "";
        }
        return TIME.format(fallback.toLocalTime());
    }

    private String initialRepeatUntilDoneToken() {
        String rawRepeat = extractFunctionValue(
                task == null ? null : task.getRawLine(),
                TaskFormatSettings.load(this).repeatUntilDoneKeywords()
        );
        if (!rawRepeat.isEmpty()) {
            return rawRepeat;
        }
        if (task != null && task.getExplicitRepeatUntilDoneInterval() != null) {
            return durationToToken(task.getExplicitRepeatUntilDoneInterval());
        }
        return "";
    }

    private String initialOverdueGraceToken() {
        String rawGrace = extractFunctionValue(task == null ? null : task.getRawLine(), java.util.Arrays.asList("grace", "g"));
        if (!rawGrace.isEmpty()) {
            return rawGrace;
        }
        return task != null && task.getExplicitOverdueGracePeriod() != null
                ? durationToTokenAllowZero(task.getExplicitOverdueGracePeriod())
                : "";
    }

    private String initialSnoozeToken() {
        String rawSnooze = extractFunctionValue(task == null ? null : task.getRawLine(), Collections.singletonList("snooze"));
        if (!rawSnooze.isEmpty()) {
            return rawSnooze;
        }
        return defaultSnoozeToken();
    }

    private String repeatUntilDoneValue() {
        return repeatUntilDoneInput != null && repeatUntilDoneInput.isChecked()
                ? valueOf(repeatUntilDoneIntervalInput)
                : "";
    }

    private String overdueGraceValue() {
        return overdueGraceEnabledInput != null && overdueGraceEnabledInput.isChecked()
                ? valueOf(overdueGraceInput)
                : "";
    }

    private String snoozeValue() {
        return valueOf(snoozeInput);
    }

    private String defaultRepeatUntilDoneToken() {
        return ActionPreferences.getRepeatUntilDoneMinutes(this) + "m";
    }

    private String defaultOverdueGraceToken() {
        return ActionPreferences.getOverdueGraceMinutes(this) + "m";
    }

    private String defaultSnoozeToken() {
        return ActionPreferences.getSnoozeMinutes(this) + "m";
    }

    private boolean shouldPersistSnooze(String value) {
        if (value == null || value.trim().isEmpty()) {
            return false;
        }
        boolean explicitInSource = task != null
                && !extractFunctionValue(task.getRawLine(), Collections.singletonList("snooze")).isEmpty();
        return explicitInSource || !value.trim().equals(defaultSnoozeToken());
    }

    private void updateExtraSection() {
        if (extraContent == null || extraChevron == null || extraSummaryText == null) {
            return;
        }
        extraContent.setVisibility(extraExpanded ? View.VISIBLE : View.GONE);
        extraChevron.setText(extraExpanded ? "\u2212" : "+");
        String summary = extraSectionSummary();
        extraSummaryText.setText(summary);
        extraSummaryText.setVisibility(summary.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private String extraSectionSummary() {
        List<String> parts = new ArrayList<>();
        String group = valueOf(groupInput);
        if (!group.isEmpty() && !ObsidianTask.DEFAULT_GROUP.equals(group)) {
            parts.add(group);
        }
        if (selectedPriority != null && selectedPriority != TaskPriority.NONE) {
            parts.add(priorityLabel(selectedPriority));
        }
        String tags = valueOf(tagsInput);
        if (!tags.isEmpty()) {
            parts.add(tags);
        }
        return parts.isEmpty() ? "" : TextUtils.join(" \u00b7 ", parts);
    }

    private String priorityLabel(TaskPriority priority) {
        if (priority == TaskPriority.LOW) {
            return getString(R.string.task_edit_priority_low);
        }
        if (priority == TaskPriority.MEDIUM) {
            return getString(R.string.task_edit_priority_medium);
        }
        if (priority == TaskPriority.HIGH) {
            return getString(R.string.task_edit_priority_high);
        }
        return getString(R.string.task_edit_priority_none);
    }

    private void setStatusMessage(String message) {
        if (statusText == null) {
            return;
        }
        String safeMessage = message == null ? "" : message.trim();
        statusText.setText(safeMessage);
        statusText.setVisibility(safeMessage.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private LinearLayout createDangerSection() {
        LinearLayout card = createSectionCard(
                getString(R.string.task_edit_section_danger),
                getString(R.string.task_edit_section_danger_body)
        );
        Button delete = createSecondaryButton(getString(R.string.task_edit_delete_task));
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
        card.addView(createText(title, 15, R.color.text_primary, true), fullWidth());
        if (subtitle != null && !subtitle.trim().isEmpty()) {
            TextView subtitleView = createText(subtitle, 12, R.color.text_secondary, false);
            subtitleView.setPadding(0, dp(2), 0, dp(8));
            card.addView(subtitleView, fullWidth());
        }
        return card;
    }

    private LinearLayout createInputBlock(String label, EditText input) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.addView(createLabel(label), fullWidthWithBottomMargin(dp(3)));
        block.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(44)
        ));
        return block;
    }

    private LinearLayout createInputBlockWithSuffix(String label, EditText input, String suffix) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.addView(createLabel(label), fullWidthWithBottomMargin(dp(3)));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                0,
                dp(44),
                1
        );
        row.addView(input, inputParams);

        TextView suffixView = createText(suffix, 13, R.color.text_tertiary, true);
        suffixView.setGravity(Gravity.CENTER);
        suffixView.setPadding(dp(12), 0, dp(12), 0);
        suffixView.setBackground(createRoundedBackground(
                getColor(R.color.background),
                getColor(R.color.card_stroke),
                8
        ));
        LinearLayout.LayoutParams suffixParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(44)
        );
        suffixParams.setMargins(dp(8), 0, 0, 0);
        row.addView(suffixView, suffixParams);

        block.addView(row, fullWidth());
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
        addPriorityButton(row, getString(R.string.task_edit_priority_none), TaskPriority.NONE, 0);
        addPriorityButton(row, getString(R.string.task_edit_priority_low), TaskPriority.LOW, dp(6));
        addPriorityButton(row, getString(R.string.task_edit_priority_medium), TaskPriority.MEDIUM, dp(6));
        addPriorityButton(row, getString(R.string.task_edit_priority_high), TaskPriority.HIGH, dp(6));
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

    private EditText createNumberInput(String value, String hint) {
        EditText input = createInput(value);
        input.setHint(hint);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        return input;
    }

    private String dayToken(DayOfWeek dayOfWeek) {
        switch (dayOfWeek) {
            case MONDAY:
                return "mon";
            case TUESDAY:
                return "tue";
            case WEDNESDAY:
                return "wed";
            case THURSDAY:
                return "thu";
            case FRIDAY:
                return "fri";
            case SATURDAY:
                return "sat";
            case SUNDAY:
                return "sun";
            default:
                return "";
        }
    }

    private String dayLabel(DayOfWeek dayOfWeek) {
        switch (dayOfWeek) {
            case MONDAY:
                return getString(R.string.edit_weekday_mon);
            case TUESDAY:
                return getString(R.string.edit_weekday_tue);
            case WEDNESDAY:
                return getString(R.string.edit_weekday_wed);
            case THURSDAY:
                return getString(R.string.edit_weekday_thu);
            case FRIDAY:
                return getString(R.string.edit_weekday_fri);
            case SATURDAY:
                return getString(R.string.edit_weekday_sat);
            case SUNDAY:
                return getString(R.string.edit_weekday_sun);
            default:
                return "";
        }
    }

    private final class RepeatEditorController {
        private final EditText backingInput;
        private final Runnable onChanged;
        private final EnumSet<DayOfWeek> weeklyDays = EnumSet.noneOf(DayOfWeek.class);

        private RepeatEditorMode mode = RepeatEditorMode.NONE;
        private RepeatRule.Unit intervalUnit = RepeatRule.Unit.DAYS;
        private MonthRepeatMode monthMode = MonthRepeatMode.SAME_DAY;

        private LinearLayout modeRow;
        private LinearLayout contentContainer;
        private EditText intervalAmountInput;
        private EditText weeklyIntervalInput;
        private EditText monthlyIntervalInput;
        private EditText monthlyDayInput;
        private EditText customInput;
        private String intervalAmountValue = "1";
        private String weeklyIntervalValue = "1";
        private String monthlyIntervalValue = "1";
        private String monthlyDayValue = "1";
        private String customValue = "";

        private RepeatEditorController(EditText backingInput, Runnable onChanged) {
            this.backingInput = backingInput;
            this.onChanged = onChanged;
            initializeFromBacking();
        }

        private LinearLayout createView() {
            LinearLayout block = new LinearLayout(TaskEditActivity.this);
            block.setOrientation(LinearLayout.VERTICAL);
            block.addView(createLabel(getString(R.string.edit_repeat_title)), fullWidthWithBottomMargin(dp(3)));

            modeRow = new LinearLayout(TaskEditActivity.this);
            modeRow.setOrientation(LinearLayout.HORIZONTAL);
            block.addView(modeRow, fullWidthWithBottomMargin(dp(8)));

            contentContainer = new LinearLayout(TaskEditActivity.this);
            contentContainer.setOrientation(LinearLayout.VERTICAL);
            block.addView(contentContainer, fullWidth());

            rebuildModeRow();
            rebuildContent();
            return block;
        }

        private void initializeFromBacking() {
            String raw = valueOf(backingInput);
            if (raw.isEmpty()) {
                mode = RepeatEditorMode.NONE;
                return;
            }

            RepeatRule rule = RepeatRule.parseStoredSpec(raw);
            if (rule == null) {
                mode = RepeatEditorMode.CUSTOM;
                customValue = raw;
                return;
            }

            if (rule.getUnit() == RepeatRule.Unit.WEEKS && rule.hasDaySelector()) {
                mode = RepeatEditorMode.WEEKLY;
                weeklyDays.clear();
                weeklyDays.addAll(rule.getDaysOfWeek());
                weeklyIntervalValue = String.valueOf(rule.getAmount());
                return;
            }

            if (rule.getUnit() == RepeatRule.Unit.MONTHS) {
                mode = RepeatEditorMode.MONTHLY;
                monthlyIntervalValue = String.valueOf(rule.getAmount());
                if (rule.getMonthDaySelector() == RepeatRule.MonthDaySelector.LAST_DAY) {
                    monthMode = MonthRepeatMode.LAST_DAY;
                } else if (rule.getDayOfMonth() != null) {
                    monthMode = MonthRepeatMode.FIXED_DAY;
                    monthlyDayValue = String.valueOf(rule.getDayOfMonth());
                } else {
                    monthMode = MonthRepeatMode.SAME_DAY;
                }
                return;
            }

            mode = RepeatEditorMode.INTERVAL;
            intervalUnit = rule.getUnit();
            intervalAmountValue = String.valueOf(rule.getAmount());
        }

        private void rebuildModeRow() {
            modeRow.removeAllViews();
            addCompactButton(modeRow, createModeButton(getString(R.string.edit_repeat_mode_none), RepeatEditorMode.NONE), 0);
            addCompactButton(modeRow, createModeButton(getString(R.string.edit_repeat_mode_interval), RepeatEditorMode.INTERVAL), dp(6));
            addCompactButton(modeRow, createModeButton(getString(R.string.edit_repeat_mode_weekly), RepeatEditorMode.WEEKLY), dp(6));
            addCompactButton(modeRow, createModeButton(getString(R.string.edit_repeat_mode_monthly), RepeatEditorMode.MONTHLY), dp(6));
            if (mode == RepeatEditorMode.CUSTOM) {
                addCompactButton(modeRow, createModeButton(getString(R.string.edit_repeat_mode_custom), RepeatEditorMode.CUSTOM), dp(6));
            }
        }

        private Button createModeButton(String text, RepeatEditorMode targetMode) {
            Button button = createSegmentButton(text, mode == targetMode);
            button.setOnClickListener(view -> selectMode(targetMode));
            return button;
        }

        private void selectMode(RepeatEditorMode targetMode) {
            mode = targetMode;
            if (mode == RepeatEditorMode.INTERVAL && intervalUnit == null) {
                intervalUnit = RepeatRule.Unit.DAYS;
            }
            if (mode == RepeatEditorMode.WEEKLY && weeklyDays.isEmpty()) {
                weeklyDays.add(DayOfWeek.MONDAY);
            }
            if (mode == RepeatEditorMode.MONTHLY) {
                if (monthMode == null) {
                    monthMode = MonthRepeatMode.SAME_DAY;
                }
                if (monthlyIntervalValue == null || monthlyIntervalValue.trim().isEmpty()) {
                    monthlyIntervalValue = "1";
                }
                if (monthMode == MonthRepeatMode.FIXED_DAY
                        && (monthlyDayValue == null || monthlyDayValue.trim().isEmpty())) {
                    monthlyDayValue = "1";
                }
            }
            rebuildModeRow();
            rebuildContent();
            pushValue();
        }

        private void rebuildContent() {
            contentContainer.removeAllViews();
            switch (mode) {
                case NONE:
                    contentContainer.addView(
                            createText(getString(R.string.edit_repeat_none), 12, R.color.text_secondary, false),
                            fullWidth()
                    );
                    break;
                case INTERVAL:
                    buildIntervalContent();
                    break;
                case WEEKLY:
                    buildWeeklyContent();
                    break;
                case MONTHLY:
                    buildMonthlyContent();
                    break;
                case CUSTOM:
                    buildCustomContent();
                    break;
                default:
                    break;
            }
        }

        private void buildIntervalContent() {
            intervalAmountInput = createNumberInput(intervalAmountValue, "1");
            intervalAmountInput.addTextChangedListener(simpleWatcher(() -> {
                intervalAmountValue = valueOf(intervalAmountInput);
                pushValue();
            }));
            contentContainer.addView(
                    createInputBlockWithSuffix(getString(R.string.edit_repeat_interval_label), intervalAmountInput, intervalUnit.getToken()),
                    fullWidthWithBottomMargin(dp(4))
            );
            LinearLayout unitsRow = new LinearLayout(TaskEditActivity.this);
            unitsRow.setOrientation(LinearLayout.HORIZONTAL);
            addCompactButton(unitsRow, createIntervalUnitButton("m", RepeatRule.Unit.MINUTES), 0);
            addCompactButton(unitsRow, createIntervalUnitButton("h", RepeatRule.Unit.HOURS), dp(6));
            addCompactButton(unitsRow, createIntervalUnitButton("d", RepeatRule.Unit.DAYS), dp(6));
            addCompactButton(unitsRow, createIntervalUnitButton("w", RepeatRule.Unit.WEEKS), dp(6));
            addCompactButton(unitsRow, createIntervalUnitButton("mo", RepeatRule.Unit.MONTHS), dp(6));
            contentContainer.addView(unitsRow, fullWidth());
        }

        private Button createIntervalUnitButton(String label, RepeatRule.Unit unit) {
            Button button = createSegmentButton(label, intervalUnit == unit);
            button.setOnClickListener(view -> {
                intervalUnit = unit;
                pushValue();
                rebuildContent();
            });
            return button;
        }

        private void buildWeeklyContent() {
            if (weeklyDays.isEmpty()) {
                weeklyDays.add(DayOfWeek.MONDAY);
            }

            weeklyIntervalInput = createNumberInput(weeklyIntervalValue, "1");
            weeklyIntervalInput.addTextChangedListener(simpleWatcher(() -> {
                weeklyIntervalValue = valueOf(weeklyIntervalInput);
                pushValue();
            }));
            contentContainer.addView(
                    createInputBlockWithSuffix(
                            getString(R.string.edit_repeat_interval_label),
                            weeklyIntervalInput,
                            getString(R.string.edit_repeat_week_suffix)
                    ),
                    fullWidthWithBottomMargin(dp(8))
            );

            LinearLayout firstRow = new LinearLayout(TaskEditActivity.this);
            firstRow.setOrientation(LinearLayout.HORIZONTAL);
            addCompactButton(firstRow, createWeekdayButton(DayOfWeek.MONDAY), 0);
            addCompactButton(firstRow, createWeekdayButton(DayOfWeek.TUESDAY), dp(6));
            addCompactButton(firstRow, createWeekdayButton(DayOfWeek.WEDNESDAY), dp(6));
            addCompactButton(firstRow, createWeekdayButton(DayOfWeek.THURSDAY), dp(6));
            contentContainer.addView(firstRow, fullWidthWithBottomMargin(dp(6)));

            LinearLayout secondRow = new LinearLayout(TaskEditActivity.this);
            secondRow.setOrientation(LinearLayout.HORIZONTAL);
            addCompactButton(secondRow, createWeekdayButton(DayOfWeek.FRIDAY), 0);
            addCompactButton(secondRow, createWeekdayButton(DayOfWeek.SATURDAY), dp(6));
            addCompactButton(secondRow, createWeekdayButton(DayOfWeek.SUNDAY), dp(6));
            contentContainer.addView(secondRow, fullWidth());
        }

        private Button createWeekdayButton(DayOfWeek dayOfWeek) {
            Button button = createSegmentButton(dayLabel(dayOfWeek), weeklyDays.contains(dayOfWeek));
            button.setOnClickListener(view -> {
                if (weeklyDays.contains(dayOfWeek)) {
                    if (weeklyDays.size() == 1) {
                        return;
                    }
                    weeklyDays.remove(dayOfWeek);
                } else {
                    weeklyDays.add(dayOfWeek);
                }
                pushValue();
                rebuildContent();
            });
            return button;
        }

        private void buildMonthlyContent() {
            monthlyIntervalInput = createNumberInput(monthlyIntervalValue, "1");
            monthlyIntervalInput.addTextChangedListener(simpleWatcher(() -> {
                monthlyIntervalValue = valueOf(monthlyIntervalInput);
                pushValue();
            }));
            contentContainer.addView(
                    createInputBlockWithSuffix(
                            getString(R.string.edit_repeat_interval_label),
                            monthlyIntervalInput,
                            getString(R.string.edit_repeat_month_suffix)
                    ),
                    fullWidthWithBottomMargin(dp(8))
            );

            LinearLayout modeButtons = new LinearLayout(TaskEditActivity.this);
            modeButtons.setOrientation(LinearLayout.HORIZONTAL);
            addCompactButton(modeButtons, createMonthModeButton(getString(R.string.edit_repeat_month_mode_same_day), MonthRepeatMode.SAME_DAY), 0);
            addCompactButton(modeButtons, createMonthModeButton(getString(R.string.edit_repeat_month_mode_fixed_day), MonthRepeatMode.FIXED_DAY), dp(6));
            addCompactButton(modeButtons, createMonthModeButton(getString(R.string.edit_repeat_month_mode_last_day), MonthRepeatMode.LAST_DAY), dp(6));
            contentContainer.addView(modeButtons, fullWidthWithBottomMargin(dp(8)));

            if (monthMode == MonthRepeatMode.FIXED_DAY) {
                monthlyDayInput = createNumberInput(monthlyDayValue, "1..31");
                monthlyDayInput.addTextChangedListener(simpleWatcher(() -> {
                    monthlyDayValue = valueOf(monthlyDayInput);
                    pushValue();
                }));
                contentContainer.addView(
                        createInputBlock(getString(R.string.edit_repeat_month_day), monthlyDayInput),
                        fullWidth()
                    );
            }
        }

        private Button createMonthModeButton(String label, MonthRepeatMode targetMode) {
            Button button = createSegmentButton(label, monthMode == targetMode);
            button.setOnClickListener(view -> {
                monthMode = targetMode;
                if (monthMode == MonthRepeatMode.FIXED_DAY
                        && (monthlyDayValue == null || monthlyDayValue.trim().isEmpty())) {
                    monthlyDayValue = "1";
                }
                pushValue();
                rebuildContent();
            });
            return button;
        }

        private void buildCustomContent() {
            customInput = createInput(customValue);
            customInput.setHint("1w @days(mon,wed,fri)");
            customInput.addTextChangedListener(simpleWatcher(() -> {
                customValue = valueOf(customInput);
                pushValue();
            }));
            contentContainer.addView(
                    createInputBlock(getString(R.string.edit_repeat_custom_rule), customInput),
                    fullWidth()
            );
        }

        private void pushValue() {
            String nextValue = buildValue();
            String current = valueOf(backingInput);
            if (!nextValue.equals(current)) {
                backingInput.setText(nextValue);
            } else if (onChanged != null) {
                onChanged.run();
            }
        }

        private String buildValue() {
            switch (mode) {
                case NONE:
                    return "";
                case INTERVAL:
                    return intervalAmountValue.isEmpty() ? "" : intervalAmountValue + intervalUnit.getToken();
                case WEEKLY:
                    if (weeklyIntervalValue.isEmpty()) {
                        return "";
                    }
                    return weeklyIntervalValue + "w @days(" + formatSelectedDays() + ")";
                case MONTHLY:
                    if (monthlyIntervalValue.isEmpty()) {
                        return "";
                    }
                    if (monthMode == MonthRepeatMode.SAME_DAY) {
                        return monthlyIntervalValue + "mo";
                    }
                    if (monthMode == MonthRepeatMode.LAST_DAY) {
                        return monthlyIntervalValue + "mo @monthday(last)";
                    }
                    return monthlyIntervalValue + "mo @monthday(" + monthlyDayValue + ")";
                case CUSTOM:
                    customValue = valueOf(customInput);
                    return customValue;
                default:
                    return "";
            }
        }

        private String formatSelectedDays() {
            List<String> tokens = new ArrayList<>();
            for (DayOfWeek dayOfWeek : DayOfWeek.values()) {
                if (weeklyDays.contains(dayOfWeek)) {
                    tokens.add(dayToken(dayOfWeek));
                }
            }
            return TextUtils.join(",", tokens);
        }
    }

    private void showDatePicker() {
        LocalDate initial;
        try {
            initial = LocalDate.parse(valueOf(dateInput), DATE);
        } catch (RuntimeException exception) {
            initial = LocalDate.now();
        }
        DatePickerDialog dialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    dateInput.setText(DATE.format(LocalDate.of(year, month + 1, dayOfMonth)));
                    updatePreview();
                },
                initial.getYear(),
                initial.getMonthValue() - 1,
                initial.getDayOfMonth()
        );
        dialog.show();
        tintDialogButtons(dialog);
    }

    private void showTimePicker() {
        LocalTime initial;
        try {
            initial = LocalTime.parse(valueOf(timeInput), TIME);
        } catch (RuntimeException exception) {
            initial = LocalTime.now();
        }
        TimePickerDialog dialog = new TimePickerDialog(
                this,
                (view, hourOfDay, minute) -> {
                    timeInput.setText(TIME.format(LocalTime.of(hourOfDay, minute)));
                    updatePreview();
                },
                initial.getHour(),
                initial.getMinute(),
                true
        );
        dialog.show();
        tintDialogButtons(dialog);
    }

    private void renderSubtasks() {
        if (subtaskList == null || subtaskSummaryText == null) {
            return;
        }
        subtaskList.removeAllViews();
        subtaskSummaryText.setText(formatSubtaskSummary());

        if (subtaskDrafts.isEmpty()) {
            LinearLayout empty = new LinearLayout(this);
            empty.setOrientation(LinearLayout.VERTICAL);
            empty.setGravity(Gravity.CENTER_HORIZONTAL);
            empty.setPadding(dp(8), dp(10), dp(8), dp(4));

            TextView title = createText(getString(R.string.task_edit_subtasks_empty_title), 13, R.color.text_primary, true);
            title.setGravity(Gravity.CENTER_HORIZONTAL);
            empty.addView(title, fullWidth());

            TextView body = createText(getString(R.string.task_edit_subtasks_empty_body), 12, R.color.text_secondary, false);
            body.setGravity(Gravity.CENTER_HORIZONTAL);
            empty.addView(body, fullWidthWithTopMargin(dp(3)));
            subtaskList.addView(empty, fullWidth());
            return;
        }

        for (int i = 0; i < subtaskDrafts.size(); i++) {
            subtaskList.addView(createSubtaskRow(i), fullWidthWithBottomMargin(dp(5)));
        }
    }

    private String formatSubtaskSummary() {
        if (subtaskDrafts.isEmpty()) {
            return getString(R.string.task_edit_subtasks_empty_summary);
        }
        int completed = 0;
        for (SubtaskDraft draft : subtaskDrafts) {
            if (draft.completed) {
                completed++;
            }
        }
        return getString(R.string.task_edit_subtasks_summary, completed, subtaskDrafts.size());
    }

    private LinearLayout createSubtaskRow(int index) {
        SubtaskDraft draft = subtaskDrafts.get(index);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(7), dp(8), dp(7));
        row.setBackground(createRoundedBackground(
                getColor(R.color.background),
                getColor(R.color.chip_stroke),
                8
        ));
        row.setClickable(true);
        row.setOnClickListener(view -> showSubtaskDialog(index));

        String statusSymbol = draft.completed ? "\u2713" : draft.skipped ? "\u2715" : "";
        int statusTextColor = draft.completed
                ? R.color.status_completed_text
                : draft.skipped ? R.color.status_skipped_text : R.color.text_secondary;
        int statusBackground = draft.completed
                ? R.color.status_completed_background
                : draft.skipped ? R.color.status_skipped_background : android.R.color.transparent;
        TextView status = createText(statusSymbol, 13, statusTextColor, true);
        status.setGravity(Gravity.CENTER);
        status.setBackground(createCircleOutlineBackground(
                getColor(statusBackground),
                getColor(statusTextColor)
        ));
        status.setOnClickListener(view -> {
            if (draft.completed || draft.skipped) {
                draft.completed = false;
                draft.skipped = false;
            } else {
                draft.completed = true;
                draft.skipped = false;
            }
            renderSubtasks();
            updatePreview();
        });
        row.addView(status, new LinearLayout.LayoutParams(dp(26), dp(26)));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView title = createText(
                draft.title.isEmpty() ? getString(R.string.task_edit_subtask_default_title) : draft.title,
                13,
                R.color.text_primary,
                true
        );
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(title, fullWidth());
        String meta = draft.meta(this);
        if (!meta.isEmpty()) {
            TextView metaView = createText(meta, 11, R.color.text_secondary, false);
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

        TextView chevron = createText("\u203a", 18, R.color.text_secondary, true);
        chevron.setGravity(Gravity.CENTER);
        row.addView(chevron, new LinearLayout.LayoutParams(dp(18), dp(18)));

        ImageButton delete = createPlainIconButton(
                R.drawable.ic_delete,
                getString(R.string.task_edit_delete_subtask)
        );
        delete.setOnClickListener(view -> {
            subtaskDrafts.remove(index);
            renderSubtasks();
            updatePreview();
        });
        row.addView(delete, new LinearLayout.LayoutParams(dp(34), dp(34)));
        return row;
    }

    private void showSubtaskDialog(int index) {
        if (activeSubtaskEditor != null) {
            activeSubtaskEditor.dismiss(false);
        }
        activeSubtaskEditor = new SubtaskEditorSheet(index);
        activeSubtaskEditor.show();
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
                .setTitle(R.string.task_edit_confirm_delete_title)
                .setMessage(R.string.task_edit_confirm_delete_message)
                .setPositiveButton(R.string.common_delete, (dialog, which) ->
                        handleWriteResult(NoteStore.deleteTaskBlock(this, taskKey)))
                .setNegativeButton(R.string.common_cancel, null)
                .show();
    }

    private void handleWriteResult(TaskEditResult result) {
        if (result.isUpdated()) {
            NoteChangeMonitor.syncNow(this, true);
            Toast.makeText(this, getString(R.string.task_edit_saved), Toast.LENGTH_SHORT).show();
            setResult(RESULT_OK);
            finish();
            return;
        }

        String message = result.getMessage() == null ? getString(R.string.task_edit_save_failed) : result.getMessage();
        setStatusMessage(message);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void requestClose() {
        if (!isDirty()) {
            finish();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.task_edit_discard_title)
                .setMessage(R.string.task_edit_discard_message)
                .setPositiveButton(R.string.common_close, (dialog, which) -> finish())
                .setNeutralButton(R.string.common_save, (dialog, which) -> saveTask())
                .setNegativeButton(R.string.common_stay, null)
                .show();
    }

    private boolean isDirty() {
        return !currentMarkdownBlock().equals(initialMarkdownBlock);
    }

    private boolean validateCandidate(String candidate, boolean showSuccess) {
        String error = validationError(candidate);
        if (!error.isEmpty()) {
            setStatusMessage(error);
            return false;
        }
        setStatusMessage(showSuccess ? getString(R.string.task_edit_valid_format) : "");
        return true;
    }

    private String validationError(String candidate) {
        if (loadError != null && task == null && defaultDocument == null) {
            return getString(R.string.task_edit_source_unavailable, loadError);
        }
        if (candidate == null || candidate.trim().isEmpty()) {
            return getString(R.string.task_edit_empty_markdown_block);
        }

        TaskEditorInputValidator.FieldIssue fieldIssue = TaskEditorInputValidator.firstFieldIssue(
                LocalDate.now(),
                dueValue(),
                valueOf(repeatInput),
                repeatUntilDoneValue(),
                repeatUntilDoneInput != null && repeatUntilDoneInput.isChecked(),
                overdueGraceValue(),
                overdueGraceEnabledInput != null && overdueGraceEnabledInput.isChecked(),
                snoozeValue()
        );
        if (fieldIssue != null) {
            return messageForFieldIssue(fieldIssue);
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
            return getString(R.string.task_edit_not_a_task_line);
        }
        if (result.getTasks().get(0).getReminderAt() == null) {
            return getString(R.string.task_edit_due_required);
        }
        return "";
    }

    private String messageForFieldIssue(TaskEditorInputValidator.FieldIssue issue) {
        if (issue == null) {
            return "";
        }
        switch (issue) {
            case INVALID_DUE:
                return getString(R.string.task_edit_validation_due_invalid);
            case INVALID_REPEAT:
                return getString(R.string.task_edit_validation_repeat_invalid);
            case INVALID_REPEAT_UNTIL_DONE:
                return getString(R.string.task_edit_validation_repeat_until_done_invalid);
            case INVALID_GRACE:
                return getString(R.string.task_edit_validation_grace_invalid);
            case INVALID_SNOOZE:
                return getString(R.string.task_edit_validation_snooze_invalid);
            default:
                return "";
        }
    }

    private String currentMarkdownBlock() {
        StringBuilder builder = new StringBuilder(currentParentMarkdownLine());
        for (SubtaskDraft draft : subtaskDrafts) {
            String line = draft.toMarkdownLine(this);
            if (!line.isEmpty()) {
                builder.append('\n').append("  ").append(line);
            }
        }
        return builder.toString().trim();
    }

    private String currentParentMarkdownLine() {
        String title = valueOf(titleInput);
        if (title.isEmpty()) {
            title = getString(R.string.task_edit_title_new);
        }

        StringBuilder builder = new StringBuilder();
        if (checkboxTaskInput == null || checkboxTaskInput.isChecked()) {
            boolean completed = task != null && task.isCompleted();
            builder.append(completed ? "- [x] " : "- [ ] ");
        }
        builder.append(title);

        appendDue(builder, dueValue());
        appendRepeat(builder, valueOf(repeatInput));
        appendRepeatUntilDone(builder, repeatUntilDoneValue());
        appendOverdueGrace(builder, overdueGraceValue());
        appendSnooze(builder, snoozeValue(), shouldPersistSnooze(snoozeValue()));
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
            builder.append(" @")
                    .append(TaskFormatSettings.load(this).getDueKeyword())
                    .append("(")
                    .append(dueValue.trim())
                    .append(")");
        }
    }

    private void appendRepeat(StringBuilder builder, String repeat) {
        if (repeat != null && !repeat.trim().isEmpty()) {
            String value = repeat.trim();
            String keyword = TaskSyntaxPreferences.useCompactSyntax(this)
                    ? "r"
                    : TaskFormatSettings.load(this).getRepeatKeyword();
            int selectorIndex = value.indexOf(" @");
            if (selectorIndex < 0) {
                selectorIndex = value.indexOf("@");
            }
            if (selectorIndex > 0) {
                builder.append(" @")
                        .append(keyword)
                        .append("(")
                        .append(value.substring(0, selectorIndex).trim())
                        .append(") ")
                        .append(value.substring(selectorIndex).trim());
                return;
            }
            builder.append(" @")
                    .append(keyword)
                    .append("(")
                    .append(value)
                    .append(")");
        }
    }

    private void appendRepeatUntilDone(StringBuilder builder, String repeatUntilDone) {
        if (repeatUntilDone != null && !repeatUntilDone.trim().isEmpty()) {
            builder.append(" @")
                    .append(TaskSyntaxPreferences.useCompactSyntax(this)
                            ? "rud"
                            : TaskFormatSettings.load(this).getRepeatUntilDoneKeyword())
                    .append("(")
                    .append(repeatUntilDone.trim())
                    .append(")");
        }
    }

    private void appendPriority(StringBuilder builder, TaskPriority priority) {
        String token = priorityToToken(priority);
        if (!token.isEmpty()) {
            builder.append(" @")
                    .append(TaskFormatSettings.load(this).getPriorityKeyword())
                    .append("(")
                    .append(token)
                    .append(")");
        }
    }

    private void appendGroup(StringBuilder builder, String group) {
        if (group != null && !group.trim().isEmpty()
                && !ObsidianTask.DEFAULT_GROUP.equals(group.trim())) {
            builder.append(" @")
                    .append(TaskFormatSettings.load(this).getGroupKeyword())
                    .append("(")
                    .append(group.trim())
                    .append(")");
        }
    }

    private void appendTags(StringBuilder builder, String tags) {
        if (tags != null && !tags.trim().isEmpty()) {
            if (TaskSyntaxPreferences.useHashTags(this)) {
                for (String tag : tags.trim().split("[,;\\s]+")) {
                    String cleanTag = tag.trim().replaceFirst("^#+", "");
                    if (!cleanTag.isEmpty()) {
                        builder.append(" #").append(cleanTag);
                    }
                }
                return;
            }
            builder.append(" @")
                    .append(TaskFormatSettings.load(this).getTagKeyword())
                    .append("(")
                    .append(tags.trim())
                    .append(")");
        }
    }

    private void appendOverdueGrace(StringBuilder builder, String graceValue) {
        if (graceValue != null && !graceValue.trim().isEmpty()) {
            builder.append(" @")
                    .append(TaskSyntaxPreferences.useCompactSyntax(this) ? "g" : "grace")
                    .append("(")
                    .append(graceValue.trim())
                    .append(")");
        }
    }

    private void appendSnooze(StringBuilder builder, String snoozeValue, boolean persist) {
        if (persist && snoozeValue != null && !snoozeValue.trim().isEmpty()) {
            builder.append(" @snooze(").append(snoozeValue.trim()).append(")");
        }
    }

    private void updatePreview() {
        String candidate = currentMarkdownBlock();
        if (previewText != null) {
            previewText.setText(candidate);
            previewText.setVisibility(previewExpanded ? View.VISIBLE : View.GONE);
        }
        if (previewChevron != null) {
            previewChevron.setText(previewExpanded ? "\u2212" : "+");
        }

        String error = validationError(candidate);
        boolean valid = error.isEmpty();
        if (saveButton != null) {
            saveButton.setEnabled(valid);
            saveButton.setAlpha(valid ? 1f : 0.55f);
        }
        updateExtraSection();
        setStatusMessage(error);
    }

    private TextWatcher simpleWatcher(Runnable callback) {
        return new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
                if (callback != null) {
                    callback.run();
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        };
    }

    private TextWatcher previewWatcher() {
        return simpleWatcher(this::updatePreview);
    }

    private String sourceLabel() {
        if (taskMatch != null && task != null) {
            return compactName(taskMatch.getDisplayName());
        }
        if (defaultDocument != null) {
            return compactName(defaultDocument.getDisplayName())
                    + " \u00b7 "
                    + getString(R.string.task_edit_write_file_suffix);
        }
        return loadError == null ? compactName(NoteStore.sourceLabel(this)) : loadError;
    }

    private String formatErrors(List<TaskParseError> errors) {
        StringBuilder builder = new StringBuilder(getString(R.string.task_edit_format_error_title));
        int limit = Math.min(3, errors.size());
        for (int i = 0; i < limit; i++) {
            builder.append('\n').append(errors.get(i).format(this));
        }
        if (errors.size() > limit) {
            builder.append('\n').append(getString(R.string.task_edit_more_errors, errors.size() - limit));
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

    private String durationToTokenAllowZero(Duration duration) {
        if (duration == null) {
            return "";
        }
        long minutes = duration.toMinutes();
        if (minutes == 0L) {
            return "0m";
        }
        return durationToToken(duration);
    }

    private String extractFunctionValue(String body, List<String> keywords) {
        if (body == null || body.trim().isEmpty() || keywords == null) {
            return "";
        }
        for (String keyword : keywords) {
            if (keyword == null || keyword.trim().isEmpty()) {
                continue;
            }
            Pattern pattern = Pattern.compile("@" + Pattern.quote(keyword) + "\\(([^)]*)\\)", Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(body);
            if (matcher.find()) {
                return matcher.group(1).trim();
            }
        }
        return "";
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

    private void applyEditorFieldStyle(EditText input) {
        input.setSingleLine(true);
        input.setSelectAllOnFocus(false);
        input.setTextColor(getColor(R.color.text_primary));
        input.setHintTextColor(getColor(R.color.text_tertiary));
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setBackground(createRoundedBackground(
                getColor(R.color.background),
                getColor(R.color.card_stroke),
                8
        ));
        input.setPadding(dp(12), dp(10), dp(12), dp(10));
    }

    private EditText createInput(String value) {
        EditText input = new EditText(this);
        applyEditorFieldStyle(input);
        input.setText(value == null ? "" : value);
        return input;
    }

    private AutoCompleteTextView createGroupAutocomplete(String value, List<String> suggestions) {
        AutoCompleteTextView input = new AutoCompleteTextView(this);
        applyEditorFieldStyle(input);
        input.setText(value == null ? "" : value);
        List<String> safe = suggestions == null ? Collections.emptyList() : suggestions;
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_dropdown_item_1line,
                safe
        );
        input.setAdapter(adapter);
        input.setThreshold(1);
        return input;
    }

    /**
     * Quick-pick chips under the group field; complements {@link AutoCompleteTextView} suggestions.
     */
    private HorizontalScrollView createGroupSuggestionChipRow(List<String> groups) {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setFillViewport(false);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        if (groups == null || groups.isEmpty()) {
            scroll.setVisibility(View.GONE);
            scroll.addView(row, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));
            return scroll;
        }

        String current = valueOf(groupInput);
        int gap = dp(6);
        boolean first = true;
        for (String group : groups) {
            LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            if (!first) {
                chipParams.setMargins(gap, 0, 0, 0);
            }
            first = false;

            Button chip = createSegmentButton(group, current != null && current.equals(group));
            chip.setMaxLines(1);
            chip.setEllipsize(TextUtils.TruncateAt.END);
            chip.setMaxWidth(dp(220));
            chip.setPadding(dp(10), dp(6), dp(10), dp(6));
            chip.setOnClickListener(view -> {
                groupInput.setText(group);
                groupInput.setSelection(group.length());
                updatePreview();
            });
            row.addView(chip, chipParams);
        }

        scroll.addView(row, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        return scroll;
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
        card.setPadding(dp(12), dp(9), dp(12), dp(9));
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

    private ImageButton createPrimaryIconButton(int iconRes, String description) {
        ImageButton button = createPlainIconButton(iconRes, description);
        button.setColorFilter(getColor(R.color.primary_button_text));
        button.setBackground(createRoundedBackground(
                getColor(R.color.primary_button_background),
                0,
                10
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

    private void tintDialogButtons(AlertDialog dialog) {
        if (dialog == null) {
            return;
        }
        Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (positive != null) {
            positive.setTextColor(getColor(R.color.text_primary));
        }
        Button negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        if (negative != null) {
            negative.setTextColor(getColor(R.color.text_secondary));
        }
        Button neutral = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
        if (neutral != null) {
            neutral.setTextColor(getColor(R.color.text_secondary));
        }
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
            return getString(R.string.source_not_selected);
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

    private final class SubtaskEditorSheet {
        private final int index;
        private final SubtaskDraft workingCopy;
        private final String initialMarkdownLine;

        private boolean previewExpanded;
        private boolean extraExpanded;
        private TaskPriority priority;

        private FrameLayout overlay;
        private LinearLayout sheet;
        private TextView statusView;
        private TextView previewView;
        private TextView previewChevronView;
        private TextView extraChevronView;
        private TextView extraSummaryView;
        private LinearLayout extraContentView;
        private EditText titleView;
        private EditText dateView;
        private EditText timeView;
        private EditText repeatView;
        private EditText repeatUntilDoneIntervalView;
        private EditText overdueGraceView;
        private EditText snoozeView;
        private EditText tagsView;
        private CheckBox repeatUntilDoneView;
        private CheckBox overdueGraceEnabledView;
        private CheckBox completedView;
        private LinearLayout repeatUntilDoneIntervalRowView;
        private LinearLayout overdueGraceRowView;
        private ImageButton saveView;

        private SubtaskEditorSheet(int index) {
            this.index = index;
            this.workingCopy = index >= 0 ? new SubtaskDraft(subtaskDrafts.get(index)) : new SubtaskDraft();
            this.priority = taskPriorityFromToken(workingCopy.priority);
            this.initialMarkdownLine = workingCopy.toMarkdownLine(TaskEditActivity.this);
        }

        private void show() {
            if (rootContainer == null) {
                return;
            }
            overlay = new FrameLayout(TaskEditActivity.this);
            overlay.setClickable(true);
            overlay.setBackgroundColor(Color.TRANSPARENT);
            overlay.setOnClickListener(view -> requestClose());

            sheet = new LinearLayout(TaskEditActivity.this);
            sheet.setOrientation(LinearLayout.VERTICAL);
            sheet.setClickable(true);
            sheet.setBackground(createSheetBackground());
            sheet.setPadding(dp(12), dp(8), dp(12), dp(8));

            View handle = new View(TaskEditActivity.this);
            handle.setBackground(createRoundedBackground(getColor(R.color.card_stroke), 0, 99));
            LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(dp(44), dp(4));
            handleParams.gravity = Gravity.CENTER_HORIZONTAL;
            handleParams.setMargins(0, 0, 0, dp(4));
            sheet.addView(handle, handleParams);

            LinearLayout header = createHeader();
            sheet.addView(header, fullWidthWithBottomMargin(dp(4)));
            attachDismissGesture(handle, header);

            ScrollView scroll = new ScrollView(TaskEditActivity.this);
            LinearLayout content = new LinearLayout(TaskEditActivity.this);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setPadding(0, 0, 0, dp(16));

            statusView = createText("", 13, R.color.error_text, false);
            statusView.setVisibility(View.GONE);
            content.addView(statusView, fullWidth());
            content.addView(createBasicSection(), fullWidthWithBottomMargin(dp(8)));
            content.addView(createExtraSection(), fullWidthWithBottomMargin(dp(8)));
            content.addView(createPreviewSection(), fullWidthWithBottomMargin(dp(8)));

            scroll.setFillViewport(true);
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
            sheetParams.setMargins(0, dp(92), 0, 0);
            overlay.addView(sheet, sheetParams);
            rootContainer.addView(overlay, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));
            updatePreview();
        }

        private LinearLayout createHeader() {
            LinearLayout header = new LinearLayout(TaskEditActivity.this);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);

            ImageButton close = createIconButton(R.drawable.ic_close, getString(R.string.common_close));
            close.setOnClickListener(view -> requestClose());
            header.addView(close, new LinearLayout.LayoutParams(dp(42), dp(42)));

            LinearLayout texts = new LinearLayout(TaskEditActivity.this);
            texts.setOrientation(LinearLayout.VERTICAL);
            TextView title = createText(index >= 0
                    ? getString(R.string.task_edit_subtask_title_edit)
                    : getString(R.string.task_edit_subtask_title_new), 17, R.color.text_primary, true);
            title.setSingleLine(true);
            title.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(title, fullWidth());
            TextView subtitle = createText(getString(R.string.task_edit_subtask_subtitle), 12, R.color.text_secondary, false);
            subtitle.setSingleLine(true);
            texts.addView(subtitle, fullWidthWithTopMargin(dp(1)));
            header.addView(texts, new LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1
            ));

            saveView = createPrimaryIconButton(R.drawable.ic_check, getString(R.string.common_save));
            saveView.setOnClickListener(view -> save());
            header.addView(saveView, new LinearLayout.LayoutParams(
                    dp(42),
                    dp(42)
            ));
            return header;
        }

        private LinearLayout createBasicSection() {
            LinearLayout card = createSectionCard(getString(R.string.task_edit_section_basic), null);
            titleView = createInput(workingCopy.title);
            titleView.setHint(getString(R.string.task_edit_title_hint));
            titleView.addTextChangedListener(localWatcher());
            card.addView(createInputBlock(getString(R.string.task_edit_field_title), titleView), fullWidthWithBottomMargin(dp(8)));

            dateView = createInput(workingCopy.date);
            dateView.setHint(getString(R.string.task_edit_hint_date_example));
            dateView.setOnClickListener(view -> showDatePicker());
            dateView.setOnFocusChangeListener((view, hasFocus) -> {
                if (hasFocus) {
                    showDatePicker();
                }
            });
            dateView.addTextChangedListener(localWatcher());

            timeView = createInput(workingCopy.time);
            timeView.setHint(getString(R.string.task_edit_hint_time_example));
            timeView.setOnClickListener(view -> showTimePicker());
            timeView.setOnFocusChangeListener((view, hasFocus) -> {
                if (hasFocus) {
                    showTimePicker();
                }
            });
            timeView.addTextChangedListener(localWatcher());

            LinearLayout row = new LinearLayout(TaskEditActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.addView(createInputBlock(getString(R.string.task_edit_field_date), dateView), new LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1
            ));
            LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1
            );
            timeParams.setMargins(dp(6), 0, 0, 0);
            row.addView(createInputBlock(getString(R.string.task_edit_field_time), timeView), timeParams);
            card.addView(row, fullWidthWithBottomMargin(dp(8)));

            repeatView = createInput(workingCopy.repeat);
            repeatView.addTextChangedListener(localWatcher());
            card.addView(
                    new RepeatEditorController(repeatView, this::updatePreview).createView(),
                    fullWidthWithBottomMargin(dp(8))
            );

            repeatUntilDoneView = new CheckBox(TaskEditActivity.this);
            repeatUntilDoneView.setText(getString(R.string.task_edit_repeat_until_done_toggle));
            repeatUntilDoneView.setTextColor(getColor(R.color.text_secondary));
            repeatUntilDoneView.setChecked(workingCopy.repeatUntilDone);
            repeatUntilDoneView.setOnCheckedChangeListener((buttonView, isChecked) -> {
                updateRepeatUntilDoneSection();
                updatePreview();
            });
            card.addView(repeatUntilDoneView, fullWidthWithBottomMargin(dp(6)));

            repeatUntilDoneIntervalView = createInput(workingCopy.repeatUntilDoneValue);
            repeatUntilDoneIntervalView.setHint(getString(R.string.task_edit_hint_repeat_until_done_example));
            repeatUntilDoneIntervalView.addTextChangedListener(localWatcher());
            repeatUntilDoneIntervalRowView = createInputBlock(
                    getString(R.string.task_edit_repeat_until_done_interval),
                    repeatUntilDoneIntervalView
            );
            card.addView(repeatUntilDoneIntervalRowView, fullWidthWithBottomMargin(dp(8)));

            overdueGraceEnabledView = new CheckBox(TaskEditActivity.this);
            overdueGraceEnabledView.setText(getString(R.string.task_edit_overdue_grace_toggle));
            overdueGraceEnabledView.setTextColor(getColor(R.color.text_secondary));
            overdueGraceEnabledView.setChecked(!workingCopy.overdueGrace.isEmpty());
            overdueGraceEnabledView.setOnCheckedChangeListener((buttonView, isChecked) -> {
                updateOverdueGraceSection();
                updatePreview();
            });
            card.addView(overdueGraceEnabledView, fullWidthWithBottomMargin(dp(6)));

            overdueGraceView = createInput(workingCopy.overdueGrace);
            overdueGraceView.setHint(getString(R.string.task_edit_hint_grace_example));
            overdueGraceView.addTextChangedListener(localWatcher());
            overdueGraceRowView = createInputBlock(
                    getString(R.string.task_edit_overdue_grace_label),
                    overdueGraceView
            );
            card.addView(overdueGraceRowView, fullWidth());
            updateRepeatUntilDoneSection();
            updateOverdueGraceSection();
            return card;
        }

        private LinearLayout createExtraSection() {
            LinearLayout card = createCardContainer();
            LinearLayout header = new LinearLayout(TaskEditActivity.this);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);
            header.setClickable(true);
            header.setOnClickListener(view -> {
                extraExpanded = !extraExpanded;
                updateExtraSection();
            });

            LinearLayout texts = new LinearLayout(TaskEditActivity.this);
            texts.setOrientation(LinearLayout.VERTICAL);
            texts.addView(createText(getString(R.string.task_edit_section_extra), 15, R.color.text_primary, true), fullWidth());
            extraSummaryView = createText("", 12, R.color.text_secondary, false);
            texts.addView(extraSummaryView, fullWidthWithTopMargin(dp(2)));
            header.addView(texts, new LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1
            ));

            extraChevronView = createText("", 18, R.color.text_secondary, true);
            extraChevronView.setGravity(Gravity.CENTER);
            header.addView(extraChevronView, new LinearLayout.LayoutParams(dp(28), dp(28)));
            card.addView(header, fullWidthWithBottomMargin(dp(8)));

            extraContentView = new LinearLayout(TaskEditActivity.this);
            extraContentView.setOrientation(LinearLayout.VERTICAL);
            extraContentView.addView(createLabel(getString(R.string.task_edit_field_priority)), fullWidthWithBottomMargin(dp(4)));
            LinearLayout priorityRow = new LinearLayout(TaskEditActivity.this);
            priorityRow.setOrientation(LinearLayout.HORIZONTAL);
            rebuildPriorityRow(priorityRow);
            extraContentView.addView(priorityRow, fullWidthWithBottomMargin(dp(8)));

            tagsView = createInput(workingCopy.tags);
            tagsView.setHint(getString(R.string.task_edit_hint_tags_example));
            tagsView.addTextChangedListener(localWatcher());
            extraContentView.addView(createInputBlock(getString(R.string.task_edit_field_tags), tagsView),
                    fullWidthWithBottomMargin(dp(8)));

            snoozeView = createInput(workingCopy.snooze.isEmpty() ? defaultSnoozeToken() : workingCopy.snooze);
            snoozeView.setHint(defaultSnoozeToken());
            snoozeView.addTextChangedListener(localWatcher());
            extraContentView.addView(createInputBlock(
                    getString(R.string.task_edit_field_snooze),
                    snoozeView
            ), fullWidthWithBottomMargin(dp(8)));

            completedView = new CheckBox(TaskEditActivity.this);
            completedView.setText(getString(R.string.task_edit_subtask_completed));
            completedView.setTextColor(getColor(R.color.text_secondary));
            completedView.setChecked(workingCopy.completed);
            completedView.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    workingCopy.skipped = false;
                }
                updatePreview();
            });
            extraContentView.addView(completedView, fullWidth());

            card.addView(extraContentView, fullWidth());
            updateExtraSection();
            return card;
        }

        private LinearLayout createPreviewSection() {
            LinearLayout card = createCardContainer();
            card.setOnClickListener(view -> {
                previewExpanded = !previewExpanded;
                updatePreview();
            });

            LinearLayout row = new LinearLayout(TaskEditActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(createText(getString(R.string.task_edit_section_preview), 14, R.color.text_primary, true), new LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1
            ));
            previewChevronView = createText("", 18, R.color.text_secondary, true);
            previewChevronView.setGravity(Gravity.CENTER);
            row.addView(previewChevronView, new LinearLayout.LayoutParams(dp(28), dp(28)));
            card.addView(row, fullWidth());

            previewView = createText("", 13, R.color.text_primary, false);
            previewView.setTypeface(Typeface.MONOSPACE);
            previewView.setPadding(0, dp(8), 0, 0);
            card.addView(previewView, fullWidth());
            return card;
        }

        private void addQuickIntervalButton(LinearLayout row, String value, int leftMargin) {
            Button button = createSegmentButton(value, value.equals(localValue(repeatView)));
            button.setOnClickListener(view -> {
                repeatView.setText(value);
                updatePreview();
            });
            addCompactButton(row, button, leftMargin);
        }

        private void rebuildPriorityRow(LinearLayout row) {
            row.removeAllViews();
            addPriorityButton(row, getString(R.string.task_edit_priority_none), TaskPriority.NONE, 0);
            addPriorityButton(row, getString(R.string.task_edit_priority_low), TaskPriority.LOW, dp(6));
            addPriorityButton(row, getString(R.string.task_edit_priority_medium), TaskPriority.MEDIUM, dp(6));
            addPriorityButton(row, getString(R.string.task_edit_priority_high), TaskPriority.HIGH, dp(6));
        }

        private void addPriorityButton(LinearLayout row, String label, TaskPriority value, int leftMargin) {
            Button button = createSegmentButton(label, priority == value);
            button.setOnClickListener(view -> {
                priority = value == null ? TaskPriority.NONE : value;
                rebuildPriorityRow(row);
                updatePreview();
            });
            addCompactButton(row, button, leftMargin);
        }

        private void updateRepeatUntilDoneSection() {
            if (repeatUntilDoneIntervalRowView == null || repeatUntilDoneView == null) {
                return;
            }
            boolean visible = repeatUntilDoneView.isChecked();
            repeatUntilDoneIntervalRowView.setVisibility(visible ? View.VISIBLE : View.GONE);
            if (visible && localValue(repeatUntilDoneIntervalView).isEmpty()) {
                repeatUntilDoneIntervalView.setText(defaultRepeatUntilDoneToken());
            }
        }

        private void updateOverdueGraceSection() {
            if (overdueGraceRowView == null || overdueGraceEnabledView == null) {
                return;
            }
            boolean visible = overdueGraceEnabledView.isChecked();
            overdueGraceRowView.setVisibility(visible ? View.VISIBLE : View.GONE);
            if (visible && localValue(overdueGraceView).isEmpty()) {
                overdueGraceView.setText(defaultOverdueGraceToken());
            }
        }

        private TextWatcher localWatcher() {
            return new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    updatePreview();
                }

                @Override
                public void afterTextChanged(Editable s) {
                }
            };
        }

        private void showDatePicker() {
            LocalDate initial;
            try {
                initial = LocalDate.parse(localValue(dateView), DATE);
            } catch (RuntimeException exception) {
                initial = LocalDate.now();
            }
            DatePickerDialog dialog = new DatePickerDialog(
                    TaskEditActivity.this,
                    (view, year, month, dayOfMonth) -> {
                        dateView.setText(DATE.format(LocalDate.of(year, month + 1, dayOfMonth)));
                        updatePreview();
                    },
                    initial.getYear(),
                    initial.getMonthValue() - 1,
                    initial.getDayOfMonth()
            );
            dialog.show();
            tintDialogButtons(dialog);
        }

        private void showTimePicker() {
            LocalTime initial;
            try {
                initial = LocalTime.parse(localValue(timeView), TIME);
            } catch (RuntimeException exception) {
                initial = LocalTime.now();
            }
            TimePickerDialog dialog = new TimePickerDialog(
                    TaskEditActivity.this,
                    (view, hourOfDay, minute) -> {
                        timeView.setText(TIME.format(LocalTime.of(hourOfDay, minute)));
                        updatePreview();
                    },
                    initial.getHour(),
                    initial.getMinute(),
                    true
            );
            dialog.show();
            tintDialogButtons(dialog);
        }

        private void attachDismissGesture(View... dragTargets) {
            int touchSlop = ViewConfiguration.get(TaskEditActivity.this).getScaledTouchSlop();
            View.OnTouchListener listener = new View.OnTouchListener() {
                private float downX;
                private float downY;
                private boolean dragging;

                @Override
                public boolean onTouch(View view, MotionEvent event) {
                    if (sheet == null) {
                        return false;
                    }
                    switch (event.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            downX = event.getRawX();
                            downY = event.getRawY();
                            dragging = false;
                            return true;
                        case MotionEvent.ACTION_MOVE:
                            float dx = event.getRawX() - downX;
                            float dy = event.getRawY() - downY;
                            if (!dragging) {
                                if (dy <= touchSlop || dy <= Math.abs(dx)) {
                                    return true;
                                }
                                dragging = true;
                            }
                            sheet.setTranslationY(Math.max(0f, dy));
                            return true;
                        case MotionEvent.ACTION_CANCEL:
                            if (dragging) {
                                animateBack();
                                return true;
                            }
                            return false;
                        case MotionEvent.ACTION_UP:
                            if (!dragging) {
                                return false;
                            }
                            if (sheet.getTranslationY() > dp(120)) {
                                if (isDirty()) {
                                    animateBack();
                                    requestClose();
                                } else {
                                    dismiss(true);
                                }
                            } else {
                                animateBack();
                            }
                            return true;
                        default:
                            return false;
                    }
                }
            };
            for (View dragTarget : dragTargets) {
                if (dragTarget != null) {
                    dragTarget.setOnTouchListener(listener);
                }
            }
        }

        private void animateBack() {
            if (sheet == null) {
                return;
            }
            sheet.animate().translationY(0f).setDuration(180L).start();
        }

        private String localValue(EditText input) {
            return input == null ? "" : input.getText().toString().trim();
        }

        private boolean isDirty() {
            return !currentMarkdownLine().equals(initialMarkdownLine);
        }

        private void requestClose() {
            if (!isDirty()) {
                dismiss(false);
                return;
            }
            new AlertDialog.Builder(TaskEditActivity.this)
                    .setTitle(R.string.task_edit_discard_title)
                    .setMessage(R.string.task_edit_subtask_discard_message)
                    .setPositiveButton(R.string.common_close, (dialog, which) -> dismiss(false))
                    .setNeutralButton(R.string.common_save, (dialog, which) -> save())
                    .setNegativeButton(R.string.common_stay, null)
                    .show();
        }

        private void dismiss(boolean animated) {
            if (overlay == null) {
                activeSubtaskEditor = null;
                return;
            }
            Runnable removeAction = () -> {
                if (rootContainer != null) {
                    rootContainer.removeView(overlay);
                }
                if (activeSubtaskEditor == this) {
                    activeSubtaskEditor = null;
                }
            };
            if (!animated || sheet == null) {
                removeAction.run();
                return;
            }
            float target = Math.max(rootContainer == null ? 0 : rootContainer.getHeight(), dp(640));
            sheet.animate()
                    .translationY(target)
                    .setDuration(180L)
                    .withEndAction(removeAction)
                    .start();
        }

        private void save() {
            String candidate = currentMarkdownLine();
            String error = validationError(candidate);
            if (!error.isEmpty()) {
                setStatus(error);
                return;
            }

            workingCopy.title = localValue(titleView);
            workingCopy.date = localValue(dateView);
            workingCopy.time = localValue(timeView);
            workingCopy.repeat = localValue(repeatView);
            workingCopy.repeatUntilDone = repeatUntilDoneView != null && repeatUntilDoneView.isChecked();
            workingCopy.repeatUntilDoneValue = workingCopy.repeatUntilDone
                    ? localValue(repeatUntilDoneIntervalView)
                    : "";
            workingCopy.overdueGrace = overdueGraceEnabledView != null && overdueGraceEnabledView.isChecked()
                    ? localValue(overdueGraceView)
                    : "";
            workingCopy.snooze = localValue(snoozeView);
            workingCopy.defaultSnooze = defaultSnoozeToken();
            workingCopy.completed = completedView != null && completedView.isChecked();
            if (workingCopy.completed) {
                workingCopy.skipped = false;
            }
            workingCopy.priority = priorityToToken(priority);
            workingCopy.tags = localValue(tagsView);

            if (index >= 0) {
                subtaskDrafts.set(index, workingCopy);
            } else {
                subtaskDrafts.add(workingCopy);
            }
            renderSubtasks();
            updatePreview();
            dismiss(false);
        }

        private String localDueCombined() {
            String date = localValue(dateView);
            String time = localValue(timeView);
            if (!date.isEmpty() && !time.isEmpty()) {
                return date + " " + time;
            }
            if (!date.isEmpty()) {
                return date;
            }
            return time;
        }

        private String validationError(String candidate) {
            if (candidate == null || candidate.trim().isEmpty()) {
                return getString(R.string.task_edit_subtask_empty);
            }
            TaskEditorInputValidator.FieldIssue fieldIssue = TaskEditorInputValidator.firstFieldIssue(
                    LocalDate.now(),
                    localDueCombined(),
                    localValue(repeatView),
                    repeatUntilDoneView != null && repeatUntilDoneView.isChecked()
                            ? localValue(repeatUntilDoneIntervalView)
                            : "",
                    repeatUntilDoneView != null && repeatUntilDoneView.isChecked(),
                    overdueGraceEnabledView != null && overdueGraceEnabledView.isChecked()
                            ? localValue(overdueGraceView)
                            : "",
                    overdueGraceEnabledView != null && overdueGraceEnabledView.isChecked(),
                    localValue(snoozeView)
            );
            if (fieldIssue != null) {
                return TaskEditActivity.this.messageForFieldIssue(fieldIssue);
            }
            TaskParseResult result = TaskParser.parseDocument(
                    candidate + "\n",
                    LocalDate.now(),
                    "subtask.md",
                    TaskFormatSettings.load(TaskEditActivity.this)
            );
            if (!result.getErrors().isEmpty()) {
                return formatErrors(result.getErrors());
            }
            if (result.getTasks().isEmpty()) {
                return getString(R.string.task_edit_subtask_not_recognized);
            }
            return "";
        }

        private String currentMarkdownLine() {
            SubtaskDraft draft = new SubtaskDraft(workingCopy);
            draft.title = localValue(titleView);
            draft.date = localValue(dateView);
            draft.time = localValue(timeView);
            draft.repeat = localValue(repeatView);
            draft.repeatUntilDone = repeatUntilDoneView != null && repeatUntilDoneView.isChecked();
            draft.repeatUntilDoneValue = draft.repeatUntilDone
                    ? localValue(repeatUntilDoneIntervalView)
                    : "";
            draft.overdueGrace = overdueGraceEnabledView != null && overdueGraceEnabledView.isChecked()
                    ? localValue(overdueGraceView)
                    : "";
            draft.snooze = localValue(snoozeView);
            draft.defaultSnooze = defaultSnoozeToken();
            draft.completed = completedView != null && completedView.isChecked();
            if (draft.completed) {
                draft.skipped = false;
            }
            draft.priority = priorityToToken(priority);
            draft.tags = localValue(tagsView);
            return draft.toMarkdownLine(TaskEditActivity.this);
        }

        private void updatePreview() {
            String candidate = currentMarkdownLine();
            if (previewView != null) {
                previewView.setText(candidate);
                previewView.setVisibility(previewExpanded ? View.VISIBLE : View.GONE);
            }
            if (previewChevronView != null) {
                previewChevronView.setText(previewExpanded ? "\u2212" : "+");
            }
            updateExtraSection();

            String error = validationError(candidate);
            boolean valid = error.isEmpty();
            if (saveView != null) {
                saveView.setEnabled(valid);
                saveView.setAlpha(valid ? 1f : 0.55f);
            }
            setStatus(error);
        }

        private void updateExtraSection() {
            if (extraContentView == null || extraChevronView == null || extraSummaryView == null) {
                return;
            }
            extraContentView.setVisibility(extraExpanded ? View.VISIBLE : View.GONE);
            extraChevronView.setText(extraExpanded ? "\u2212" : "+");
            List<String> parts = new ArrayList<>();
            if (priority != null && priority != TaskPriority.NONE) {
                parts.add(priorityLabel(priority));
            }
            String tags = localValue(tagsView);
            if (!tags.isEmpty()) {
                parts.add(tags);
            }
            if (completedView != null && completedView.isChecked()) {
                parts.add(getString(R.string.task_edit_subtask_completed));
            }
            String summary = parts.isEmpty() ? "" : TextUtils.join(" \u00b7 ", parts);
            extraSummaryView.setText(summary);
            extraSummaryView.setVisibility(summary.isEmpty() ? View.GONE : View.VISIBLE);
        }

        private void setStatus(String message) {
            if (statusView == null) {
                return;
            }
            String safeMessage = message == null ? "" : message.trim();
            statusView.setText(safeMessage);
            statusView.setVisibility(safeMessage.isEmpty() ? View.GONE : View.VISIBLE);
        }

        private TaskPriority taskPriorityFromToken(String token) {
            if (token == null || token.trim().isEmpty()) {
                return TaskPriority.NONE;
            }
            return TaskPriority.fromName(token.trim());
        }
    }

    private static final class SubtaskDraft {
        private String title = "";
        private String date = "";
        private String time = "";
        private String repeat = "";
        private String repeatUntilDoneValue = "";
        private String overdueGrace = "";
        private String snooze = "";
        private String defaultSnooze = "";
        private String priority = "";
        private String tags = "";
        private boolean repeatUntilDone;
        private boolean completed;
        private boolean skipped;
        private boolean snoozeExplicit;

        private SubtaskDraft() {
        }

        private SubtaskDraft(SubtaskDraft source) {
            this.title = source.title;
            this.date = source.date;
            this.time = source.time;
            this.repeat = source.repeat;
            this.repeatUntilDoneValue = source.repeatUntilDoneValue;
            this.overdueGrace = source.overdueGrace;
            this.snooze = source.snooze;
            this.defaultSnooze = source.defaultSnooze;
            this.priority = source.priority;
            this.tags = source.tags;
            this.repeatUntilDone = source.repeatUntilDone;
            this.completed = source.completed;
            this.skipped = source.skipped;
            this.snoozeExplicit = source.snoozeExplicit;
        }

        private static SubtaskDraft fromTask(ObsidianTask task, TaskFormatSettings settings) {
            SubtaskDraft draft = new SubtaskDraft();
            draft.title = task.getTitle();
            String rawDue = extractFunctionValueStatic(task.getRawLine(), settings.dueKeywords());
            if (!rawDue.isEmpty()) {
                int split = rawDue.indexOf(' ');
                if (split > 0) {
                    draft.date = rawDue.substring(0, split).trim();
                    draft.time = rawDue.substring(split + 1).trim();
                } else if (rawDue.matches("\\d{4}-\\d{2}-\\d{2}")) {
                    draft.date = rawDue;
                } else if (rawDue.matches("\\d{2}:\\d{2}")) {
                    draft.time = rawDue;
                }
            } else if (task.getReminderAt() != null) {
                draft.date = DATE.format(task.getReminderAt().toLocalDate());
                draft.time = TIME.format(task.getReminderAt().toLocalTime());
            }
            if (task.getRepeatRule() != null) {
                draft.repeat = task.getRepeatRule().formatForUi();
            } else {
                draft.repeat = extractFunctionValueStatic(task.getRawLine(), settings.repeatKeywords());
            }
            draft.repeatUntilDoneValue = extractFunctionValueStatic(task.getRawLine(), settings.repeatUntilDoneKeywords());
            draft.repeatUntilDone = !draft.repeatUntilDoneValue.isEmpty() || task.getRepeatMode() == RepeatMode.UNTIL_DONE;
            if (draft.repeat.isEmpty() && task.getRepeatMode() == RepeatMode.ALWAYS) {
                draft.repeat = durationToTokenStatic(task.getRepeatInterval());
            }
            if (draft.repeatUntilDoneValue.isEmpty() && task.getRepeatMode() == RepeatMode.UNTIL_DONE) {
                draft.repeatUntilDoneValue = durationToTokenStatic(task.getRepeatInterval());
            }
            if (task.getOverdueGracePeriod() != null) {
                draft.overdueGrace = durationToTokenAllowZeroStatic(task.getOverdueGracePeriod());
            }
            draft.snooze = extractFunctionValueStatic(task.getRawLine(), Collections.singletonList("snooze"));
            draft.snoozeExplicit = !draft.snooze.isEmpty();
            draft.completed = task.isCompleted();
            draft.skipped = task.isSkipped();
            draft.priority = priorityToTokenStatic(task.getPriority());
            draft.tags = tagsToTextStatic(task.getTags());
            return draft;
        }

        private String toMarkdownLine(TaskEditActivity activity) {
            String normalizedTitle = title == null ? "" : title.trim();
            if (normalizedTitle.isEmpty()) {
                normalizedTitle = activity.getString(R.string.task_edit_subtask_default_title);
            }

            StringBuilder builder = new StringBuilder(completed ? "- [x] " : "- [ ] ");
            builder.append(normalizedTitle);

            String due = dueValue();
            if (!due.isEmpty()) {
                builder.append(" @due(").append(due).append(")");
            }
            if (repeat != null && !repeat.trim().isEmpty()) {
                String repeatValue = repeat.trim();
                String repeatKeyword = TaskSyntaxPreferences.useCompactSyntax(activity) ? "r" : "repeat";
                int selectorIndex = repeatValue.indexOf(" @");
                if (selectorIndex < 0) {
                    selectorIndex = repeatValue.indexOf("@");
                }
                if (selectorIndex > 0) {
                    builder.append(" @")
                            .append(repeatKeyword)
                            .append("(")
                            .append(repeatValue.substring(0, selectorIndex).trim())
                            .append(") ")
                            .append(repeatValue.substring(selectorIndex).trim());
                } else {
                    builder.append(" @").append(repeatKeyword).append("(").append(repeatValue).append(")");
                }
            }
            if (repeatUntilDone && repeatUntilDoneValue != null && !repeatUntilDoneValue.trim().isEmpty()) {
                builder.append(" @")
                        .append(TaskSyntaxPreferences.useCompactSyntax(activity) ? "rud" : "repeatUntilDone")
                        .append("(")
                        .append(repeatUntilDoneValue.trim())
                        .append(")");
            }
            if (overdueGrace != null && !overdueGrace.trim().isEmpty()) {
                builder.append(" @")
                        .append(TaskSyntaxPreferences.useCompactSyntax(activity) ? "g" : "grace")
                        .append("(")
                        .append(overdueGrace.trim())
                        .append(")");
            }
            if (snooze != null && !snooze.trim().isEmpty()
                    && (snoozeExplicit || defaultSnooze == null || !snooze.trim().equals(defaultSnooze.trim()))) {
                builder.append(" @snooze(").append(snooze.trim()).append(")");
            }
            if (priority != null && !priority.trim().isEmpty()) {
                builder.append(" @priority(").append(priority.trim()).append(")");
            }
            if (tags != null && !tags.trim().isEmpty()) {
                if (TaskSyntaxPreferences.useHashTags(activity)) {
                    String[] tokens = tags.trim().split("\\s+");
                    for (String token : tokens) {
                        if (token == null || token.trim().isEmpty()) {
                            continue;
                        }
                        String clean = token.trim();
                        builder.append(" ").append(clean.startsWith("#") ? clean : "#" + clean);
                    }
                } else {
                    builder.append(" @tag(").append(tags.trim()).append(")");
                }
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

        private String meta(TaskEditActivity activity) {
            List<String> parts = new ArrayList<>();
            String due = dueValue();
            if (!due.isEmpty()) {
                parts.add(due);
            }
            if (repeat != null && !repeat.trim().isEmpty()) {
                parts.add(activity.getString(R.string.task_edit_subtask_repeat_prefix, repeat.trim()));
            }
            if (completed) {
                parts.add(activity.getString(R.string.task_edit_subtask_completed));
            } else if (skipped) {
                parts.add(activity.getString(R.string.task_edit_subtask_skipped));
            }
            if (parts.isEmpty()) {
                return "";
            }

            StringBuilder builder = new StringBuilder();
            for (String part : parts) {
                if (builder.length() > 0) {
                    builder.append(" \u00b7 ");
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

        private static String durationToTokenAllowZeroStatic(Duration duration) {
            if (duration == null) {
                return "";
            }
            long minutes = duration.toMinutes();
            if (minutes == 0L) {
                return "0m";
            }
            return durationToTokenStatic(duration);
        }


        private static String extractFunctionValueStatic(String body, List<String> keywords) {
            if (body == null || body.trim().isEmpty() || keywords == null) {
                return "";
            }
            for (String keyword : keywords) {
                if (keyword == null || keyword.trim().isEmpty()) {
                    continue;
                }
                Pattern pattern = Pattern.compile("@" + Pattern.quote(keyword) + "\\(([^)]*)\\)", Pattern.CASE_INSENSITIVE);
                Matcher matcher = pattern.matcher(body);
                if (matcher.find()) {
                    return matcher.group(1).trim();
                }
            }
            return "";
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




