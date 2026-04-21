package com.regstar.obsidiannotification;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
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
    private static final String EXTRA_SECTION = "section";
    private static final String SECTION_BASIC = "basic";
    private static final String SECTION_SOURCES = "sources";
    private static final String SECTION_NOTIFICATIONS = "notifications";
    private static final String SECTION_FORMAT = "format";
    private static final String SECTION_SCAN = "scan";
    private static final String SECTION_ADVANCED = "advanced";

    private static final int REQUEST_REPLACE_NOTES = 3001;
    private static final int REQUEST_REPLACE_FOLDER = 3002;
    private static final int REQUEST_ADD_NOTES = 3003;
    private static final int REQUEST_ADD_FOLDER = 3004;
    private static final int REQUEST_SOURCE_MANAGEMENT = 3005;

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
    private EditText privateMarkerInput;
    private CheckBox recordSnoozeCountCheckbox;
    private CheckBox showSourceOnMainCheckbox;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemePreferences.apply(this);
        super.onCreate(savedInstanceState);
        buildUi();
        if (statusText != null) {
            updateStatus();
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_SOURCE_MANAGEMENT) {
            updateStatus();
            return;
        }

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

        String section = getIntent().getStringExtra(EXTRA_SECTION);
        if (section == null || section.trim().isEmpty()) {
            addHeader(root, "Настройки", false);
            addSettingsIndex(root);
        } else {
            addHeader(root, sectionTitle(section), true);
            addSettingsSection(root, section);
        }

        setContentView(scrollView);
    }

    private void addHeader(LinearLayout root, String titleText, boolean back) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(0, 0, 0, dp(12));

        if (back) {
            Button backButton = createSmallButton("‹");
            backButton.setTextSize(24);
            backButton.setOnClickListener(view -> finish());
            LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(dp(44), dp(42));
            backParams.setMargins(0, 0, dp(8), 0);
            row.addView(backButton, backParams);
        }

        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextSize(22);
        title.setTextColor(getColor(R.color.text_primary));
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        row.addView(title, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        ));

        root.addView(row, fullWidth());
    }

    private void addSettingsIndex(LinearLayout root) {
        root.addView(createActionCard(
                "Основные",
                "Открытие задач, главный экран, группировка и тема.",
                () -> openSection(SECTION_BASIC)
        ), fullWidthWithBottomMargin());
        root.addView(createActionCard(
                "Источники",
                "Файлы, папки и управление доступом через Android picker.",
                () -> openSection(SECTION_SOURCES)
        ), fullWidthWithBottomMargin());
        root.addView(createActionCard(
                "Уведомления",
                "Exact alarms, отложить, повторы и действия из уведомления.",
                () -> openSection(SECTION_NOTIFICATIONS)
        ), fullWidthWithBottomMargin());
        root.addView(createActionCard(
                "Формат задач",
                "Ключевые слова @due, @repeat, @tag, @priority и @group.",
                () -> openSection(SECTION_FORMAT)
        ), fullWidthWithBottomMargin());
        root.addView(createActionCard(
                "Поиск задач в файлах",
                "Маски, исключения и лимит сканирования папок.",
                () -> openSection(SECTION_SCAN)
        ), fullWidthWithBottomMargin());
        root.addView(createActionCard(
                "Продвинутые / Отладка",
                "Служебное состояние, последняя ошибка, кэш и debug actions.",
                () -> openSection(SECTION_ADVANCED)
        ), fullWidthWithBottomMargin());

        Button closeButton = createButton("Закрыть");
        closeButton.setOnClickListener(view -> finish());
        root.addView(closeButton, fullWidthWithBottomMargin());
    }

    private void addSettingsSection(LinearLayout root, String section) {
        if (SECTION_BASIC.equals(section)) {
            addBasicSettings(root);
        } else if (SECTION_SOURCES.equals(section)) {
            addSourceSettings(root);
        } else if (SECTION_NOTIFICATIONS.equals(section)) {
            addNotificationSettings(root);
        } else if (SECTION_FORMAT.equals(section)) {
            addFormatSettings(root);
        } else if (SECTION_SCAN.equals(section)) {
            addScanSettings(root);
        } else if (SECTION_ADVANCED.equals(section)) {
            addAdvancedSettings(root);
        } else {
            root.addView(createDescription("Раздел настроек не найден."), fullWidthWithBottomMargin());
        }
    }

    private String sectionTitle(String section) {
        if (SECTION_BASIC.equals(section)) {
            return "Основные";
        }
        if (SECTION_SOURCES.equals(section)) {
            return "Источники";
        }
        if (SECTION_NOTIFICATIONS.equals(section)) {
            return "Уведомления";
        }
        if (SECTION_FORMAT.equals(section)) {
            return "Формат задач";
        }
        if (SECTION_SCAN.equals(section)) {
            return "Поиск задач";
        }
        if (SECTION_ADVANCED.equals(section)) {
            return "Продвинутые";
        }
        return "Настройки";
    }

    private void openSection(String section) {
        Intent intent = new Intent(this, SettingsActivity.class);
        intent.putExtra(EXTRA_SECTION, section);
        startActivity(intent);
    }

    private void addBasicSettings(LinearLayout root) {
        root.addView(createDescription(
                "Здесь остаются только настройки, которые меняют ежедневный сценарий работы."
        ), fullWidthWithBottomMargin());

        root.addView(createSubsectionLabel("Основной способ открытия задачи"), fullWidth());
        String mode = EditPreferences.getEditMode(this);
        root.addView(createChoiceCard(
                "Через UI",
                "Форма одной задачи с preview итоговой markdown-строки.",
                EditPreferences.MODE_UI.equals(mode),
                () -> setEditMode(EditPreferences.MODE_UI)
        ), fullWidthWithBottomMargin());
        root.addView(createChoiceCard(
                "Markdown-файл",
                "Открывать полный исходный файл на строке задачи.",
                EditPreferences.MODE_MARKDOWN.equals(mode),
                () -> setEditMode(EditPreferences.MODE_MARKDOWN)
        ), fullWidthWithBottomMargin());

        root.addView(createSubsectionLabel("Главный экран"), fullWidth());
        activeFilterButton = createButton("");
        activeFilterButton.setOnClickListener(view -> {
            UserPreferences.setActiveOnly(this, !UserPreferences.isActiveOnly(this));
            updateActiveFilterButton();
        });
        updateActiveFilterButton();
        root.addView(activeFilterButton, fullWidthWithBottomMargin());

        showSourceOnMainCheckbox = createCheckBox(
                "Показывать источник на главном экране",
                UserPreferences.shouldShowSourceOnMain(this)
        );
        showSourceOnMainCheckbox.setOnCheckedChangeListener((button, checked) ->
                UserPreferences.setShowSourceOnMain(this, checked));
        root.addView(showSourceOnMainCheckbox, fullWidthWithBottomMargin());

        privateMarkerInput = addKeywordInput(
                root,
                "Приватная группа или тег",
                UserPreferences.getPrivateMarker(this)
        );
        root.addView(createDescription(
                "Например private: задачи с @group(private) или #private скрываются в «Все группы». Пустое значение отключает скрытие."
        ), fullWidthWithBottomMargin());
        Button savePrivateMarkerButton = createButton("Сохранить приватный маркер");
        savePrivateMarkerButton.setOnClickListener(view -> savePrivateMarker());
        root.addView(savePrivateMarkerButton, fullWidthWithBottomMargin());

        root.addView(createSubsectionLabel("Группировка"), fullWidth());
        String groupingMode = UserPreferences.getGroupingMode(this);
        root.addView(createChoiceCard(
                "Смешанная",
                "Использовать @group, иначе первый тег или имя файла.",
                UserPreferences.GROUPING_SMART.equals(groupingMode),
                () -> setGroupingMode(UserPreferences.GROUPING_SMART)
        ), fullWidthWithBottomMargin());
        root.addView(createChoiceCard(
                "По @group",
                "Показывать группы только из @group(...).",
                UserPreferences.GROUPING_GROUP.equals(groupingMode),
                () -> setGroupingMode(UserPreferences.GROUPING_GROUP)
        ), fullWidthWithBottomMargin());
        root.addView(createChoiceCard(
                "По тегам",
                "Использовать первый тег задачи как группу.",
                UserPreferences.GROUPING_TAG.equals(groupingMode),
                () -> setGroupingMode(UserPreferences.GROUPING_TAG)
        ), fullWidthWithBottomMargin());
        root.addView(createChoiceCard(
                "По файлам",
                "Группировать задачи по имени markdown-файла.",
                UserPreferences.GROUPING_FILE.equals(groupingMode),
                () -> setGroupingMode(UserPreferences.GROUPING_FILE)
        ), fullWidthWithBottomMargin());

        root.addView(createSubsectionLabel("Тема"), fullWidth());
        String themeMode = ThemePreferences.getThemeMode(this);
        root.addView(createChoiceCard(
                "Системная",
                "Следовать настройке темы Android.",
                ThemePreferences.MODE_SYSTEM.equals(themeMode),
                () -> setThemeMode(ThemePreferences.MODE_SYSTEM)
        ), fullWidthWithBottomMargin());
        root.addView(createChoiceCard(
                "Светлая",
                "Всегда использовать светлую тему.",
                ThemePreferences.MODE_LIGHT.equals(themeMode),
                () -> setThemeMode(ThemePreferences.MODE_LIGHT)
        ), fullWidthWithBottomMargin());
        root.addView(createChoiceCard(
                "Темная",
                "Всегда использовать темную тему.",
                ThemePreferences.MODE_DARK.equals(themeMode),
                () -> setThemeMode(ThemePreferences.MODE_DARK)
        ), fullWidthWithBottomMargin());
    }

    private void addSourceSettings(LinearLayout root) {
        root.addView(createDescription(
                "Источник нужен для первичной настройки. После выбора он может быть скрыт с главного экрана."
        ), fullWidthWithBottomMargin());
        root.addView(createActionCard(
                "Управление источниками",
                "Текущий источник: " + compactName(NoteStore.sourceLabel(this)),
                this::openSourceManagement
        ), fullWidthWithBottomMargin());
        root.addView(createActionCard(
                "Заменить заметками",
                "Выбрать один или несколько markdown-файлов заново.",
                () -> openNotePicker(REQUEST_REPLACE_NOTES)
        ), fullWidthWithBottomMargin());
        root.addView(createActionCard(
                "Заменить папкой",
                "Выбрать папку vault или Syncthing-папку.",
                () -> openFolderPicker(REQUEST_REPLACE_FOLDER)
        ), fullWidthWithBottomMargin());
        root.addView(createActionCard(
                "Добавить заметки",
                "Добавить markdown-файлы к текущим источникам.",
                () -> openNotePicker(REQUEST_ADD_NOTES)
        ), fullWidthWithBottomMargin());
        root.addView(createActionCard(
                "Добавить папку",
                "Добавить еще одну папку к текущим источникам.",
                () -> openFolderPicker(REQUEST_ADD_FOLDER)
        ), fullWidthWithBottomMargin());
        Button clearButton = createButton("Очистить источники");
        clearButton.setOnClickListener(view -> clearSources());
        root.addView(clearButton, fullWidthWithBottomMargin());
    }

    private void addNotificationSettings(LinearLayout root) {
        root.addView(createDescription(
                "Параметры точности, отложения и действий из системных уведомлений."
        ), fullWidthWithBottomMargin());
        root.addView(createActionCard(
                "Exact alarms",
                ReminderScheduler.canScheduleExactAlarms(this)
                        ? "Точные напоминания разрешены."
                        : "Разрешите точные напоминания в системных настройках.",
                this::requestExactAlarmPermission
        ), fullWidthWithBottomMargin());
        addNotificationActionSettings(root);
        root.addView(createActionCard(
                "Перепланировать все",
                "Перечитать источник и заново поставить активные уведомления.",
                this::rescheduleAll
        ), fullWidthWithBottomMargin());
        root.addView(createActionCard(
                "Тестовое уведомление",
                "Проверить звук, вибрацию и разрешение уведомлений.",
                this::sendTestNotification
        ), fullWidthWithBottomMargin());
    }

    private void addAdvancedSettings(LinearLayout root) {
        LinearLayout summaryCard = createSettingsCard();
        TextView summaryTitle = new TextView(this);
        summaryTitle.setText("Состояние");
        summaryTitle.setTextSize(15);
        summaryTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        summaryTitle.setTextColor(getColor(R.color.text_primary));
        summaryCard.addView(summaryTitle, fullWidth());
        statusText = new TextView(this);
        statusText.setTextSize(13);
        statusText.setTextColor(getColor(R.color.text_secondary));
        statusText.setPadding(0, dp(6), 0, 0);
        summaryCard.addView(statusText, fullWidth());
        root.addView(summaryCard, fullWidthWithBottomMargin());
        addDebugSettings(root);
    }

    private void addEditingSettings(LinearLayout root) {
        TextView editTitle = new TextView(this);
        editTitle.setText("Поведение");
        editTitle.setTextSize(18);
        editTitle.setTextColor(getColor(R.color.text_primary));
        editTitle.setPadding(0, dp(12), 0, dp(6));
        root.addView(editTitle, fullWidth());

        TextView editDescription = new TextView(this);
        editDescription.setText("Основной способ открытия задачи из списка и базовое поведение главного экрана.");
        editDescription.setTextSize(14);
        editDescription.setTextColor(getColor(R.color.text_secondary));
        editDescription.setPadding(0, 0, 0, dp(8));
        root.addView(editDescription, fullWidth());

        root.addView(createSubsectionLabel("Основной способ редактирования"), fullWidth());
        String mode = EditPreferences.getEditMode(this);
        root.addView(createChoiceCard(
                "Через UI",
                "Форма одной задачи с preview итоговой markdown-строки.",
                EditPreferences.MODE_UI.equals(mode),
                () -> setEditMode(EditPreferences.MODE_UI)
        ), fullWidthWithBottomMargin());
        root.addView(createChoiceCard(
                "Markdown-файл",
                "Полный исходный файл с переходом к строке выбранной задачи.",
                EditPreferences.MODE_MARKDOWN.equals(mode),
                () -> setEditMode(EditPreferences.MODE_MARKDOWN)
        ), fullWidthWithBottomMargin());

        activeFilterButton = createButton("");
        activeFilterButton.setOnClickListener(view -> {
            UserPreferences.setActiveOnly(this, !UserPreferences.isActiveOnly(this));
            updateActiveFilterButton();
            updateStatus();
        });
        updateActiveFilterButton();
        root.addView(activeFilterButton, fullWidthWithBottomMargin());

        root.addView(createSubsectionLabel("Группировка на главном экране"), fullWidth());
        String groupingMode = UserPreferences.getGroupingMode(this);
        root.addView(createChoiceCard(
                "Смешанная",
                "Использовать @group, иначе первый тег или имя файла.",
                UserPreferences.GROUPING_SMART.equals(groupingMode),
                () -> setGroupingMode(UserPreferences.GROUPING_SMART)
        ), fullWidthWithBottomMargin());
        root.addView(createChoiceCard(
                "По @group",
                "Показывать группы только из @group(...).",
                UserPreferences.GROUPING_GROUP.equals(groupingMode),
                () -> setGroupingMode(UserPreferences.GROUPING_GROUP)
        ), fullWidthWithBottomMargin());
        root.addView(createChoiceCard(
                "По тегам",
                "Использовать первый тег задачи как группу.",
                UserPreferences.GROUPING_TAG.equals(groupingMode),
                () -> setGroupingMode(UserPreferences.GROUPING_TAG)
        ), fullWidthWithBottomMargin());
        root.addView(createChoiceCard(
                "По файлам",
                "Группировать задачи по имени markdown-файла.",
                UserPreferences.GROUPING_FILE.equals(groupingMode),
                () -> setGroupingMode(UserPreferences.GROUPING_FILE)
        ), fullWidthWithBottomMargin());
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

        String themeMode = ThemePreferences.getThemeMode(this);
        root.addView(createChoiceCard(
                "Системная",
                "Следовать настройке темы Android.",
                ThemePreferences.MODE_SYSTEM.equals(themeMode),
                () -> setThemeMode(ThemePreferences.MODE_SYSTEM)
        ), fullWidthWithBottomMargin());
        root.addView(createChoiceCard(
                "Светлая",
                "Всегда использовать светлую тему.",
                ThemePreferences.MODE_LIGHT.equals(themeMode),
                () -> setThemeMode(ThemePreferences.MODE_LIGHT)
        ), fullWidthWithBottomMargin());
        root.addView(createChoiceCard(
                "Темная",
                "Всегда использовать темную тему.",
                ThemePreferences.MODE_DARK.equals(themeMode),
                () -> setThemeMode(ThemePreferences.MODE_DARK)
        ), fullWidthWithBottomMargin());
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
    private void openSourceManagement() {
        startActivityForResult(new Intent(this, SourceManagementActivity.class), REQUEST_SOURCE_MANAGEMENT);
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

    private void setGroupingMode(String mode) {
        UserPreferences.setGroupingMode(this, mode);
        Toast.makeText(this, "Группировка сохранена", Toast.LENGTH_SHORT).show();
        rebuild();
    }

    private void setThemeMode(String mode) {
        ThemePreferences.setThemeMode(this, mode);
        Toast.makeText(this, "Тема сохранена", Toast.LENGTH_SHORT).show();
        recreate();
    }

    private void savePrivateMarker() {
        UserPreferences.setPrivateMarker(this, privateMarkerInput.getText().toString());
        privateMarkerInput.setText(UserPreferences.getPrivateMarker(this));
        Toast.makeText(this, "Приватный маркер сохранен", Toast.LENGTH_SHORT).show();
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

    private void requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                || ReminderScheduler.canScheduleExactAlarms(this)) {
            Toast.makeText(this, "Точные напоминания уже разрешены", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(
                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                Uri.parse("package:" + getPackageName())
        );
        startActivity(intent);
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
                ? "markdown-файл"
                : "UI-форма";
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
        if (statusText == null) {
            return;
        }
        String cachedAt = TaskCache.getSavedAt(this);
        String latestError = ErrorLog.latest(this);
        StringBuilder status = new StringBuilder("Источник: " + compactName(NoteStore.sourceLabel(this))
                + " · источников: " + NoteStore.getSavedSourceCount(this)
                + "\nФильтр: " + UserPreferences.getTaskFilterLabel(this)
                + " · запись: " + (NoteStore.canWriteSavedSource(this) ? "да" : "нужно выбрать источник")
                + "\nНапоминания: точные "
                + (ReminderScheduler.canScheduleExactAlarms(this) ? "да" : "нет")
                + " · отложить " + ActionPreferences.getSnoozeMinutes(this) + " мин."
                + " · счетчик: " + (ActionPreferences.shouldRecordSnoozeCount(this) ? "вкл." : "выкл.")
                + "\nРедактирование: " + formatEditMode(EditPreferences.getEditMode(this))
                + " · тема: " + formatThemeMode(ThemePreferences.getThemeMode(this))
                + "\nГруппировка: " + UserPreferences.getGroupingModeLabel(this)
                + "\nКэш задач: " + TaskCache.getCachedTaskCount(this)
                + (cachedAt == null ? "" : " · " + cachedAt));
        if (latestError != null) {
            status.append("\nПоследняя ошибка записана в лог.");
        }
        statusText.setText(status.toString());
    }

    private void updateActiveFilterButton() {
        activeFilterButton.setText(UserPreferences.isActiveOnly(this)
                ? "Главный экран: только активные"
                : "Главный экран: все задачи");
    }

    private void addSectionTitle(LinearLayout root, String text) {
        TextView title = new TextView(this);
        title.setText(text);
        title.setTextSize(18);
        title.setTextColor(getColor(R.color.text_primary));
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setPadding(0, dp(12), 0, dp(6));
        root.addView(title, fullWidth());
    }

    private TextView createSubsectionLabel(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(14);
        label.setTextColor(getColor(R.color.text_secondary));
        label.setPadding(0, 0, 0, dp(6));
        return label;
    }

    private LinearLayout createSettingsCard() {
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

    private LinearLayout createActionCard(String title, String subtitle, Runnable action) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(android.view.Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(createRoundedBackground(
                getColor(R.color.card_background),
                getColor(R.color.card_stroke),
                8
        ));
        card.setOnClickListener(view -> action.run());

        LinearLayout textColumn = new LinearLayout(this);
        textColumn.setOrientation(LinearLayout.VERTICAL);

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextSize(15);
        titleView.setTextColor(getColor(R.color.text_primary));
        titleView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        textColumn.addView(titleView, fullWidth());

        TextView subtitleView = new TextView(this);
        subtitleView.setText(subtitle);
        subtitleView.setTextSize(13);
        subtitleView.setTextColor(getColor(R.color.text_secondary));
        subtitleView.setPadding(0, dp(3), 0, 0);
        textColumn.addView(subtitleView, fullWidth());

        card.addView(textColumn, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        ));

        TextView chevron = new TextView(this);
        chevron.setText("›");
        chevron.setTextSize(24);
        chevron.setTextColor(getColor(R.color.text_secondary));
        chevron.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams chevronParams = new LinearLayout.LayoutParams(dp(32), dp(42));
        chevronParams.setMargins(dp(8), 0, 0, 0);
        card.addView(chevron, chevronParams);
        return card;
    }

    private LinearLayout createChoiceCard(
            String title,
            String subtitle,
            boolean selected,
            Runnable action
    ) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(android.view.Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(createRoundedBackground(
                getColor(selected ? R.color.chip_selected_background : R.color.card_background),
                getColor(selected ? R.color.chip_selected_stroke : R.color.card_stroke),
                8
        ));
        card.setOnClickListener(view -> action.run());

        LinearLayout textColumn = new LinearLayout(this);
        textColumn.setOrientation(LinearLayout.VERTICAL);

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextSize(15);
        titleView.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
        titleView.setTextColor(getColor(R.color.text_primary));
        textColumn.addView(titleView, fullWidth());

        TextView subtitleView = new TextView(this);
        subtitleView.setText(subtitle);
        subtitleView.setTextSize(13);
        subtitleView.setTextColor(getColor(R.color.text_secondary));
        subtitleView.setPadding(0, dp(3), 0, 0);
        textColumn.addView(subtitleView, fullWidth());

        card.addView(textColumn, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        ));

        TextView state = new TextView(this);
        state.setText(selected ? "выбрано" : "");
        state.setTextSize(12);
        state.setTextColor(getColor(R.color.chip_selected_text));
        state.setPadding(dp(10), 0, 0, 0);
        card.addView(state, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        return card;
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

    private Button createSmallButton(String text) {
        Button button = createButton(text);
        button.setMinHeight(0);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(0, 0, 0, 0);
        return button;
    }

    private TextView createDescription(String text) {
        TextView description = new TextView(this);
        description.setText(text);
        description.setTextSize(14);
        description.setTextColor(getColor(R.color.text_secondary));
        description.setPadding(0, 0, 0, dp(2));
        return description;
    }

    private CheckBox createCheckBox(String text, boolean checked) {
        CheckBox checkBox = new CheckBox(this);
        checkBox.setText(text);
        checkBox.setTextSize(14);
        checkBox.setTextColor(getColor(R.color.text_secondary));
        checkBox.setChecked(checked);
        return checkBox;
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
