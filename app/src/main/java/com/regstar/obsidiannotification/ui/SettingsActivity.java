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
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
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
    private EditText repeatUntilDoneMinutesInput;
    private EditText overdueGraceMinutesInput;
    private EditText includePatternsInput;
    private EditText excludePatternsInput;
    private EditText maxFilesInput;
    private EditText privateMarkerInput;
    private CheckBox recordSnoozeCountCheckbox;
    private CheckBox showSourceOnMainCheckbox;
    private CheckBox autoSkipEnabledCheckbox;
    private EditText autoSkipDelayMinutesInput;
    private LinearLayout autoSkipDetailsContainer;

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

        TaskSourceManager.useExternalStorage(this);
        OnboardingPreferences.markCompleted(this);
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
        addHeader(root, getString(R.string.settings_title), false);
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
                getString(R.string.settings_section_basic_title),
                getString(R.string.settings_section_basic_subtitle),
                () -> openSection(SECTION_BASIC)
        ), fullWidthWithBottomMargin());
        root.addView(createActionCard(
                getString(R.string.settings_section_sources_title),
                getString(R.string.settings_section_sources_subtitle),
                () -> openSection(SECTION_SOURCES)
        ), fullWidthWithBottomMargin());
        root.addView(createActionCard(
                getString(R.string.settings_section_notifications_title),
                getString(R.string.settings_section_notifications_subtitle),
                () -> openSection(SECTION_NOTIFICATIONS)
        ), fullWidthWithBottomMargin());
        root.addView(createActionCard(
                getString(R.string.settings_section_format_title),
                getString(R.string.settings_section_format_subtitle),
                () -> openSection(SECTION_FORMAT)
        ), fullWidthWithBottomMargin());
        root.addView(createActionCard(
                getString(R.string.settings_section_scan_title),
                getString(R.string.settings_section_scan_subtitle),
                () -> openSection(SECTION_SCAN)
        ), fullWidthWithBottomMargin());
        root.addView(createActionCard(
                getString(R.string.settings_section_advanced_title),
                getString(R.string.settings_section_advanced_subtitle),
                () -> openSection(SECTION_ADVANCED)
        ), fullWidthWithBottomMargin());

        Button closeButton = createButton(getString(R.string.settings_close));
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
        root.addView(createDescription(getString(R.string.settings_section_not_found)), fullWidthWithBottomMargin());
        }
    }

    private String sectionTitle(String section) {
        if (SECTION_BASIC.equals(section)) {
            return getString(R.string.settings_section_basic_title);
        }
        if (SECTION_SOURCES.equals(section)) {
            return getString(R.string.settings_section_sources_title);
        }
        if (SECTION_NOTIFICATIONS.equals(section)) {
            return getString(R.string.settings_section_notifications_title);
        }
        if (SECTION_FORMAT.equals(section)) {
            return getString(R.string.settings_section_format_title);
        }
        if (SECTION_SCAN.equals(section)) {
            return getString(R.string.settings_section_scan_title);
        }
        if (SECTION_ADVANCED.equals(section)) {
            return getString(R.string.settings_advanced_title);
        }
        return getString(R.string.settings_title);
    }

    private void openSection(String section) {
        Intent intent = new Intent(this, SettingsActivity.class);
        intent.putExtra(EXTRA_SECTION, section);
        startActivity(intent);
    }

    private void addBasicSettings(LinearLayout root) {
        root.addView(createDescription(
                getString(R.string.settings_basic_description)
        ), fullWidthWithBottomMargin());

        root.addView(createSubsectionLabel(getString(R.string.settings_open_mode_title)), fullWidth());
        String mode = EditPreferences.getEditMode(this);
        root.addView(createChoiceCard(
                getString(R.string.settings_edit_mode_ui_title),
                getString(R.string.settings_edit_mode_ui_subtitle),
                EditPreferences.MODE_UI.equals(mode),
                () -> setEditMode(EditPreferences.MODE_UI)
        ), fullWidthWithBottomMargin());
        root.addView(createChoiceCard(
                getString(R.string.settings_edit_mode_markdown_title),
                getString(R.string.settings_edit_mode_markdown_subtitle),
                EditPreferences.MODE_MARKDOWN.equals(mode),
                () -> setEditMode(EditPreferences.MODE_MARKDOWN)
        ), fullWidthWithBottomMargin());

        root.addView(createSubsectionLabel(getString(R.string.settings_main_screen_title)), fullWidth());
        activeFilterButton = createButton("");
        activeFilterButton.setOnClickListener(view -> {
            UserPreferences.setActiveOnly(this, !UserPreferences.isActiveOnly(this));
            updateActiveFilterButton();
        });
        updateActiveFilterButton();
        root.addView(activeFilterButton, fullWidthWithBottomMargin());

        showSourceOnMainCheckbox = createCheckBox(
                getString(R.string.settings_show_source_on_main),
                UserPreferences.shouldShowSourceOnMain(this)
        );
        showSourceOnMainCheckbox.setOnCheckedChangeListener((button, checked) ->
                UserPreferences.setShowSourceOnMain(this, checked));
        root.addView(showSourceOnMainCheckbox, fullWidthWithBottomMargin());

        privateMarkerInput = addKeywordInput(
                root,
                getString(R.string.settings_private_marker_title),
                UserPreferences.getPrivateMarker(this)
        );
        root.addView(createDescription(
                getString(R.string.settings_private_marker_description)
        ), fullWidthWithBottomMargin());
        Button savePrivateMarkerButton = createButton(getString(R.string.settings_save_private_marker));
        savePrivateMarkerButton.setOnClickListener(view -> savePrivateMarker());
        root.addView(savePrivateMarkerButton, fullWidthWithBottomMargin());

        root.addView(createSubsectionLabel(getString(R.string.settings_grouping_title)), fullWidth());
        String groupingMode = UserPreferences.getGroupingMode(this);
        root.addView(createChoiceCard(
                getString(R.string.settings_grouping_smart_title),
                getString(R.string.settings_grouping_smart_subtitle),
                UserPreferences.GROUPING_SMART.equals(groupingMode),
                () -> setGroupingMode(UserPreferences.GROUPING_SMART)
        ), fullWidthWithBottomMargin());
        root.addView(createChoiceCard(
                getString(R.string.settings_grouping_group_title),
                getString(R.string.settings_grouping_group_subtitle),
                UserPreferences.GROUPING_GROUP.equals(groupingMode),
                () -> setGroupingMode(UserPreferences.GROUPING_GROUP)
        ), fullWidthWithBottomMargin());
        root.addView(createChoiceCard(
                getString(R.string.settings_grouping_tag_title),
                getString(R.string.settings_grouping_tag_subtitle),
                UserPreferences.GROUPING_TAG.equals(groupingMode),
                () -> setGroupingMode(UserPreferences.GROUPING_TAG)
        ), fullWidthWithBottomMargin());
        root.addView(createChoiceCard(
                getString(R.string.settings_grouping_file_title),
                getString(R.string.settings_grouping_file_subtitle),
                UserPreferences.GROUPING_FILE.equals(groupingMode),
                () -> setGroupingMode(UserPreferences.GROUPING_FILE)
        ), fullWidthWithBottomMargin());

        root.addView(createSubsectionLabel(getString(R.string.settings_theme_title)), fullWidth());
        String themeMode = ThemePreferences.getThemeMode(this);
        root.addView(createChoiceCard(
                getString(R.string.settings_theme_system_title),
                getString(R.string.settings_theme_system_subtitle),
                ThemePreferences.MODE_SYSTEM.equals(themeMode),
                () -> setThemeMode(ThemePreferences.MODE_SYSTEM)
        ), fullWidthWithBottomMargin());
        root.addView(createChoiceCard(
                getString(R.string.settings_theme_light_title),
                getString(R.string.settings_theme_light_subtitle),
                ThemePreferences.MODE_LIGHT.equals(themeMode),
                () -> setThemeMode(ThemePreferences.MODE_LIGHT)
        ), fullWidthWithBottomMargin());
        root.addView(createChoiceCard(
                getString(R.string.settings_theme_dark_title),
                getString(R.string.settings_theme_dark_subtitle),
                ThemePreferences.MODE_DARK.equals(themeMode),
                () -> setThemeMode(ThemePreferences.MODE_DARK)
        ), fullWidthWithBottomMargin());
    }

    private void addSourceSettings(LinearLayout root) {
        boolean internalSelected =
                TaskSourceManager.getStorageMode(this) == TaskStorageMode.INTERNAL_MARKDOWN_STORAGE;
        boolean hasExternalSources = TaskSourceManager.hasExternalSources(this);
        String externalSubtitle = hasExternalSources
                ? getString(R.string.settings_external_current_source, compactName(NoteStore.externalSourceLabel(this)))
                : getString(R.string.storage_external_description_long);
        root.addView(createDescription(
                getString(R.string.settings_sources_description)
        ), fullWidthWithBottomMargin());
        root.addView(createChoiceCard(
                getString(R.string.storage_internal_label),
                getString(R.string.storage_internal_description_long),
                internalSelected,
                this::switchToInternalStorage
        ), fullWidthWithBottomMargin());
        root.addView(createChoiceCard(
                getString(R.string.storage_external_label),
                externalSubtitle,
                !internalSelected,
                this::activateOrSelectExternalStorage
        ), fullWidthWithBottomMargin());
        root.addView(createActionCard(
                getString(R.string.settings_manage_external_sources),
                getString(R.string.settings_manage_external_sources_subtitle),
                this::openSourceManagement
        ), fullWidthWithBottomMargin());
        root.addView(createDescription(
                getString(R.string.settings_source_switch_hint)
        ), fullWidthWithBottomMargin());
    }

    private void addNotificationSettings(LinearLayout root) {
        root.addView(createDescription(
                getString(R.string.settings_notifications_description)
        ), fullWidthWithBottomMargin());
        root.addView(createActionCard(
                getString(R.string.settings_exact_alarms_title),
                ReminderScheduler.canScheduleExactAlarms(this)
                        ? getString(R.string.settings_exact_alarms_enabled)
                        : getString(R.string.settings_exact_alarms_disabled),
                this::requestExactAlarmPermission
        ), fullWidthWithBottomMargin());
        addNotificationActionSettings(root);
        root.addView(createActionCard(
                getString(R.string.settings_reschedule_all),
                getString(R.string.settings_reschedule_all_subtitle),
                this::rescheduleAll
        ), fullWidthWithBottomMargin());
        root.addView(createActionCard(
                getString(R.string.settings_test_notification),
                getString(R.string.settings_test_notification_subtitle),
                this::sendTestNotification
        ), fullWidthWithBottomMargin());
    }

    private void addAdvancedSettings(LinearLayout root) {
        LinearLayout summaryCard = createSettingsCard();
        TextView summaryTitle = new TextView(this);
        summaryTitle.setText(getString(R.string.settings_state_title));
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
        editTitle.setText(getString(R.string.settings_behavior_title));
        editTitle.setTextSize(18);
        editTitle.setTextColor(getColor(R.color.text_primary));
        editTitle.setPadding(0, dp(12), 0, dp(6));
        root.addView(editTitle, fullWidth());

        TextView editDescription = new TextView(this);
        editDescription.setText(getString(R.string.settings_behavior_description));
        editDescription.setTextSize(14);
        editDescription.setTextColor(getColor(R.color.text_secondary));
        editDescription.setPadding(0, 0, 0, dp(8));
        root.addView(editDescription, fullWidth());

        root.addView(createSubsectionLabel(getString(R.string.settings_open_mode_title)), fullWidth());
        String mode = EditPreferences.getEditMode(this);
        root.addView(createChoiceCard(
                getString(R.string.settings_edit_mode_ui_title),
                getString(R.string.settings_edit_mode_ui_subtitle),
                EditPreferences.MODE_UI.equals(mode),
                () -> setEditMode(EditPreferences.MODE_UI)
        ), fullWidthWithBottomMargin());
        root.addView(createChoiceCard(
                getString(R.string.settings_edit_mode_markdown_title),
                getString(R.string.settings_edit_mode_markdown_subtitle),
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

        root.addView(createSubsectionLabel(getString(R.string.settings_main_grouping_title)), fullWidth());
        String groupingMode = UserPreferences.getGroupingMode(this);
        root.addView(createChoiceCard(
                getString(R.string.settings_grouping_smart_title),
                getString(R.string.settings_grouping_smart_subtitle),
                UserPreferences.GROUPING_SMART.equals(groupingMode),
                () -> setGroupingMode(UserPreferences.GROUPING_SMART)
        ), fullWidthWithBottomMargin());
        root.addView(createChoiceCard(
                getString(R.string.settings_grouping_group_title),
                getString(R.string.settings_grouping_group_subtitle),
                UserPreferences.GROUPING_GROUP.equals(groupingMode),
                () -> setGroupingMode(UserPreferences.GROUPING_GROUP)
        ), fullWidthWithBottomMargin());
        root.addView(createChoiceCard(
                getString(R.string.settings_grouping_tag_title),
                getString(R.string.settings_grouping_tag_subtitle),
                UserPreferences.GROUPING_TAG.equals(groupingMode),
                () -> setGroupingMode(UserPreferences.GROUPING_TAG)
        ), fullWidthWithBottomMargin());
        root.addView(createChoiceCard(
                getString(R.string.settings_grouping_file_title),
                getString(R.string.settings_grouping_file_subtitle),
                UserPreferences.GROUPING_FILE.equals(groupingMode),
                () -> setGroupingMode(UserPreferences.GROUPING_FILE)
        ), fullWidthWithBottomMargin());
    }

    private void addAppearanceSettings(LinearLayout root) {
        TextView appearanceTitle = new TextView(this);
        appearanceTitle.setText(getString(R.string.settings_appearance_title));
        appearanceTitle.setTextSize(18);
        appearanceTitle.setTextColor(getColor(R.color.text_primary));
        appearanceTitle.setPadding(0, dp(12), 0, dp(6));
        root.addView(appearanceTitle, fullWidth());

        TextView appearanceDescription = new TextView(this);
        appearanceDescription.setText(getString(R.string.settings_appearance_description));
        appearanceDescription.setTextSize(14);
        appearanceDescription.setTextColor(getColor(R.color.text_secondary));
        appearanceDescription.setPadding(0, 0, 0, dp(8));
        root.addView(appearanceDescription, fullWidth());

        String themeMode = ThemePreferences.getThemeMode(this);
        root.addView(createChoiceCard(
                getString(R.string.settings_theme_system_title),
                getString(R.string.settings_theme_system_subtitle),
                ThemePreferences.MODE_SYSTEM.equals(themeMode),
                () -> setThemeMode(ThemePreferences.MODE_SYSTEM)
        ), fullWidthWithBottomMargin());
        root.addView(createChoiceCard(
                getString(R.string.settings_theme_light_title),
                getString(R.string.settings_theme_light_subtitle),
                ThemePreferences.MODE_LIGHT.equals(themeMode),
                () -> setThemeMode(ThemePreferences.MODE_LIGHT)
        ), fullWidthWithBottomMargin());
        root.addView(createChoiceCard(
                getString(R.string.settings_theme_dark_title),
                getString(R.string.settings_theme_dark_subtitle),
                ThemePreferences.MODE_DARK.equals(themeMode),
                () -> setThemeMode(ThemePreferences.MODE_DARK)
        ), fullWidthWithBottomMargin());
    }

    private void addFormatSettings(LinearLayout root) {
        TextView formatTitle = new TextView(this);
        formatTitle.setText(getString(R.string.settings_task_format_title));
        formatTitle.setTextSize(18);
        formatTitle.setTextColor(getColor(R.color.text_primary));
        formatTitle.setPadding(0, dp(12), 0, dp(6));
        root.addView(formatTitle, fullWidth());

        TaskFormatSettings settings = TaskFormatSettings.load(this);
        dueKeywordInput = addKeywordInput(root, getString(R.string.settings_due_keyword), settings.getDueKeyword());
        repeatKeywordInput = addKeywordInput(root, getString(R.string.settings_repeat_keyword), settings.getRepeatKeyword());
        repeatUntilDoneKeywordInput = addKeywordInput(
                root,
                getString(R.string.settings_repeat_until_done_keyword),
                settings.getRepeatUntilDoneKeyword()
        );
        tagKeywordInput = addKeywordInput(root, getString(R.string.settings_tag_keyword), settings.getTagKeyword());
        priorityKeywordInput = addKeywordInput(
                root,
                getString(R.string.settings_priority_keyword),
                settings.getPriorityKeyword()
        );
        groupKeywordInput = addKeywordInput(root, getString(R.string.settings_group_keyword), settings.getGroupKeyword());

        Button saveFormatButton = createButton(getString(R.string.settings_save));
        saveFormatButton.setOnClickListener(view -> saveFormatSettings());
        root.addView(saveFormatButton, fullWidthWithBottomMargin());

        Button resetFormatButton = createButton(getString(R.string.settings_reset));
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
        scanTitle.setText(getString(R.string.settings_scan_title));
        scanTitle.setTextSize(18);
        scanTitle.setTextColor(getColor(R.color.text_primary));
        scanTitle.setPadding(0, dp(12), 0, dp(6));
        root.addView(scanTitle, fullWidth());

        NoteScanSettings settings = NoteScanSettings.load(this);
        includePatternsInput = addKeywordInput(
                root,
                getString(R.string.settings_scan_include_patterns),
                settings.getIncludePatternsText()
        );
        excludePatternsInput = addKeywordInput(
                root,
                getString(R.string.settings_scan_exclude_patterns),
                settings.getExcludePatternsText()
        );
        maxFilesInput = addKeywordInput(
                root,
                getString(R.string.settings_scan_max_files),
                String.valueOf(settings.getMaxFiles())
        );
        maxFilesInput.setInputType(InputType.TYPE_CLASS_NUMBER);

        Button saveScanButton = createButton(getString(R.string.settings_scan_save));
        saveScanButton.setOnClickListener(view -> saveScanSettings());
        root.addView(saveScanButton, fullWidthWithBottomMargin());

        Button resetScanButton = createButton(getString(R.string.settings_scan_reset));
        resetScanButton.setOnClickListener(view -> resetScanSettings());
        root.addView(resetScanButton, fullWidthWithBottomMargin());
    }

    private void addNotificationActionSettings(LinearLayout root) {
        TextView actionTitle = new TextView(this);
        actionTitle.setText(getString(R.string.settings_notification_actions_title));
        actionTitle.setTextSize(18);
        actionTitle.setTextColor(getColor(R.color.text_primary));
        actionTitle.setPadding(0, dp(12), 0, dp(6));
        root.addView(actionTitle, fullWidth());

        snoozeMinutesInput = addKeywordInput(
                root,
                getString(R.string.settings_snooze_minutes),
                String.valueOf(ActionPreferences.getSnoozeMinutes(this))
        );
        snoozeMinutesInput.setInputType(InputType.TYPE_CLASS_NUMBER);

        repeatUntilDoneMinutesInput = addKeywordInput(
                root,
                getString(R.string.settings_repeat_until_done_minutes),
                String.valueOf(ActionPreferences.getRepeatUntilDoneMinutes(this))
        );
        repeatUntilDoneMinutesInput.setInputType(InputType.TYPE_CLASS_NUMBER);

        overdueGraceMinutesInput = addKeywordInput(
                root,
                getString(R.string.settings_overdue_grace_minutes),
                String.valueOf(ActionPreferences.getOverdueGraceMinutes(this))
        );
        overdueGraceMinutesInput.setInputType(InputType.TYPE_CLASS_NUMBER);

        autoSkipEnabledCheckbox = new CheckBox(this);
        autoSkipEnabledCheckbox.setText(getString(R.string.settings_auto_skip_enabled));
        autoSkipEnabledCheckbox.setTextColor(getColor(R.color.text_secondary));
        autoSkipEnabledCheckbox.setChecked(AutoSkipPreferences.isEnabled(this));
        autoSkipEnabledCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (autoSkipDetailsContainer != null) {
                autoSkipDetailsContainer.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            }
        });
        root.addView(autoSkipEnabledCheckbox, fullWidthWithBottomMargin());

        autoSkipDetailsContainer = new LinearLayout(this);
        autoSkipDetailsContainer.setOrientation(LinearLayout.VERTICAL);
        autoSkipDetailsContainer.setVisibility(
                AutoSkipPreferences.isEnabled(this) ? View.VISIBLE : View.GONE
        );

        autoSkipDelayMinutesInput = addKeywordInput(
                autoSkipDetailsContainer,
                getString(R.string.settings_auto_skip_custom_minutes_label),
                String.valueOf(AutoSkipPreferences.getDelayMinutes(this))
        );
        autoSkipDelayMinutesInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        root.addView(autoSkipDetailsContainer, fullWidthWithBottomMargin());

        recordSnoozeCountCheckbox = new CheckBox(this);
        recordSnoozeCountCheckbox.setText(getString(R.string.settings_record_snooze_count));
        recordSnoozeCountCheckbox.setTextColor(getColor(R.color.text_secondary));
        recordSnoozeCountCheckbox.setChecked(ActionPreferences.shouldRecordSnoozeCount(this));
        root.addView(recordSnoozeCountCheckbox, fullWidthWithBottomMargin());

        Button saveActionSettingsButton = createButton(getString(R.string.settings_save_notification_actions));
        saveActionSettingsButton.setOnClickListener(view -> saveActionSettings());
        root.addView(saveActionSettingsButton, fullWidthWithBottomMargin());
    }

    private void addDebugSettings(LinearLayout root) {
        TextView debugTitle = new TextView(this);
        debugTitle.setText(getString(R.string.settings_debug_title));
        debugTitle.setTextSize(18);
        debugTitle.setTextColor(getColor(R.color.text_primary));
        debugTitle.setPadding(0, dp(12), 0, dp(6));
        root.addView(debugTitle, fullWidth());

        TextView debugDescription = new TextView(this);
        debugDescription.setText(getString(R.string.settings_debug_description));
        debugDescription.setTextSize(14);
        debugDescription.setTextColor(getColor(R.color.text_secondary));
        debugDescription.setPadding(0, 0, 0, dp(8));
        root.addView(debugDescription, fullWidth());

        Button debugActionsButton = createButton(getString(R.string.settings_open_debug_actions));
        debugActionsButton.setOnClickListener(view -> showDebugActions());
        root.addView(debugActionsButton, fullWidthWithBottomMargin());
    }

    private void showDebugActions() {
        String[] actions = new String[]{
                getString(R.string.settings_debug_action_show_now),
                getString(R.string.settings_debug_action_complete_first),
                getString(R.string.settings_debug_action_snooze_first),
                getString(R.string.settings_reschedule_all),
                getString(R.string.settings_test_notification)
        };
        new android.app.AlertDialog.Builder(this)
                .setTitle(getString(R.string.settings_debug_dialog_title))
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

    private void switchToInternalStorage() {
        if (TaskSourceManager.getStorageMode(this) == TaskStorageMode.INTERNAL_MARKDOWN_STORAGE) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.settings_switch_source_title))
                .setMessage(TaskSourceManager.switchWithoutMigrationWarning(this, TaskStorageMode.INTERNAL_MARKDOWN_STORAGE))
                .setNegativeButton(getString(R.string.common_cancel), null)
                .setPositiveButton(getString(R.string.settings_switch_source_confirm), (dialog, which) -> {
                    try {
                        TaskSourceManager.useInternalStorage(this);
                        OnboardingPreferences.markCompleted(this);
                        rescheduleAll();
                        updateStatus();
                        Toast.makeText(this, getString(R.string.settings_internal_enabled), Toast.LENGTH_SHORT).show();
                    } catch (IOException exception) {
                        ErrorLog.record(this, getString(R.string.settings_enable_internal_error), exception);
                        Toast.makeText(
                                this,
                                getString(R.string.settings_enable_internal_error_prefix, safeMessage(exception)),
                                Toast.LENGTH_LONG
                        ).show();
                    }
                })
                .show();
    }

    private void activateOrSelectExternalStorage() {
        if (!TaskSourceManager.hasExternalSources(this)) {
            openSourceManagement();
            return;
        }
        TaskSourceManager.useExternalStorage(this);
        OnboardingPreferences.markCompleted(this);
        rescheduleAll();
        updateStatus();
        Toast.makeText(this, getString(R.string.settings_external_activated), Toast.LENGTH_SHORT).show();
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
        try {
            TaskSourceManager.useInternalStorage(this);
            OnboardingPreferences.markCompleted(this);
            rescheduleAll();
            Toast.makeText(
                    this,
                    getString(R.string.settings_external_cleared_internal_active),
                    Toast.LENGTH_SHORT
            ).show();
        } catch (IOException exception) {
            ErrorLog.record(this, getString(R.string.settings_clear_sources_internal_error), exception);
            Toast.makeText(
                    this,
                    getString(R.string.settings_clear_sources_internal_error_prefix, safeMessage(exception)),
                    Toast.LENGTH_LONG
            ).show();
        }
        updateStatus();
    }

    private String safeMessage(Exception exception) {
        String message = exception == null ? null : exception.getMessage();
        return message == null || message.trim().isEmpty()
                ? getString(R.string.settings_source_access_check)
                : message;
    }

    private void rescheduleAll() {
        NoteChangeMonitor.NoteSyncResult result = NoteChangeMonitor.syncNow(this, true);
        NoteChangeMonitor.ensureScheduled(this);
        if (result.isSuccess()) {
            Toast.makeText(
                    this,
                    getString(R.string.settings_rescheduled_count, result.getTaskCount()),
                    Toast.LENGTH_SHORT
            ).show();
        } else {
            String message = result.isRestoredFromCache()
                    ? getString(R.string.settings_source_cache_restored)
                    : getString(R.string.settings_source_read_error_prefix, result.getErrorMessage());
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

        int overdueGraceMinutes;
        try {
            overdueGraceMinutes = Integer.parseInt(overdueGraceMinutesInput.getText().toString().trim());
        } catch (NumberFormatException exception) {
            overdueGraceMinutes = ActionPreferences.getOverdueGraceMinutes(this);
        }

        int repeatUntilDoneMinutes;
        try {
            repeatUntilDoneMinutes = Integer.parseInt(repeatUntilDoneMinutesInput.getText().toString().trim());
        } catch (NumberFormatException exception) {
            repeatUntilDoneMinutes = ActionPreferences.getRepeatUntilDoneMinutes(this);
        }

        ActionPreferences.setSnoozeMinutes(this, snoozeMinutes);
        ActionPreferences.setRepeatUntilDoneMinutes(this, repeatUntilDoneMinutes);
        ActionPreferences.setOverdueGraceMinutes(this, overdueGraceMinutes);
        ActionPreferences.setRecordSnoozeCount(this, recordSnoozeCountCheckbox.isChecked());

        AutoSkipPreferences.setEnabled(this, autoSkipEnabledCheckbox.isChecked());
        int autoSkipDelayMinutes;
        try {
            autoSkipDelayMinutes = Integer.parseInt(autoSkipDelayMinutesInput.getText().toString().trim());
        } catch (NumberFormatException exception) {
            autoSkipDelayMinutes = AutoSkipPreferences.getDelayMinutes(this);
        }
        AutoSkipPreferences.setDelayMinutes(this, autoSkipDelayMinutes);

        snoozeMinutesInput.setText(String.valueOf(ActionPreferences.getSnoozeMinutes(this)));
        repeatUntilDoneMinutesInput.setText(String.valueOf(ActionPreferences.getRepeatUntilDoneMinutes(this)));
        overdueGraceMinutesInput.setText(String.valueOf(ActionPreferences.getOverdueGraceMinutes(this)));
        autoSkipDelayMinutesInput.setText(String.valueOf(AutoSkipPreferences.getDelayMinutes(this)));
        Toast.makeText(this, getString(R.string.settings_notification_actions_saved), Toast.LENGTH_SHORT).show();
        rescheduleAll();
        updateStatus();
    }

    private void setEditMode(String mode) {
        EditPreferences.setEditMode(this, mode);
        Toast.makeText(this, getString(R.string.settings_edit_mode_saved), Toast.LENGTH_SHORT).show();
        rebuild();
    }

    private void setGroupingMode(String mode) {
        UserPreferences.setGroupingMode(this, mode);
        Toast.makeText(this, getString(R.string.settings_grouping_saved), Toast.LENGTH_SHORT).show();
        rebuild();
    }

    private void setThemeMode(String mode) {
        ThemePreferences.setThemeMode(this, mode);
        Toast.makeText(this, getString(R.string.settings_theme_saved), Toast.LENGTH_SHORT).show();
        recreate();
    }

    private void savePrivateMarker() {
        UserPreferences.setPrivateMarker(this, privateMarkerInput.getText().toString());
        privateMarkerInput.setText(UserPreferences.getPrivateMarker(this));
        Toast.makeText(this, getString(R.string.settings_private_marker_saved), Toast.LENGTH_SHORT).show();
    }

    private String themeButtonText(String mode, String label) {
        return mode.equals(ThemePreferences.getThemeMode(this))
                ? getString(R.string.settings_theme_selected, label)
                : label;
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
                    getString(R.string.settings_keywords_must_differ),
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        TaskFormatSettings.save(this, settings);
        populateFormatInputs(settings);
        Toast.makeText(this, getString(R.string.settings_task_format_saved), Toast.LENGTH_SHORT).show();
        rescheduleAll();
    }

    private void resetFormatSettings() {
        TaskFormatSettings.reset(this);
        populateFormatInputs(TaskFormatSettings.defaults());
        Toast.makeText(this, getString(R.string.settings_task_format_reset), Toast.LENGTH_SHORT).show();
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
        Toast.makeText(this, getString(R.string.settings_scan_saved), Toast.LENGTH_SHORT).show();
        rescheduleAll();
    }

    private void resetScanSettings() {
        NoteScanSettings.reset(this);
        populateScanInputs(NoteScanSettings.defaults());
        Toast.makeText(this, getString(R.string.settings_scan_reset_done), Toast.LENGTH_SHORT).show();
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
                sent ? getString(R.string.settings_test_notification_sent)
                        : getString(R.string.settings_notification_permission_missing),
                Toast.LENGTH_SHORT
        ).show();
    }

    private void requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                || ReminderScheduler.canScheduleExactAlarms(this)) {
            Toast.makeText(this, getString(R.string.settings_exact_alarms_already_enabled), Toast.LENGTH_SHORT).show();
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
                ? getString(R.string.settings_edit_mode_markdown_value)
                : getString(R.string.settings_edit_mode_ui_value);
    }

    private String formatThemeMode(String mode) {
        if (ThemePreferences.MODE_LIGHT.equals(mode)) {
            return getString(R.string.settings_theme_light_value);
        }
        if (ThemePreferences.MODE_DARK.equals(mode)) {
            return getString(R.string.settings_theme_dark_value);
        }
        return getString(R.string.settings_theme_system_value);
    }

    private void updateStatus() {
        if (statusText == null) {
            return;
        }
        String cachedAt = TaskCache.getSavedAt(this);
        String latestError = ErrorLog.latest(this);
        StringBuilder status = new StringBuilder(getString(
                R.string.settings_status_template,
                TaskSourceManager.storageModeLabel(this),
                compactName(TaskSourceManager.activeSourceLabel(this)),
                TaskSourceManager.activeSourceCount(this),
                getString(TaskSourceManager.canWriteActiveSource(this)
                        ? R.string.settings_status_yes
                        : R.string.settings_status_no),
                UserPreferences.getTaskFilterLabel(this),
                getString(ReminderScheduler.canScheduleExactAlarms(this)
                        ? R.string.settings_status_yes
                        : R.string.settings_status_no),
                ActionPreferences.getSnoozeMinutes(this),
                ActionPreferences.getOverdueGraceMinutes(this),
                getString(ActionPreferences.shouldRecordSnoozeCount(this)
                        ? R.string.settings_status_enabled_short
                        : R.string.settings_status_disabled_short),
                formatEditMode(EditPreferences.getEditMode(this)),
                formatThemeMode(ThemePreferences.getThemeMode(this)),
                UserPreferences.getGroupingModeLabel(this),
                TaskCache.getCachedTaskCount(this),
                cachedAt == null ? "" : " · " + cachedAt
        ));
        if (latestError != null) {
            status.append('\n').append(getString(R.string.settings_status_last_error_logged));
        }
        statusText.setText(status.toString());
    }

    private void updateActiveFilterButton() {
        activeFilterButton.setText(UserPreferences.isActiveOnly(this)
                ? getString(R.string.settings_main_filter_active_only)
                : getString(R.string.settings_main_filter_all_tasks));
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
        state.setText(selected ? getString(R.string.common_active) : "");
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
        return getString(R.string.settings_not_selected_short);
        }
        String value = rawName.trim();
        if (value.startsWith(getString(R.string.source_folder_prefix))) {
            value = value.substring(getString(R.string.source_folder_prefix).length()).trim();
        } else if (value.startsWith(getString(R.string.source_file_prefix))) {
            value = value.substring(getString(R.string.source_file_prefix).length()).trim();
        }
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
            value = value.substring(colon + 1).trim();
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

