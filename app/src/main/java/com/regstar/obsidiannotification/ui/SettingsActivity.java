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
            addHeader(root, "Р В РЎСљР В Р’В°Р РЋР С“Р РЋРІР‚С™Р РЋР вЂљР В РЎвЂўР В РІвЂћвЂ“Р В РЎвЂќР В РЎвЂ", false);
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
            Button backButton = createSmallButton("Р Р†Р вЂљРІвЂћвЂ“");
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
                "Р В РЎвЂєР РЋР С“Р В Р вЂ¦Р В РЎвЂўР В Р вЂ Р В Р вЂ¦Р РЋРІР‚в„–Р В Р’Вµ",
                "Р В РЎвЂєР РЋРІР‚С™Р В РЎвЂќР РЋР вЂљР РЋРІР‚в„–Р РЋРІР‚С™Р В РЎвЂР В Р’Вµ Р В Р’В·Р В Р’В°Р В РўвЂР В Р’В°Р РЋРІР‚РЋ, Р В РЎвЂ“Р В Р’В»Р В Р’В°Р В Р вЂ Р В Р вЂ¦Р РЋРІР‚в„–Р В РІвЂћвЂ“ Р РЋР РЉР В РЎвЂќР РЋР вЂљР В Р’В°Р В Р вЂ¦, Р В РЎвЂ“Р РЋР вЂљР РЋРЎвЂњР В РЎвЂ”Р В РЎвЂ”Р В РЎвЂР РЋР вЂљР В РЎвЂўР В Р вЂ Р В РЎвЂќР В Р’В° Р В РЎвЂ Р РЋРІР‚С™Р В Р’ВµР В РЎВР В Р’В°.",
                () -> openSection(SECTION_BASIC)
        ), fullWidthWithBottomMargin());
        root.addView(createActionCard(
                "Р В Р’ВР РЋР С“Р РЋРІР‚С™Р В РЎвЂўР РЋРІР‚РЋР В Р вЂ¦Р В РЎвЂР В РЎвЂќР В РЎвЂ Р В Р’В·Р В Р’В°Р В РўвЂР В Р’В°Р РЋРІР‚РЋ",
                "Р В Р’В¤Р В Р’В°Р В РІвЂћвЂ“Р В Р’В»Р РЋРІР‚в„–, Р В РЎвЂ”Р В Р’В°Р В РЎвЂ”Р В РЎвЂќР В РЎвЂ Р В РЎвЂ Р РЋРЎвЂњР В РЎвЂ”Р РЋР вЂљР В Р’В°Р В Р вЂ Р В Р’В»Р В Р’ВµР В Р вЂ¦Р В РЎвЂР В Р’Вµ Р В РўвЂР В РЎвЂўР РЋР С“Р РЋРІР‚С™Р РЋРЎвЂњР В РЎвЂ”Р В РЎвЂўР В РЎВ Р РЋРІР‚РЋР В Р’ВµР РЋР вЂљР В Р’ВµР В Р’В· Android picker.",
                () -> openSection(SECTION_SOURCES)
        ), fullWidthWithBottomMargin());
        root.addView(createActionCard(
                "Р В Р в‚¬Р В Р вЂ Р В Р’ВµР В РўвЂР В РЎвЂўР В РЎВР В Р’В»Р В Р’ВµР В Р вЂ¦Р В РЎвЂР РЋР РЏ",
                "Exact alarms, Р В РЎвЂўР РЋРІР‚С™Р В Р’В»Р В РЎвЂўР В Р’В¶Р В РЎвЂР РЋРІР‚С™Р РЋР Р‰, Р В РЎвЂ”Р В РЎвЂўР В Р вЂ Р РЋРІР‚С™Р В РЎвЂўР РЋР вЂљР РЋРІР‚в„– Р В РЎвЂ Р В РўвЂР В Р’ВµР В РІвЂћвЂ“Р РЋР С“Р РЋРІР‚С™Р В Р вЂ Р В РЎвЂР РЋР РЏ Р В РЎвЂР В Р’В· Р РЋРЎвЂњР В Р вЂ Р В Р’ВµР В РўвЂР В РЎвЂўР В РЎВР В Р’В»Р В Р’ВµР В Р вЂ¦Р В РЎвЂР РЋР РЏ.",
                () -> openSection(SECTION_NOTIFICATIONS)
        ), fullWidthWithBottomMargin());
        root.addView(createActionCard(
                "Р В Р’В¤Р В РЎвЂўР РЋР вЂљР В РЎВР В Р’В°Р РЋРІР‚С™ Р В Р’В·Р В Р’В°Р В РўвЂР В Р’В°Р РЋРІР‚РЋ",
                "Р В РЎв„ўР В Р’В»Р РЋР вЂ№Р РЋРІР‚РЋР В Р’ВµР В Р вЂ Р РЋРІР‚в„–Р В Р’Вµ Р РЋР С“Р В Р’В»Р В РЎвЂўР В Р вЂ Р В Р’В° @due, @repeat, @tag, @priority Р В РЎвЂ @group.",
                () -> openSection(SECTION_FORMAT)
        ), fullWidthWithBottomMargin());
        root.addView(createActionCard(
                "Р В РЎСџР В РЎвЂўР В РЎвЂР РЋР С“Р В РЎвЂќ Р В Р’В·Р В Р’В°Р В РўвЂР В Р’В°Р РЋРІР‚РЋ Р В Р вЂ  Р РЋРІР‚С›Р В Р’В°Р В РІвЂћвЂ“Р В Р’В»Р В Р’В°Р РЋРІР‚В¦",
                "Р В РЎС™Р В Р’В°Р РЋР С“Р В РЎвЂќР В РЎвЂ, Р В РЎвЂР РЋР С“Р В РЎвЂќР В Р’В»Р РЋР вЂ№Р РЋРІР‚РЋР В Р’ВµР В Р вЂ¦Р В РЎвЂР РЋР РЏ Р В РЎвЂ Р В Р’В»Р В РЎвЂР В РЎВР В РЎвЂР РЋРІР‚С™ Р РЋР С“Р В РЎвЂќР В Р’В°Р В Р вЂ¦Р В РЎвЂР РЋР вЂљР В РЎвЂўР В Р вЂ Р В Р’В°Р В Р вЂ¦Р В РЎвЂР РЋР РЏ Р В РЎвЂ”Р В Р’В°Р В РЎвЂ”Р В РЎвЂўР В РЎвЂќ.",
                () -> openSection(SECTION_SCAN)
        ), fullWidthWithBottomMargin());
        root.addView(createActionCard(
                "Р В РЎСџР РЋР вЂљР В РЎвЂўР В РўвЂР В Р вЂ Р В РЎвЂР В Р вЂ¦Р РЋРЎвЂњР РЋРІР‚С™Р РЋРІР‚в„–Р В Р’Вµ / Р В РЎвЂєР РЋРІР‚С™Р В Р’В»Р В Р’В°Р В РўвЂР В РЎвЂќР В Р’В°",
                "Р В Р Р‹Р В Р’В»Р РЋРЎвЂњР В Р’В¶Р В Р’ВµР В Р’В±Р В Р вЂ¦Р В РЎвЂўР В Р’Вµ Р РЋР С“Р В РЎвЂўР РЋР С“Р РЋРІР‚С™Р В РЎвЂўР РЋР РЏР В Р вЂ¦Р В РЎвЂР В Р’Вµ, Р В РЎвЂ”Р В РЎвЂўР РЋР С“Р В Р’В»Р В Р’ВµР В РўвЂР В Р вЂ¦Р РЋР РЏР РЋР РЏ Р В РЎвЂўР РЋРІвЂљВ¬Р В РЎвЂР В Р’В±Р В РЎвЂќР В Р’В°, Р В РЎвЂќР РЋР РЉР РЋРІвЂљВ¬ Р В РЎвЂ debug actions.",
                () -> openSection(SECTION_ADVANCED)
        ), fullWidthWithBottomMargin());

        Button closeButton = createButton("Р В РІР‚вЂќР В Р’В°Р В РЎвЂќР РЋР вЂљР РЋРІР‚в„–Р РЋРІР‚С™Р РЋР Р‰");
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
            root.addView(createDescription("Р В Р’В Р В Р’В°Р В Р’В·Р В РўвЂР В Р’ВµР В Р’В» Р В Р вЂ¦Р В Р’В°Р РЋР С“Р РЋРІР‚С™Р РЋР вЂљР В РЎвЂўР В Р’ВµР В РЎвЂќ Р В Р вЂ¦Р В Р’Вµ Р В Р вЂ¦Р В Р’В°Р В РІвЂћвЂ“Р В РўвЂР В Р’ВµР В Р вЂ¦."), fullWidthWithBottomMargin());
        }
    }

    private String sectionTitle(String section) {
        if (SECTION_BASIC.equals(section)) {
            return "Р В РЎвЂєР РЋР С“Р В Р вЂ¦Р В РЎвЂўР В Р вЂ Р В Р вЂ¦Р РЋРІР‚в„–Р В Р’Вµ";
        }
        if (SECTION_SOURCES.equals(section)) {
            return "Р В Р’ВР РЋР С“Р РЋРІР‚С™Р В РЎвЂўР РЋРІР‚РЋР В Р вЂ¦Р В РЎвЂР В РЎвЂќР В РЎвЂ Р В Р’В·Р В Р’В°Р В РўвЂР В Р’В°Р РЋРІР‚РЋ";
        }
        if (SECTION_NOTIFICATIONS.equals(section)) {
            return "Р В Р в‚¬Р В Р вЂ Р В Р’ВµР В РўвЂР В РЎвЂўР В РЎВР В Р’В»Р В Р’ВµР В Р вЂ¦Р В РЎвЂР РЋР РЏ";
        }
        if (SECTION_FORMAT.equals(section)) {
            return "Р В Р’В¤Р В РЎвЂўР РЋР вЂљР В РЎВР В Р’В°Р РЋРІР‚С™ Р В Р’В·Р В Р’В°Р В РўвЂР В Р’В°Р РЋРІР‚РЋ";
        }
        if (SECTION_SCAN.equals(section)) {
            return "Р В РЎСџР В РЎвЂўР В РЎвЂР РЋР С“Р В РЎвЂќ Р В Р’В·Р В Р’В°Р В РўвЂР В Р’В°Р РЋРІР‚РЋ";
        }
        if (SECTION_ADVANCED.equals(section)) {
            return "Р В РЎСџР РЋР вЂљР В РЎвЂўР В РўвЂР В Р вЂ Р В РЎвЂР В Р вЂ¦Р РЋРЎвЂњР РЋРІР‚С™Р РЋРІР‚в„–Р В Р’Вµ";
        }
        return "Р В РЎСљР В Р’В°Р РЋР С“Р РЋРІР‚С™Р РЋР вЂљР В РЎвЂўР В РІвЂћвЂ“Р В РЎвЂќР В РЎвЂ";
    }

    private void openSection(String section) {
        Intent intent = new Intent(this, SettingsActivity.class);
        intent.putExtra(EXTRA_SECTION, section);
        startActivity(intent);
    }

    private void addBasicSettings(LinearLayout root) {
        root.addView(createDescription(
                "Р В РІР‚вЂќР В РўвЂР В Р’ВµР РЋР С“Р РЋР Р‰ Р В РЎвЂўР РЋР С“Р РЋРІР‚С™Р В Р’В°Р РЋР вЂ№Р РЋРІР‚С™Р РЋР С“Р РЋР РЏ Р РЋРІР‚С™Р В РЎвЂўР В Р’В»Р РЋР Р‰Р В РЎвЂќР В РЎвЂў Р В Р вЂ¦Р В Р’В°Р РЋР С“Р РЋРІР‚С™Р РЋР вЂљР В РЎвЂўР В РІвЂћвЂ“Р В РЎвЂќР В РЎвЂ, Р В РЎвЂќР В РЎвЂўР РЋРІР‚С™Р В РЎвЂўР РЋР вЂљР РЋРІР‚в„–Р В Р’Вµ Р В РЎВР В Р’ВµР В Р вЂ¦Р РЋР РЏР РЋР вЂ№Р РЋРІР‚С™ Р В Р’ВµР В Р’В¶Р В Р’ВµР В РўвЂР В Р вЂ¦Р В Р’ВµР В Р вЂ Р В Р вЂ¦Р РЋРІР‚в„–Р В РІвЂћвЂ“ Р РЋР С“Р РЋРІР‚В Р В Р’ВµР В Р вЂ¦Р В Р’В°Р РЋР вЂљР В РЎвЂР В РІвЂћвЂ“ Р РЋР вЂљР В Р’В°Р В Р’В±Р В РЎвЂўР РЋРІР‚С™Р РЋРІР‚в„–."
        ), fullWidthWithBottomMargin());

        root.addView(createSubsectionLabel("Р В РЎвЂєР РЋР С“Р В Р вЂ¦Р В РЎвЂўР В Р вЂ Р В Р вЂ¦Р В РЎвЂўР В РІвЂћвЂ“ Р РЋР С“Р В РЎвЂ”Р В РЎвЂўР РЋР С“Р В РЎвЂўР В Р’В± Р В РЎвЂўР РЋРІР‚С™Р В РЎвЂќР РЋР вЂљР РЋРІР‚в„–Р РЋРІР‚С™Р В РЎвЂР РЋР РЏ Р В Р’В·Р В Р’В°Р В РўвЂР В Р’В°Р РЋРІР‚РЋР В РЎвЂ"), fullWidth());
        String mode = EditPreferences.getEditMode(this);
        root.addView(createChoiceCard(
                "Р В Р’В§Р В Р’ВµР РЋР вЂљР В Р’ВµР В Р’В· UI",
                "Р В Р’В¤Р В РЎвЂўР РЋР вЂљР В РЎВР В Р’В° Р В РЎвЂўР В РўвЂР В Р вЂ¦Р В РЎвЂўР В РІвЂћвЂ“ Р В Р’В·Р В Р’В°Р В РўвЂР В Р’В°Р РЋРІР‚РЋР В РЎвЂ Р РЋР С“ preview Р В РЎвЂР РЋРІР‚С™Р В РЎвЂўР В РЎвЂ“Р В РЎвЂўР В Р вЂ Р В РЎвЂўР В РІвЂћвЂ“ markdown-Р РЋР С“Р РЋРІР‚С™Р РЋР вЂљР В РЎвЂўР В РЎвЂќР В РЎвЂ.",
                EditPreferences.MODE_UI.equals(mode),
                () -> setEditMode(EditPreferences.MODE_UI)
        ), fullWidthWithBottomMargin());
        root.addView(createChoiceCard(
                "Markdown-Р РЋРІР‚С›Р В Р’В°Р В РІвЂћвЂ“Р В Р’В»",
                "Р В РЎвЂєР РЋРІР‚С™Р В РЎвЂќР РЋР вЂљР РЋРІР‚в„–Р В Р вЂ Р В Р’В°Р РЋРІР‚С™Р РЋР Р‰ Р В РЎвЂ”Р В РЎвЂўР В Р’В»Р В Р вЂ¦Р РЋРІР‚в„–Р В РІвЂћвЂ“ Р В РЎвЂР РЋР С“Р РЋРІР‚В¦Р В РЎвЂўР В РўвЂР В Р вЂ¦Р РЋРІР‚в„–Р В РІвЂћвЂ“ Р РЋРІР‚С›Р В Р’В°Р В РІвЂћвЂ“Р В Р’В» Р В Р вЂ¦Р В Р’В° Р РЋР С“Р РЋРІР‚С™Р РЋР вЂљР В РЎвЂўР В РЎвЂќР В Р’Вµ Р В Р’В·Р В Р’В°Р В РўвЂР В Р’В°Р РЋРІР‚РЋР В РЎвЂ.",
                EditPreferences.MODE_MARKDOWN.equals(mode),
                () -> setEditMode(EditPreferences.MODE_MARKDOWN)
        ), fullWidthWithBottomMargin());

        root.addView(createSubsectionLabel("Р В РІР‚СљР В Р’В»Р В Р’В°Р В Р вЂ Р В Р вЂ¦Р РЋРІР‚в„–Р В РІвЂћвЂ“ Р РЋР РЉР В РЎвЂќР РЋР вЂљР В Р’В°Р В Р вЂ¦"), fullWidth());
        activeFilterButton = createButton("");
        activeFilterButton.setOnClickListener(view -> {
            UserPreferences.setActiveOnly(this, !UserPreferences.isActiveOnly(this));
            updateActiveFilterButton();
        });
        updateActiveFilterButton();
        root.addView(activeFilterButton, fullWidthWithBottomMargin());

        showSourceOnMainCheckbox = createCheckBox(
                "Р В РЎСџР В РЎвЂўР В РЎвЂќР В Р’В°Р В Р’В·Р РЋРІР‚в„–Р В Р вЂ Р В Р’В°Р РЋРІР‚С™Р РЋР Р‰ Р В РЎвЂР РЋР С“Р РЋРІР‚С™Р В РЎвЂўР РЋРІР‚РЋР В Р вЂ¦Р В РЎвЂР В РЎвЂќ Р В Р вЂ¦Р В Р’В° Р В РЎвЂ“Р В Р’В»Р В Р’В°Р В Р вЂ Р В Р вЂ¦Р В РЎвЂўР В РЎВ Р РЋР РЉР В РЎвЂќР РЋР вЂљР В Р’В°Р В Р вЂ¦Р В Р’Вµ",
                UserPreferences.shouldShowSourceOnMain(this)
        );
        showSourceOnMainCheckbox.setOnCheckedChangeListener((button, checked) ->
                UserPreferences.setShowSourceOnMain(this, checked));
        root.addView(showSourceOnMainCheckbox, fullWidthWithBottomMargin());

        privateMarkerInput = addKeywordInput(
                root,
                "Р В РЎСџР РЋР вЂљР В РЎвЂР В Р вЂ Р В Р’В°Р РЋРІР‚С™Р В Р вЂ¦Р В Р’В°Р РЋР РЏ Р В РЎвЂ“Р РЋР вЂљР РЋРЎвЂњР В РЎвЂ”Р В РЎвЂ”Р В Р’В° Р В РЎвЂР В Р’В»Р В РЎвЂ Р РЋРІР‚С™Р В Р’ВµР В РЎвЂ“",
                UserPreferences.getPrivateMarker(this)
        );
        root.addView(createDescription(
                "Р В РЎСљР В Р’В°Р В РЎвЂ”Р РЋР вЂљР В РЎвЂР В РЎВР В Р’ВµР РЋР вЂљ private: Р В Р’В·Р В Р’В°Р В РўвЂР В Р’В°Р РЋРІР‚РЋР В РЎвЂ Р РЋР С“ @group(private) Р В РЎвЂР В Р’В»Р В РЎвЂ #private Р РЋР С“Р В РЎвЂќР РЋР вЂљР РЋРІР‚в„–Р В Р вЂ Р В Р’В°Р РЋР вЂ№Р РЋРІР‚С™Р РЋР С“Р РЋР РЏ Р В Р вЂ  Р вЂ™Р’В«Р В РІР‚в„ўР РЋР С“Р В Р’Вµ Р В РЎвЂ“Р РЋР вЂљР РЋРЎвЂњР В РЎвЂ”Р В РЎвЂ”Р РЋРІР‚в„–Р вЂ™Р’В». Р В РЎСџР РЋРЎвЂњР РЋР С“Р РЋРІР‚С™Р В РЎвЂўР В Р’Вµ Р В Р’В·Р В Р вЂ¦Р В Р’В°Р РЋРІР‚РЋР В Р’ВµР В Р вЂ¦Р В РЎвЂР В Р’Вµ Р В РЎвЂўР РЋРІР‚С™Р В РЎвЂќР В Р’В»Р РЋР вЂ№Р РЋРІР‚РЋР В Р’В°Р В Р’ВµР РЋРІР‚С™ Р РЋР С“Р В РЎвЂќР РЋР вЂљР РЋРІР‚в„–Р РЋРІР‚С™Р В РЎвЂР В Р’Вµ."
        ), fullWidthWithBottomMargin());
        Button savePrivateMarkerButton = createButton("Р В Р Р‹Р В РЎвЂўР РЋРІР‚В¦Р РЋР вЂљР В Р’В°Р В Р вЂ¦Р В РЎвЂР РЋРІР‚С™Р РЋР Р‰ Р В РЎвЂ”Р РЋР вЂљР В РЎвЂР В Р вЂ Р В Р’В°Р РЋРІР‚С™Р В Р вЂ¦Р РЋРІР‚в„–Р В РІвЂћвЂ“ Р В РЎВР В Р’В°Р РЋР вЂљР В РЎвЂќР В Р’ВµР РЋР вЂљ");
        savePrivateMarkerButton.setOnClickListener(view -> savePrivateMarker());
        root.addView(savePrivateMarkerButton, fullWidthWithBottomMargin());

        root.addView(createSubsectionLabel("Р В РІР‚СљР РЋР вЂљР РЋРЎвЂњР В РЎвЂ”Р В РЎвЂ”Р В РЎвЂР РЋР вЂљР В РЎвЂўР В Р вЂ Р В РЎвЂќР В Р’В°"), fullWidth());
        String groupingMode = UserPreferences.getGroupingMode(this);
        root.addView(createChoiceCard(
                "Р В Р Р‹Р В РЎВР В Р’ВµР РЋРІвЂљВ¬Р В Р’В°Р В Р вЂ¦Р В Р вЂ¦Р В Р’В°Р РЋР РЏ",
                "Р В Р’ВР РЋР С“Р В РЎвЂ”Р В РЎвЂўР В Р’В»Р РЋР Р‰Р В Р’В·Р В РЎвЂўР В Р вЂ Р В Р’В°Р РЋРІР‚С™Р РЋР Р‰ @group, Р В РЎвЂР В Р вЂ¦Р В Р’В°Р РЋРІР‚РЋР В Р’Вµ Р В РЎвЂ”Р В Р’ВµР РЋР вЂљР В Р вЂ Р РЋРІР‚в„–Р В РІвЂћвЂ“ Р РЋРІР‚С™Р В Р’ВµР В РЎвЂ“ Р В РЎвЂР В Р’В»Р В РЎвЂ Р В РЎвЂР В РЎВР РЋР РЏ Р РЋРІР‚С›Р В Р’В°Р В РІвЂћвЂ“Р В Р’В»Р В Р’В°.",
                UserPreferences.GROUPING_SMART.equals(groupingMode),
                () -> setGroupingMode(UserPreferences.GROUPING_SMART)
        ), fullWidthWithBottomMargin());
        root.addView(createChoiceCard(
                "Р В РЎСџР В РЎвЂў @group",
                "Р В РЎСџР В РЎвЂўР В РЎвЂќР В Р’В°Р В Р’В·Р РЋРІР‚в„–Р В Р вЂ Р В Р’В°Р РЋРІР‚С™Р РЋР Р‰ Р В РЎвЂ“Р РЋР вЂљР РЋРЎвЂњР В РЎвЂ”Р В РЎвЂ”Р РЋРІР‚в„– Р РЋРІР‚С™Р В РЎвЂўР В Р’В»Р РЋР Р‰Р В РЎвЂќР В РЎвЂў Р В РЎвЂР В Р’В· @group(...).",
                UserPreferences.GROUPING_GROUP.equals(groupingMode),
                () -> setGroupingMode(UserPreferences.GROUPING_GROUP)
        ), fullWidthWithBottomMargin());
        root.addView(createChoiceCard(
                "Р В РЎСџР В РЎвЂў Р РЋРІР‚С™Р В Р’ВµР В РЎвЂ“Р В Р’В°Р В РЎВ",
                "Р В Р’ВР РЋР С“Р В РЎвЂ”Р В РЎвЂўР В Р’В»Р РЋР Р‰Р В Р’В·Р В РЎвЂўР В Р вЂ Р В Р’В°Р РЋРІР‚С™Р РЋР Р‰ Р В РЎвЂ”Р В Р’ВµР РЋР вЂљР В Р вЂ Р РЋРІР‚в„–Р В РІвЂћвЂ“ Р РЋРІР‚С™Р В Р’ВµР В РЎвЂ“ Р В Р’В·Р В Р’В°Р В РўвЂР В Р’В°Р РЋРІР‚РЋР В РЎвЂ Р В РЎвЂќР В Р’В°Р В РЎвЂќ Р В РЎвЂ“Р РЋР вЂљР РЋРЎвЂњР В РЎвЂ”Р В РЎвЂ”Р РЋРЎвЂњ.",
                UserPreferences.GROUPING_TAG.equals(groupingMode),
                () -> setGroupingMode(UserPreferences.GROUPING_TAG)
        ), fullWidthWithBottomMargin());
        root.addView(createChoiceCard(
                "Р В РЎСџР В РЎвЂў Р РЋРІР‚С›Р В Р’В°Р В РІвЂћвЂ“Р В Р’В»Р В Р’В°Р В РЎВ",
                "Р В РІР‚СљР РЋР вЂљР РЋРЎвЂњР В РЎвЂ”Р В РЎвЂ”Р В РЎвЂР РЋР вЂљР В РЎвЂўР В Р вЂ Р В Р’В°Р РЋРІР‚С™Р РЋР Р‰ Р В Р’В·Р В Р’В°Р В РўвЂР В Р’В°Р РЋРІР‚РЋР В РЎвЂ Р В РЎвЂ”Р В РЎвЂў Р В РЎвЂР В РЎВР В Р’ВµР В Р вЂ¦Р В РЎвЂ markdown-Р РЋРІР‚С›Р В Р’В°Р В РІвЂћвЂ“Р В Р’В»Р В Р’В°.",
                UserPreferences.GROUPING_FILE.equals(groupingMode),
                () -> setGroupingMode(UserPreferences.GROUPING_FILE)
        ), fullWidthWithBottomMargin());

        root.addView(createSubsectionLabel("Р В РЎС›Р В Р’ВµР В РЎВР В Р’В°"), fullWidth());
        String themeMode = ThemePreferences.getThemeMode(this);
        root.addView(createChoiceCard(
                "Р В Р Р‹Р В РЎвЂР РЋР С“Р РЋРІР‚С™Р В Р’ВµР В РЎВР В Р вЂ¦Р В Р’В°Р РЋР РЏ",
                "Р В Р Р‹Р В Р’В»Р В Р’ВµР В РўвЂР В РЎвЂўР В Р вЂ Р В Р’В°Р РЋРІР‚С™Р РЋР Р‰ Р В Р вЂ¦Р В Р’В°Р РЋР С“Р РЋРІР‚С™Р РЋР вЂљР В РЎвЂўР В РІвЂћвЂ“Р В РЎвЂќР В Р’Вµ Р РЋРІР‚С™Р В Р’ВµР В РЎВР РЋРІР‚в„– Android.",
                ThemePreferences.MODE_SYSTEM.equals(themeMode),
                () -> setThemeMode(ThemePreferences.MODE_SYSTEM)
        ), fullWidthWithBottomMargin());
        root.addView(createChoiceCard(
                "Р В Р Р‹Р В Р вЂ Р В Р’ВµР РЋРІР‚С™Р В Р’В»Р В Р’В°Р РЋР РЏ",
                "Р В РІР‚в„ўР РЋР С“Р В Р’ВµР В РЎвЂ“Р В РўвЂР В Р’В° Р В РЎвЂР РЋР С“Р В РЎвЂ”Р В РЎвЂўР В Р’В»Р РЋР Р‰Р В Р’В·Р В РЎвЂўР В Р вЂ Р В Р’В°Р РЋРІР‚С™Р РЋР Р‰ Р РЋР С“Р В Р вЂ Р В Р’ВµР РЋРІР‚С™Р В Р’В»Р РЋРЎвЂњР РЋР вЂ№ Р РЋРІР‚С™Р В Р’ВµР В РЎВР РЋРЎвЂњ.",
                ThemePreferences.MODE_LIGHT.equals(themeMode),
                () -> setThemeMode(ThemePreferences.MODE_LIGHT)
        ), fullWidthWithBottomMargin());
        root.addView(createChoiceCard(
                "Р В РЎС›Р В Р’ВµР В РЎВР В Р вЂ¦Р В Р’В°Р РЋР РЏ",
                "Р В РІР‚в„ўР РЋР С“Р В Р’ВµР В РЎвЂ“Р В РўвЂР В Р’В° Р В РЎвЂР РЋР С“Р В РЎвЂ”Р В РЎвЂўР В Р’В»Р РЋР Р‰Р В Р’В·Р В РЎвЂўР В Р вЂ Р В Р’В°Р РЋРІР‚С™Р РЋР Р‰ Р РЋРІР‚С™Р В Р’ВµР В РЎВР В Р вЂ¦Р РЋРЎвЂњР РЋР вЂ№ Р РЋРІР‚С™Р В Р’ВµР В РЎВР РЋРЎвЂњ.",
                ThemePreferences.MODE_DARK.equals(themeMode),
                () -> setThemeMode(ThemePreferences.MODE_DARK)
        ), fullWidthWithBottomMargin());
    }

    private void addSourceSettings(LinearLayout root) {
        boolean internalSelected =
                TaskSourceManager.getStorageMode(this) == TaskStorageMode.INTERNAL_MARKDOWN_STORAGE;
        boolean hasExternalSources = TaskSourceManager.hasExternalSources(this);
        String externalSubtitle = hasExternalSources
                ? "Р В РЎС›Р В Р’ВµР В РЎвЂќР РЋРЎвЂњР РЋРІР‚В°Р В РЎвЂР В РІвЂћвЂ“ Р В РЎвЂР РЋР С“Р РЋРІР‚С™Р В РЎвЂўР РЋРІР‚РЋР В Р вЂ¦Р В РЎвЂР В РЎвЂќ: " + compactName(NoteStore.externalSourceLabel(this))
                : "Р В Р’В¤Р В Р’В°Р В РІвЂћвЂ“Р В Р’В»Р РЋРІР‚в„– Р В РЎвЂ Р В РЎвЂ”Р В Р’В°Р В РЎвЂ”Р В РЎвЂќР В РЎвЂ Р РЋРІР‚РЋР В Р’ВµР РЋР вЂљР В Р’ВµР В Р’В· Android picker. Р В РЎСџР В РЎвЂўР В РўвЂР РЋРІР‚В¦Р В РЎвЂўР В РўвЂР В РЎвЂР РЋРІР‚С™ Р В РўвЂР В Р’В»Р РЋР РЏ Obsidian, Syncthing Р В РЎвЂ Р В РЎвЂўР В Р’В±Р РЋРІР‚в„–Р РЋРІР‚РЋР В Р вЂ¦Р РЋРІР‚в„–Р РЋРІР‚В¦ markdown-Р РЋРІР‚С›Р В Р’В°Р В РІвЂћвЂ“Р В Р’В»Р В РЎвЂўР В Р вЂ .";
        root.addView(createDescription(
                "Р В РІР‚в„ўР РЋРІР‚в„–Р В Р’В±Р В Р’ВµР РЋР вЂљР В РЎвЂР РЋРІР‚С™Р В Р’Вµ, Р В РЎвЂ“Р В РўвЂР В Р’Вµ Р В РЎвЂ”Р РЋР вЂљР В РЎвЂР В Р’В»Р В РЎвЂўР В Р’В¶Р В Р’ВµР В Р вЂ¦Р В РЎвЂР В Р’Вµ Р В Р’В±Р РЋРЎвЂњР В РўвЂР В Р’ВµР РЋРІР‚С™ Р РЋРІР‚РЋР В РЎвЂР РЋРІР‚С™Р В Р’В°Р РЋРІР‚С™Р РЋР Р‰ Р В РЎвЂ Р РЋРІР‚В¦Р РЋР вЂљР В Р’В°Р В Р вЂ¦Р В РЎвЂР РЋРІР‚С™Р РЋР Р‰ markdown-Р В Р’В·Р В Р’В°Р В РўвЂР В Р’В°Р РЋРІР‚РЋР В РЎвЂ: Р В Р вЂ Р В Р вЂ¦Р РЋРЎвЂњР РЋРІР‚С™Р РЋР вЂљР В РЎвЂ Р В РЎвЂ”Р РЋР вЂљР В РЎвЂР В Р’В»Р В РЎвЂўР В Р’В¶Р В Р’ВµР В Р вЂ¦Р В РЎвЂР РЋР РЏ Р В РЎвЂР В Р’В»Р В РЎвЂ Р В Р вЂ Р В РЎвЂў Р В Р вЂ Р В Р вЂ¦Р В Р’ВµР РЋРІвЂљВ¬Р В Р вЂ¦Р В РЎвЂР РЋРІР‚В¦ Р РЋРІР‚С›Р В Р’В°Р В РІвЂћвЂ“Р В Р’В»Р В Р’В°Р РЋРІР‚В¦."
        ), fullWidthWithBottomMargin());
        root.addView(createChoiceCard(
                "Р В РІР‚в„ўР РЋР С“Р РЋРІР‚С™Р РЋР вЂљР В РЎвЂўР В Р’ВµР В Р вЂ¦Р В Р вЂ¦Р В РЎвЂўР В Р’Вµ Р РЋРІР‚В¦Р РЋР вЂљР В Р’В°Р В Р вЂ¦Р В РЎвЂР В Р’В»Р В РЎвЂР РЋРІР‚В°Р В Р’Вµ",
                "Р В РІР‚С”Р В РЎвЂўР В РЎвЂќР В Р’В°Р В Р’В»Р РЋР Р‰Р В Р вЂ¦Р РЋРІР‚в„–Р В Р’Вµ markdown-Р РЋРІР‚С›Р В Р’В°Р В РІвЂћвЂ“Р В Р’В»Р РЋРІР‚в„– Р В Р вЂ Р В Р вЂ¦Р РЋРЎвЂњР РЋРІР‚С™Р РЋР вЂљР В РЎвЂ Р В РЎвЂ”Р РЋР вЂљР В РЎвЂР В Р’В»Р В РЎвЂўР В Р’В¶Р В Р’ВµР В Р вЂ¦Р В РЎвЂР РЋР РЏ. Р В РЎСљР В Р’Вµ Р РЋРІР‚С™Р РЋР вЂљР В Р’ВµР В Р’В±Р РЋРЎвЂњР В Р’ВµР РЋРІР‚С™ Р В Р вЂ Р РЋРІР‚в„–Р В Р’В±Р В РЎвЂўР РЋР вЂљР В Р’В° Р В Р вЂ Р В Р вЂ¦Р В Р’ВµР РЋРІвЂљВ¬Р В Р вЂ¦Р В Р’ВµР В РІвЂћвЂ“ Р В РЎвЂ”Р В Р’В°Р В РЎвЂ”Р В РЎвЂќР В РЎвЂ.",
                internalSelected,
                this::switchToInternalStorage
        ), fullWidthWithBottomMargin());
        root.addView(createChoiceCard(
                "Р В РІР‚в„ўР В Р вЂ¦Р В Р’ВµР РЋРІвЂљВ¬Р В Р вЂ¦Р В РЎвЂР В Р’Вµ markdown-Р РЋРІР‚С›Р В Р’В°Р В РІвЂћвЂ“Р В Р’В»Р РЋРІР‚в„–",
                externalSubtitle,
                !internalSelected,
                this::activateOrSelectExternalStorage
        ), fullWidthWithBottomMargin());
        root.addView(createActionCard(
                "Р В Р в‚¬Р В РЎвЂ”Р РЋР вЂљР В Р’В°Р В Р вЂ Р В Р’В»Р В Р’ВµР В Р вЂ¦Р В РЎвЂР В Р’Вµ Р В Р вЂ Р В Р вЂ¦Р В Р’ВµР РЋРІвЂљВ¬Р В Р вЂ¦Р В РЎвЂР В РЎВР В РЎвЂ Р В РЎвЂР РЋР С“Р РЋРІР‚С™Р В РЎвЂўР РЋРІР‚РЋР В Р вЂ¦Р В РЎвЂР В РЎвЂќР В Р’В°Р В РЎВР В РЎвЂ",
                "Р В РІР‚в„ўР РЋРІР‚в„–Р В Р’В±Р РЋР вЂљР В Р’В°Р РЋРІР‚С™Р РЋР Р‰, Р В РўвЂР В РЎвЂўР В Р’В±Р В Р’В°Р В Р вЂ Р В РЎвЂР РЋРІР‚С™Р РЋР Р‰ Р В РЎвЂР В Р’В»Р В РЎвЂ Р В РЎвЂўР РЋРІР‚РЋР В РЎвЂР РЋР С“Р РЋРІР‚С™Р В РЎвЂР РЋРІР‚С™Р РЋР Р‰ Р В Р вЂ Р В Р вЂ¦Р В Р’ВµР РЋРІвЂљВ¬Р В Р вЂ¦Р В РЎвЂР В Р’Вµ markdown-Р РЋРІР‚С›Р В Р’В°Р В РІвЂћвЂ“Р В Р’В»Р РЋРІР‚в„– Р В РЎвЂ Р В РЎвЂ”Р В Р’В°Р В РЎвЂ”Р В РЎвЂќР В РЎвЂ.",
                this::openSourceManagement
        ), fullWidthWithBottomMargin());
        root.addView(createDescription(
                "Р В Р Р‹Р В РЎВР В Р’ВµР В Р вЂ¦Р В Р’В° Р РЋР вЂљР В Р’ВµР В Р’В¶Р В РЎвЂР В РЎВР В Р’В° Р В Р вЂ¦Р В Р’Вµ Р В РЎвЂ”Р В Р’ВµР РЋР вЂљР В Р’ВµР В Р вЂ¦Р В РЎвЂўР РЋР С“Р В РЎвЂР РЋРІР‚С™ Р В Р’В·Р В Р’В°Р В РўвЂР В Р’В°Р РЋРІР‚РЋР В РЎвЂ Р В Р’В°Р В Р вЂ Р РЋРІР‚С™Р В РЎвЂўР В РЎВР В Р’В°Р РЋРІР‚С™Р В РЎвЂР РЋРІР‚РЋР В Р’ВµР РЋР С“Р В РЎвЂќР В РЎвЂ. Р В РІР‚СћР РЋР С“Р В Р’В»Р В РЎвЂ Р В РЎвЂ”Р В Р’ВµР РЋР вЂљР В Р’ВµР В Р вЂ¦Р В РЎвЂўР РЋР С“ Р В Р вЂ¦Р РЋРЎвЂњР В Р’В¶Р В Р’ВµР В Р вЂ¦, markdown-Р РЋРІР‚С›Р В Р’В°Р В РІвЂћвЂ“Р В Р’В»Р РЋРІР‚в„– Р В РЎвЂ”Р В РЎвЂўР В РЎвЂќР В Р’В° Р В Р вЂ¦Р РЋРЎвЂњР В Р’В¶Р В Р вЂ¦Р В РЎвЂў Р В РЎвЂќР В РЎвЂўР В РЎвЂ”Р В РЎвЂР РЋР вЂљР В РЎвЂўР В Р вЂ Р В Р’В°Р РЋРІР‚С™Р РЋР Р‰ Р В Р вЂ Р РЋР вЂљР РЋРЎвЂњР РЋРІР‚РЋР В Р вЂ¦Р РЋРЎвЂњР РЋР вЂ№."
        ), fullWidthWithBottomMargin());
    }

    private void addNotificationSettings(LinearLayout root) {
        root.addView(createDescription(
                "Р В РЎСџР В Р’В°Р РЋР вЂљР В Р’В°Р В РЎВР В Р’ВµР РЋРІР‚С™Р РЋР вЂљР РЋРІР‚в„– Р РЋРІР‚С™Р В РЎвЂўР РЋРІР‚РЋР В Р вЂ¦Р В РЎвЂўР РЋР С“Р РЋРІР‚С™Р В РЎвЂ, Р В РЎвЂўР РЋРІР‚С™Р В Р’В»Р В РЎвЂўР В Р’В¶Р В Р’ВµР В Р вЂ¦Р В РЎвЂР РЋР РЏ Р В РЎвЂ Р В РўвЂР В Р’ВµР В РІвЂћвЂ“Р РЋР С“Р РЋРІР‚С™Р В Р вЂ Р В РЎвЂР В РІвЂћвЂ“ Р В РЎвЂР В Р’В· Р РЋР С“Р В РЎвЂР РЋР С“Р РЋРІР‚С™Р В Р’ВµР В РЎВР В Р вЂ¦Р РЋРІР‚в„–Р РЋРІР‚В¦ Р РЋРЎвЂњР В Р вЂ Р В Р’ВµР В РўвЂР В РЎвЂўР В РЎВР В Р’В»Р В Р’ВµР В Р вЂ¦Р В РЎвЂР В РІвЂћвЂ“."
        ), fullWidthWithBottomMargin());
        root.addView(createActionCard(
                "Exact alarms",
                ReminderScheduler.canScheduleExactAlarms(this)
                        ? "Р В РЎС›Р В РЎвЂўР РЋРІР‚РЋР В Р вЂ¦Р РЋРІР‚в„–Р В Р’Вµ Р В Р вЂ¦Р В Р’В°Р В РЎвЂ”Р В РЎвЂўР В РЎВР В РЎвЂР В Р вЂ¦Р В Р’В°Р В Р вЂ¦Р В РЎвЂР РЋР РЏ Р РЋР вЂљР В Р’В°Р В Р’В·Р РЋР вЂљР В Р’ВµР РЋРІвЂљВ¬Р В Р’ВµР В Р вЂ¦Р РЋРІР‚в„–."
                        : "Р В Р’В Р В Р’В°Р В Р’В·Р РЋР вЂљР В Р’ВµР РЋРІвЂљВ¬Р В РЎвЂР РЋРІР‚С™Р В Р’Вµ Р РЋРІР‚С™Р В РЎвЂўР РЋРІР‚РЋР В Р вЂ¦Р РЋРІР‚в„–Р В Р’Вµ Р В Р вЂ¦Р В Р’В°Р В РЎвЂ”Р В РЎвЂўР В РЎВР В РЎвЂР В Р вЂ¦Р В Р’В°Р В Р вЂ¦Р В РЎвЂР РЋР РЏ Р В Р вЂ  Р РЋР С“Р В РЎвЂР РЋР С“Р РЋРІР‚С™Р В Р’ВµР В РЎВР В Р вЂ¦Р РЋРІР‚в„–Р РЋРІР‚В¦ Р В Р вЂ¦Р В Р’В°Р РЋР С“Р РЋРІР‚С™Р РЋР вЂљР В РЎвЂўР В РІвЂћвЂ“Р В РЎвЂќР В Р’В°Р РЋРІР‚В¦.",
                this::requestExactAlarmPermission
        ), fullWidthWithBottomMargin());
        addNotificationActionSettings(root);
        root.addView(createActionCard(
                "Р В РЎСџР В Р’ВµР РЋР вЂљР В Р’ВµР В РЎвЂ”Р В Р’В»Р В Р’В°Р В Р вЂ¦Р В РЎвЂР РЋР вЂљР В РЎвЂўР В Р вЂ Р В Р’В°Р РЋРІР‚С™Р РЋР Р‰ Р В Р вЂ Р РЋР С“Р В Р’Вµ",
                "Р В РЎСџР В Р’ВµР РЋР вЂљР В Р’ВµР РЋРІР‚РЋР В РЎвЂР РЋРІР‚С™Р В Р’В°Р РЋРІР‚С™Р РЋР Р‰ Р В РЎвЂР РЋР С“Р РЋРІР‚С™Р В РЎвЂўР РЋРІР‚РЋР В Р вЂ¦Р В РЎвЂР В РЎвЂќ Р В РЎвЂ Р В Р’В·Р В Р’В°Р В Р вЂ¦Р В РЎвЂўР В Р вЂ Р В РЎвЂў Р В РЎвЂ”Р В РЎвЂўР РЋР С“Р РЋРІР‚С™Р В Р’В°Р В Р вЂ Р В РЎвЂР РЋРІР‚С™Р РЋР Р‰ Р В Р’В°Р В РЎвЂќР РЋРІР‚С™Р В РЎвЂР В Р вЂ Р В Р вЂ¦Р РЋРІР‚в„–Р В Р’Вµ Р РЋРЎвЂњР В Р вЂ Р В Р’ВµР В РўвЂР В РЎвЂўР В РЎВР В Р’В»Р В Р’ВµР В Р вЂ¦Р В РЎвЂР РЋР РЏ.",
                this::rescheduleAll
        ), fullWidthWithBottomMargin());
        root.addView(createActionCard(
                "Р В РЎС›Р В Р’ВµР РЋР С“Р РЋРІР‚С™Р В РЎвЂўР В Р вЂ Р В РЎвЂўР В Р’Вµ Р РЋРЎвЂњР В Р вЂ Р В Р’ВµР В РўвЂР В РЎвЂўР В РЎВР В Р’В»Р В Р’ВµР В Р вЂ¦Р В РЎвЂР В Р’Вµ",
                "Р В РЎСџР РЋР вЂљР В РЎвЂўР В Р вЂ Р В Р’ВµР РЋР вЂљР В РЎвЂР РЋРІР‚С™Р РЋР Р‰ Р В Р’В·Р В Р вЂ Р РЋРЎвЂњР В РЎвЂќ, Р В Р вЂ Р В РЎвЂР В Р’В±Р РЋР вЂљР В Р’В°Р РЋРІР‚В Р В РЎвЂР РЋР вЂ№ Р В РЎвЂ Р РЋР вЂљР В Р’В°Р В Р’В·Р РЋР вЂљР В Р’ВµР РЋРІвЂљВ¬Р В Р’ВµР В Р вЂ¦Р В РЎвЂР В Р’Вµ Р РЋРЎвЂњР В Р вЂ Р В Р’ВµР В РўвЂР В РЎвЂўР В РЎВР В Р’В»Р В Р’ВµР В Р вЂ¦Р В РЎвЂР В РІвЂћвЂ“.",
                this::sendTestNotification
        ), fullWidthWithBottomMargin());
    }

    private void addAdvancedSettings(LinearLayout root) {
        LinearLayout summaryCard = createSettingsCard();
        TextView summaryTitle = new TextView(this);
        summaryTitle.setText("Р В Р Р‹Р В РЎвЂўР РЋР С“Р РЋРІР‚С™Р В РЎвЂўР РЋР РЏР В Р вЂ¦Р В РЎвЂР В Р’Вµ");
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
        editTitle.setText("Р В РЎСџР В РЎвЂўР В Р вЂ Р В Р’ВµР В РўвЂР В Р’ВµР В Р вЂ¦Р В РЎвЂР В Р’Вµ");
        editTitle.setTextSize(18);
        editTitle.setTextColor(getColor(R.color.text_primary));
        editTitle.setPadding(0, dp(12), 0, dp(6));
        root.addView(editTitle, fullWidth());

        TextView editDescription = new TextView(this);
        editDescription.setText("Р В РЎвЂєР РЋР С“Р В Р вЂ¦Р В РЎвЂўР В Р вЂ Р В Р вЂ¦Р В РЎвЂўР В РІвЂћвЂ“ Р РЋР С“Р В РЎвЂ”Р В РЎвЂўР РЋР С“Р В РЎвЂўР В Р’В± Р В РЎвЂўР РЋРІР‚С™Р В РЎвЂќР РЋР вЂљР РЋРІР‚в„–Р РЋРІР‚С™Р В РЎвЂР РЋР РЏ Р В Р’В·Р В Р’В°Р В РўвЂР В Р’В°Р РЋРІР‚РЋР В РЎвЂ Р В РЎвЂР В Р’В· Р РЋР С“Р В РЎвЂ”Р В РЎвЂР РЋР С“Р В РЎвЂќР В Р’В° Р В РЎвЂ Р В Р’В±Р В Р’В°Р В Р’В·Р В РЎвЂўР В Р вЂ Р В РЎвЂўР В Р’Вµ Р В РЎвЂ”Р В РЎвЂўР В Р вЂ Р В Р’ВµР В РўвЂР В Р’ВµР В Р вЂ¦Р В РЎвЂР В Р’Вµ Р В РЎвЂ“Р В Р’В»Р В Р’В°Р В Р вЂ Р В Р вЂ¦Р В РЎвЂўР В РЎвЂ“Р В РЎвЂў Р РЋР РЉР В РЎвЂќР РЋР вЂљР В Р’В°Р В Р вЂ¦Р В Р’В°.");
        editDescription.setTextSize(14);
        editDescription.setTextColor(getColor(R.color.text_secondary));
        editDescription.setPadding(0, 0, 0, dp(8));
        root.addView(editDescription, fullWidth());

        root.addView(createSubsectionLabel("Р В РЎвЂєР РЋР С“Р В Р вЂ¦Р В РЎвЂўР В Р вЂ Р В Р вЂ¦Р В РЎвЂўР В РІвЂћвЂ“ Р РЋР С“Р В РЎвЂ”Р В РЎвЂўР РЋР С“Р В РЎвЂўР В Р’В± Р РЋР вЂљР В Р’ВµР В РўвЂР В Р’В°Р В РЎвЂќР РЋРІР‚С™Р В РЎвЂР РЋР вЂљР В РЎвЂўР В Р вЂ Р В Р’В°Р В Р вЂ¦Р В РЎвЂР РЋР РЏ"), fullWidth());
        String mode = EditPreferences.getEditMode(this);
        root.addView(createChoiceCard(
                "Р В Р’В§Р В Р’ВµР РЋР вЂљР В Р’ВµР В Р’В· UI",
                "Р В Р’В¤Р В РЎвЂўР РЋР вЂљР В РЎВР В Р’В° Р В РЎвЂўР В РўвЂР В Р вЂ¦Р В РЎвЂўР В РІвЂћвЂ“ Р В Р’В·Р В Р’В°Р В РўвЂР В Р’В°Р РЋРІР‚РЋР В РЎвЂ Р РЋР С“ preview Р В РЎвЂР РЋРІР‚С™Р В РЎвЂўР В РЎвЂ“Р В РЎвЂўР В Р вЂ Р В РЎвЂўР В РІвЂћвЂ“ markdown-Р РЋР С“Р РЋРІР‚С™Р РЋР вЂљР В РЎвЂўР В РЎвЂќР В РЎвЂ.",
                EditPreferences.MODE_UI.equals(mode),
                () -> setEditMode(EditPreferences.MODE_UI)
        ), fullWidthWithBottomMargin());
        root.addView(createChoiceCard(
                "Markdown-Р РЋРІР‚С›Р В Р’В°Р В РІвЂћвЂ“Р В Р’В»",
                "Р В РЎСџР В РЎвЂўР В Р’В»Р В Р вЂ¦Р РЋРІР‚в„–Р В РІвЂћвЂ“ Р В РЎвЂР РЋР С“Р РЋРІР‚В¦Р В РЎвЂўР В РўвЂР В Р вЂ¦Р РЋРІР‚в„–Р В РІвЂћвЂ“ Р РЋРІР‚С›Р В Р’В°Р В РІвЂћвЂ“Р В Р’В» Р РЋР С“ Р В РЎвЂ”Р В Р’ВµР РЋР вЂљР В Р’ВµР РЋРІР‚В¦Р В РЎвЂўР В РўвЂР В РЎвЂўР В РЎВ Р В РЎвЂќ Р РЋР С“Р РЋРІР‚С™Р РЋР вЂљР В РЎвЂўР В РЎвЂќР В Р’Вµ Р В Р вЂ Р РЋРІР‚в„–Р В Р’В±Р РЋР вЂљР В Р’В°Р В Р вЂ¦Р В Р вЂ¦Р В РЎвЂўР В РІвЂћвЂ“ Р В Р’В·Р В Р’В°Р В РўвЂР В Р’В°Р РЋРІР‚РЋР В РЎвЂ.",
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

        root.addView(createSubsectionLabel("Р В РІР‚СљР РЋР вЂљР РЋРЎвЂњР В РЎвЂ”Р В РЎвЂ”Р В РЎвЂР РЋР вЂљР В РЎвЂўР В Р вЂ Р В РЎвЂќР В Р’В° Р В Р вЂ¦Р В Р’В° Р В РЎвЂ“Р В Р’В»Р В Р’В°Р В Р вЂ Р В Р вЂ¦Р В РЎвЂўР В РЎВ Р РЋР РЉР В РЎвЂќР РЋР вЂљР В Р’В°Р В Р вЂ¦Р В Р’Вµ"), fullWidth());
        String groupingMode = UserPreferences.getGroupingMode(this);
        root.addView(createChoiceCard(
                "Р В Р Р‹Р В РЎВР В Р’ВµР РЋРІвЂљВ¬Р В Р’В°Р В Р вЂ¦Р В Р вЂ¦Р В Р’В°Р РЋР РЏ",
                "Р В Р’ВР РЋР С“Р В РЎвЂ”Р В РЎвЂўР В Р’В»Р РЋР Р‰Р В Р’В·Р В РЎвЂўР В Р вЂ Р В Р’В°Р РЋРІР‚С™Р РЋР Р‰ @group, Р В РЎвЂР В Р вЂ¦Р В Р’В°Р РЋРІР‚РЋР В Р’Вµ Р В РЎвЂ”Р В Р’ВµР РЋР вЂљР В Р вЂ Р РЋРІР‚в„–Р В РІвЂћвЂ“ Р РЋРІР‚С™Р В Р’ВµР В РЎвЂ“ Р В РЎвЂР В Р’В»Р В РЎвЂ Р В РЎвЂР В РЎВР РЋР РЏ Р РЋРІР‚С›Р В Р’В°Р В РІвЂћвЂ“Р В Р’В»Р В Р’В°.",
                UserPreferences.GROUPING_SMART.equals(groupingMode),
                () -> setGroupingMode(UserPreferences.GROUPING_SMART)
        ), fullWidthWithBottomMargin());
        root.addView(createChoiceCard(
                "Р В РЎСџР В РЎвЂў @group",
                "Р В РЎСџР В РЎвЂўР В РЎвЂќР В Р’В°Р В Р’В·Р РЋРІР‚в„–Р В Р вЂ Р В Р’В°Р РЋРІР‚С™Р РЋР Р‰ Р В РЎвЂ“Р РЋР вЂљР РЋРЎвЂњР В РЎвЂ”Р В РЎвЂ”Р РЋРІР‚в„– Р РЋРІР‚С™Р В РЎвЂўР В Р’В»Р РЋР Р‰Р В РЎвЂќР В РЎвЂў Р В РЎвЂР В Р’В· @group(...).",
                UserPreferences.GROUPING_GROUP.equals(groupingMode),
                () -> setGroupingMode(UserPreferences.GROUPING_GROUP)
        ), fullWidthWithBottomMargin());
        root.addView(createChoiceCard(
                "Р В РЎСџР В РЎвЂў Р РЋРІР‚С™Р В Р’ВµР В РЎвЂ“Р В Р’В°Р В РЎВ",
                "Р В Р’ВР РЋР С“Р В РЎвЂ”Р В РЎвЂўР В Р’В»Р РЋР Р‰Р В Р’В·Р В РЎвЂўР В Р вЂ Р В Р’В°Р РЋРІР‚С™Р РЋР Р‰ Р В РЎвЂ”Р В Р’ВµР РЋР вЂљР В Р вЂ Р РЋРІР‚в„–Р В РІвЂћвЂ“ Р РЋРІР‚С™Р В Р’ВµР В РЎвЂ“ Р В Р’В·Р В Р’В°Р В РўвЂР В Р’В°Р РЋРІР‚РЋР В РЎвЂ Р В РЎвЂќР В Р’В°Р В РЎвЂќ Р В РЎвЂ“Р РЋР вЂљР РЋРЎвЂњР В РЎвЂ”Р В РЎвЂ”Р РЋРЎвЂњ.",
                UserPreferences.GROUPING_TAG.equals(groupingMode),
                () -> setGroupingMode(UserPreferences.GROUPING_TAG)
        ), fullWidthWithBottomMargin());
        root.addView(createChoiceCard(
                "Р В РЎСџР В РЎвЂў Р РЋРІР‚С›Р В Р’В°Р В РІвЂћвЂ“Р В Р’В»Р В Р’В°Р В РЎВ",
                "Р В РІР‚СљР РЋР вЂљР РЋРЎвЂњР В РЎвЂ”Р В РЎвЂ”Р В РЎвЂР РЋР вЂљР В РЎвЂўР В Р вЂ Р В Р’В°Р РЋРІР‚С™Р РЋР Р‰ Р В Р’В·Р В Р’В°Р В РўвЂР В Р’В°Р РЋРІР‚РЋР В РЎвЂ Р В РЎвЂ”Р В РЎвЂў Р В РЎвЂР В РЎВР В Р’ВµР В Р вЂ¦Р В РЎвЂ markdown-Р РЋРІР‚С›Р В Р’В°Р В РІвЂћвЂ“Р В Р’В»Р В Р’В°.",
                UserPreferences.GROUPING_FILE.equals(groupingMode),
                () -> setGroupingMode(UserPreferences.GROUPING_FILE)
        ), fullWidthWithBottomMargin());
    }

    private void addAppearanceSettings(LinearLayout root) {
        TextView appearanceTitle = new TextView(this);
        appearanceTitle.setText("Р В РІР‚в„ўР В Р вЂ¦Р В Р’ВµР РЋРІвЂљВ¬Р В Р вЂ¦Р В РЎвЂР В РІвЂћвЂ“ Р В Р вЂ Р В РЎвЂР В РўвЂ");
        appearanceTitle.setTextSize(18);
        appearanceTitle.setTextColor(getColor(R.color.text_primary));
        appearanceTitle.setPadding(0, dp(12), 0, dp(6));
        root.addView(appearanceTitle, fullWidth());

        TextView appearanceDescription = new TextView(this);
        appearanceDescription.setText("Р В РЎС›Р В Р’ВµР В РЎВР В Р’В° Р В РЎвЂ”Р РЋР вЂљР В РЎвЂР В Р’В»Р В РЎвЂўР В Р’В¶Р В Р’ВµР В Р вЂ¦Р В РЎвЂР РЋР РЏ: Р РЋР С“Р В РЎвЂР РЋР С“Р РЋРІР‚С™Р В Р’ВµР В РЎВР В Р вЂ¦Р В Р’В°Р РЋР РЏ, Р РЋР С“Р В Р вЂ Р В Р’ВµР РЋРІР‚С™Р В Р’В»Р В Р’В°Р РЋР РЏ Р В РЎвЂР В Р’В»Р В РЎвЂ Р РЋРІР‚С™Р В Р’ВµР В РЎВР В Р вЂ¦Р В Р’В°Р РЋР РЏ.");
        appearanceDescription.setTextSize(14);
        appearanceDescription.setTextColor(getColor(R.color.text_secondary));
        appearanceDescription.setPadding(0, 0, 0, dp(8));
        root.addView(appearanceDescription, fullWidth());

        String themeMode = ThemePreferences.getThemeMode(this);
        root.addView(createChoiceCard(
                "Р В Р Р‹Р В РЎвЂР РЋР С“Р РЋРІР‚С™Р В Р’ВµР В РЎВР В Р вЂ¦Р В Р’В°Р РЋР РЏ",
                "Р В Р Р‹Р В Р’В»Р В Р’ВµР В РўвЂР В РЎвЂўР В Р вЂ Р В Р’В°Р РЋРІР‚С™Р РЋР Р‰ Р В Р вЂ¦Р В Р’В°Р РЋР С“Р РЋРІР‚С™Р РЋР вЂљР В РЎвЂўР В РІвЂћвЂ“Р В РЎвЂќР В Р’Вµ Р РЋРІР‚С™Р В Р’ВµР В РЎВР РЋРІР‚в„– Android.",
                ThemePreferences.MODE_SYSTEM.equals(themeMode),
                () -> setThemeMode(ThemePreferences.MODE_SYSTEM)
        ), fullWidthWithBottomMargin());
        root.addView(createChoiceCard(
                "Р В Р Р‹Р В Р вЂ Р В Р’ВµР РЋРІР‚С™Р В Р’В»Р В Р’В°Р РЋР РЏ",
                "Р В РІР‚в„ўР РЋР С“Р В Р’ВµР В РЎвЂ“Р В РўвЂР В Р’В° Р В РЎвЂР РЋР С“Р В РЎвЂ”Р В РЎвЂўР В Р’В»Р РЋР Р‰Р В Р’В·Р В РЎвЂўР В Р вЂ Р В Р’В°Р РЋРІР‚С™Р РЋР Р‰ Р РЋР С“Р В Р вЂ Р В Р’ВµР РЋРІР‚С™Р В Р’В»Р РЋРЎвЂњР РЋР вЂ№ Р РЋРІР‚С™Р В Р’ВµР В РЎВР РЋРЎвЂњ.",
                ThemePreferences.MODE_LIGHT.equals(themeMode),
                () -> setThemeMode(ThemePreferences.MODE_LIGHT)
        ), fullWidthWithBottomMargin());
        root.addView(createChoiceCard(
                "Р В РЎС›Р В Р’ВµР В РЎВР В Р вЂ¦Р В Р’В°Р РЋР РЏ",
                "Р В РІР‚в„ўР РЋР С“Р В Р’ВµР В РЎвЂ“Р В РўвЂР В Р’В° Р В РЎвЂР РЋР С“Р В РЎвЂ”Р В РЎвЂўР В Р’В»Р РЋР Р‰Р В Р’В·Р В РЎвЂўР В Р вЂ Р В Р’В°Р РЋРІР‚С™Р РЋР Р‰ Р РЋРІР‚С™Р В Р’ВµР В РЎВР В Р вЂ¦Р РЋРЎвЂњР РЋР вЂ№ Р РЋРІР‚С™Р В Р’ВµР В РЎВР РЋРЎвЂњ.",
                ThemePreferences.MODE_DARK.equals(themeMode),
                () -> setThemeMode(ThemePreferences.MODE_DARK)
        ), fullWidthWithBottomMargin());
    }

    private void addFormatSettings(LinearLayout root) {
        TextView formatTitle = new TextView(this);
        formatTitle.setText("Р В Р’В¤Р В РЎвЂўР РЋР вЂљР В РЎВР В Р’В°Р РЋРІР‚С™ Р В Р’В·Р В Р’В°Р В РўвЂР В Р’В°Р РЋРІР‚РЋ");
        formatTitle.setTextSize(18);
        formatTitle.setTextColor(getColor(R.color.text_primary));
        formatTitle.setPadding(0, dp(12), 0, dp(6));
        root.addView(formatTitle, fullWidth());

        TaskFormatSettings settings = TaskFormatSettings.load(this);
        dueKeywordInput = addKeywordInput(root, "Р В РЎв„ўР В Р’В»Р РЋР вЂ№Р РЋРІР‚РЋ Р В Р вЂ Р РЋР вЂљР В Р’ВµР В РЎВР В Р’ВµР В Р вЂ¦Р В РЎвЂ", settings.getDueKeyword());
        repeatKeywordInput = addKeywordInput(root, "Р В РЎв„ўР В Р’В»Р РЋР вЂ№Р РЋРІР‚РЋ Р В РЎвЂ”Р В РЎвЂўР В Р вЂ Р РЋРІР‚С™Р В РЎвЂўР РЋР вЂљР В Р’В°", settings.getRepeatKeyword());
        repeatUntilDoneKeywordInput = addKeywordInput(
                root,
                "Р В РЎв„ўР В Р’В»Р РЋР вЂ№Р РЋРІР‚РЋ Р В РЎвЂ”Р В РЎвЂўР В Р вЂ Р РЋРІР‚С™Р В РЎвЂўР РЋР вЂљР В Р’В° Р В РўвЂР В РЎвЂў Р В Р вЂ Р РЋРІР‚в„–Р В РЎвЂ”Р В РЎвЂўР В Р’В»Р В Р вЂ¦Р В Р’ВµР В Р вЂ¦Р В РЎвЂР РЋР РЏ",
                settings.getRepeatUntilDoneKeyword()
        );
        tagKeywordInput = addKeywordInput(root, "Р В РЎв„ўР В Р’В»Р РЋР вЂ№Р РЋРІР‚РЋ Р РЋРІР‚С™Р В Р’ВµР В РЎвЂ“Р В РЎвЂўР В Р вЂ ", settings.getTagKeyword());
        priorityKeywordInput = addKeywordInput(
                root,
                "Р В РЎв„ўР В Р’В»Р РЋР вЂ№Р РЋРІР‚РЋ Р В РЎвЂ”Р РЋР вЂљР В РЎвЂР В РЎвЂўР РЋР вЂљР В РЎвЂР РЋРІР‚С™Р В Р’ВµР РЋРІР‚С™Р В Р’В°",
                settings.getPriorityKeyword()
        );
        groupKeywordInput = addKeywordInput(root, "Р В РЎв„ўР В Р’В»Р РЋР вЂ№Р РЋРІР‚РЋ Р В РЎвЂ“Р РЋР вЂљР РЋРЎвЂњР В РЎвЂ”Р В РЎвЂ”Р РЋРІР‚в„–", settings.getGroupKeyword());

        Button saveFormatButton = createButton("Р В Р Р‹Р В РЎвЂўР РЋРІР‚В¦Р РЋР вЂљР В Р’В°Р В Р вЂ¦Р В РЎвЂР РЋРІР‚С™Р РЋР Р‰");
        saveFormatButton.setOnClickListener(view -> saveFormatSettings());
        root.addView(saveFormatButton, fullWidthWithBottomMargin());

        Button resetFormatButton = createButton("Р В Р Р‹Р В Р’В±Р РЋР вЂљР В РЎвЂўР РЋР С“Р В РЎвЂР РЋРІР‚С™Р РЋР Р‰");
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
        scanTitle.setText("Р В РЎСџР В РЎвЂўР В РЎвЂР РЋР С“Р В РЎвЂќ Р В Р’В·Р В Р’В°Р В РўвЂР В Р’В°Р РЋРІР‚РЋ Р В Р вЂ  Р РЋРІР‚С›Р В Р’В°Р В РІвЂћвЂ“Р В Р’В»Р В Р’В°Р РЋРІР‚В¦");
        scanTitle.setTextSize(18);
        scanTitle.setTextColor(getColor(R.color.text_primary));
        scanTitle.setPadding(0, dp(12), 0, dp(6));
        root.addView(scanTitle, fullWidth());

        NoteScanSettings settings = NoteScanSettings.load(this);
        includePatternsInput = addKeywordInput(
                root,
                "Р В Р’ВР РЋР С“Р В РЎвЂќР В Р’В°Р РЋРІР‚С™Р РЋР Р‰ Р РЋРІР‚С›Р В Р’В°Р В РІвЂћвЂ“Р В Р’В»Р РЋРІР‚в„–",
                settings.getIncludePatternsText()
        );
        excludePatternsInput = addKeywordInput(
                root,
                "Р В Р’ВР РЋР С“Р В РЎвЂќР В Р’В»Р РЋР вЂ№Р РЋРІР‚РЋР В Р’В°Р РЋРІР‚С™Р РЋР Р‰ Р РЋРІР‚С›Р В Р’В°Р В РІвЂћвЂ“Р В Р’В»Р РЋРІР‚в„– Р В РЎвЂ Р В РЎвЂ”Р В Р’В°Р В РЎвЂ”Р В РЎвЂќР В РЎвЂ",
                settings.getExcludePatternsText()
        );
        maxFilesInput = addKeywordInput(
                root,
                "Р В РЎС™Р В Р’В°Р В РЎвЂќР РЋР С“Р В РЎвЂР В РЎВР РЋРЎвЂњР В РЎВ Р РЋРІР‚С›Р В Р’В°Р В РІвЂћвЂ“Р В Р’В»Р В РЎвЂўР В Р вЂ  Р В Р’В·Р В Р’В° Р РЋР С“Р В РЎвЂќР В Р’В°Р В Р вЂ¦Р В РЎвЂР РЋР вЂљР В РЎвЂўР В Р вЂ Р В Р’В°Р В Р вЂ¦Р В РЎвЂР В Р’Вµ",
                String.valueOf(settings.getMaxFiles())
        );
        maxFilesInput.setInputType(InputType.TYPE_CLASS_NUMBER);

        Button saveScanButton = createButton("Р В Р Р‹Р В РЎвЂўР РЋРІР‚В¦Р РЋР вЂљР В Р’В°Р В Р вЂ¦Р В РЎвЂР РЋРІР‚С™Р РЋР Р‰ Р В РЎвЂ”Р В РЎвЂўР В РЎвЂР РЋР С“Р В РЎвЂќ Р В Р’В·Р В Р’В°Р В РўвЂР В Р’В°Р РЋРІР‚РЋ");
        saveScanButton.setOnClickListener(view -> saveScanSettings());
        root.addView(saveScanButton, fullWidthWithBottomMargin());

        Button resetScanButton = createButton("Р В Р Р‹Р В Р’В±Р РЋР вЂљР В РЎвЂўР РЋР С“Р В РЎвЂР РЋРІР‚С™Р РЋР Р‰ Р В РЎвЂ”Р В РЎвЂўР В РЎвЂР РЋР С“Р В РЎвЂќ Р В Р’В·Р В Р’В°Р В РўвЂР В Р’В°Р РЋРІР‚РЋ");
        resetScanButton.setOnClickListener(view -> resetScanSettings());
        root.addView(resetScanButton, fullWidthWithBottomMargin());
    }

    private void addNotificationActionSettings(LinearLayout root) {
        TextView actionTitle = new TextView(this);
        actionTitle.setText("Р В РІР‚СњР В Р’ВµР В РІвЂћвЂ“Р РЋР С“Р РЋРІР‚С™Р В Р вЂ Р В РЎвЂР РЋР РЏ Р В РЎвЂР В Р’В· Р РЋРЎвЂњР В Р вЂ Р В Р’ВµР В РўвЂР В РЎвЂўР В РЎВР В Р’В»Р В Р’ВµР В Р вЂ¦Р В РЎвЂР РЋР РЏ");
        actionTitle.setTextSize(18);
        actionTitle.setTextColor(getColor(R.color.text_primary));
        actionTitle.setPadding(0, dp(12), 0, dp(6));
        root.addView(actionTitle, fullWidth());

        snoozeMinutesInput = addKeywordInput(
                root,
                "Р В РЎвЂєР РЋРІР‚С™Р В Р’В»Р В РЎвЂўР В Р’В¶Р В РЎвЂР РЋРІР‚С™Р РЋР Р‰ Р В Р вЂ¦Р В Р’В° Р В РЎВР В РЎвЂР В Р вЂ¦Р РЋРЎвЂњР РЋРІР‚С™",
                String.valueOf(ActionPreferences.getSnoozeMinutes(this))
        );
        snoozeMinutesInput.setInputType(InputType.TYPE_CLASS_NUMBER);

        repeatUntilDoneMinutesInput = addKeywordInput(
                root,
                "Р В РЎСџР В РЎвЂўР В Р вЂ Р РЋРІР‚С™Р В РЎвЂўР РЋР вЂљ Р В РўвЂР В РЎвЂў Р В Р вЂ Р РЋРІР‚в„–Р В РЎвЂ”Р В РЎвЂўР В Р’В»Р В Р вЂ¦Р В Р’ВµР В Р вЂ¦Р В РЎвЂР РЋР РЏ, Р В РЎВР В РЎвЂР В Р вЂ¦Р РЋРЎвЂњР РЋРІР‚С™",
                String.valueOf(ActionPreferences.getRepeatUntilDoneMinutes(this))
        );
        repeatUntilDoneMinutesInput.setInputType(InputType.TYPE_CLASS_NUMBER);

        overdueGraceMinutesInput = addKeywordInput(
                root,
                "Р В Р Р‹Р РЋРІР‚РЋР В РЎвЂР РЋРІР‚С™Р В Р’В°Р РЋРІР‚С™Р РЋР Р‰ Р В РЎвЂ”Р РЋР вЂљР В РЎвЂўР РЋР С“Р РЋР вЂљР В РЎвЂўР РЋРІР‚РЋР В Р’ВµР В Р вЂ¦Р В Р вЂ¦Р В РЎвЂўР В РІвЂћвЂ“ Р РЋРІР‚РЋР В Р’ВµР РЋР вЂљР В Р’ВµР В Р’В· Р В РЎВР В РЎвЂР В Р вЂ¦Р РЋРЎвЂњР РЋРІР‚С™",
                String.valueOf(ActionPreferences.getOverdueGraceMinutes(this))
        );
        overdueGraceMinutesInput.setInputType(InputType.TYPE_CLASS_NUMBER);

        recordSnoozeCountCheckbox = new CheckBox(this);
        recordSnoozeCountCheckbox.setText("Р В РІР‚вЂќР В Р’В°Р В РЎвЂ”Р В РЎвЂР РЋР С“Р РЋРІР‚в„–Р В Р вЂ Р В Р’В°Р РЋРІР‚С™Р РЋР Р‰ Р В РЎвЂќР В РЎвЂўР В Р’В»Р В РЎвЂР РЋРІР‚РЋР В Р’ВµР РЋР С“Р РЋРІР‚С™Р В Р вЂ Р В РЎвЂў Р В РЎвЂўР РЋРІР‚С™Р В Р’В»Р В РЎвЂўР В Р’В¶Р В Р’ВµР В Р вЂ¦Р В РЎвЂР В РІвЂћвЂ“ Р В Р вЂ  Р В Р’В·Р В Р’В°Р В РЎВР В Р’ВµР РЋРІР‚С™Р В РЎвЂќР РЋРЎвЂњ");
        recordSnoozeCountCheckbox.setTextColor(getColor(R.color.text_secondary));
        recordSnoozeCountCheckbox.setChecked(ActionPreferences.shouldRecordSnoozeCount(this));
        root.addView(recordSnoozeCountCheckbox, fullWidthWithBottomMargin());

        Button saveActionSettingsButton = createButton("Р В Р Р‹Р В РЎвЂўР РЋРІР‚В¦Р РЋР вЂљР В Р’В°Р В Р вЂ¦Р В РЎвЂР РЋРІР‚С™Р РЋР Р‰ Р В РўвЂР В Р’ВµР В РІвЂћвЂ“Р РЋР С“Р РЋРІР‚С™Р В Р вЂ Р В РЎвЂР РЋР РЏ Р РЋРЎвЂњР В Р вЂ Р В Р’ВµР В РўвЂР В РЎвЂўР В РЎВР В Р’В»Р В Р’ВµР В Р вЂ¦Р В РЎвЂР РЋР РЏ");
        saveActionSettingsButton.setOnClickListener(view -> saveActionSettings());
        root.addView(saveActionSettingsButton, fullWidthWithBottomMargin());
    }

    private void addDebugSettings(LinearLayout root) {
        TextView debugTitle = new TextView(this);
        debugTitle.setText("Р В РЎвЂєР РЋРІР‚С™Р В Р’В»Р В Р’В°Р В РўвЂР В РЎвЂќР В Р’В°");
        debugTitle.setTextSize(18);
        debugTitle.setTextColor(getColor(R.color.text_primary));
        debugTitle.setPadding(0, dp(12), 0, dp(6));
        root.addView(debugTitle, fullWidth());

        TextView debugDescription = new TextView(this);
        debugDescription.setText("Р В РЎв„ўР В Р вЂ¦Р В РЎвЂўР В РЎвЂ”Р В РЎвЂќР В РЎвЂ Р РЋР вЂљР В Р’В°Р В Р’В±Р В РЎвЂўР РЋРІР‚С™Р В Р’В°Р РЋР вЂ№Р РЋРІР‚С™ Р РЋР С“ Р В РЎвЂ”Р В Р’ВµР РЋР вЂљР В Р вЂ Р В РЎвЂўР В РІвЂћвЂ“ Р В Р’В°Р В РЎвЂќР РЋРІР‚С™Р В РЎвЂР В Р вЂ Р В Р вЂ¦Р В РЎвЂўР В РІвЂћвЂ“ Р В Р’В·Р В Р’В°Р В РўвЂР В Р’В°Р РЋРІР‚РЋР В Р’ВµР В РІвЂћвЂ“ Р В Р вЂ  Р В Р вЂ Р РЋРІР‚в„–Р В Р’В±Р РЋР вЂљР В Р’В°Р В Р вЂ¦Р В Р вЂ¦Р В РЎвЂўР В РІвЂћвЂ“ Р В Р’В·Р В Р’В°Р В РЎВР В Р’ВµР РЋРІР‚С™Р В РЎвЂќР В Р’Вµ.");
        debugDescription.setTextSize(14);
        debugDescription.setTextColor(getColor(R.color.text_secondary));
        debugDescription.setPadding(0, 0, 0, dp(8));
        root.addView(debugDescription, fullWidth());

        Button debugActionsButton = createButton("Р В РЎвЂєР РЋРІР‚С™Р В РЎвЂќР РЋР вЂљР РЋРІР‚в„–Р РЋРІР‚С™Р РЋР Р‰ Р В РЎвЂўР РЋРІР‚С™Р В Р’В»Р В Р’В°Р В РўвЂР В РЎвЂўР РЋРІР‚РЋР В Р вЂ¦Р РЋРІР‚в„–Р В Р’Вµ Р В РўвЂР В Р’ВµР В РІвЂћвЂ“Р РЋР С“Р РЋРІР‚С™Р В Р вЂ Р В РЎвЂР РЋР РЏ");
        debugActionsButton.setOnClickListener(view -> showDebugActions());
        root.addView(debugActionsButton, fullWidthWithBottomMargin());
    }

    private void showDebugActions() {
        String[] actions = new String[]{
                "Р В Р в‚¬Р В Р вЂ Р В Р’ВµР В РўвЂР В РЎвЂўР В РЎВР В Р’В»Р В Р’ВµР В Р вЂ¦Р В РЎвЂР В Р’Вµ Р РЋР С“Р В Р’ВµР В РІвЂћвЂ“Р РЋРІР‚РЋР В Р’В°Р РЋР С“",
                "Р В РІР‚в„ўР РЋРІР‚в„–Р В РЎвЂ”Р В РЎвЂўР В Р’В»Р В Р вЂ¦Р В РЎвЂР РЋРІР‚С™Р РЋР Р‰ Р В РЎвЂ”Р В Р’ВµР РЋР вЂљР В Р вЂ Р РЋРЎвЂњР РЋР вЂ№ Р В Р’В·Р В Р’В°Р В РўвЂР В Р’В°Р РЋРІР‚РЋР РЋРЎвЂњ",
                "Р В РЎвЂєР РЋРІР‚С™Р В Р’В»Р В РЎвЂўР В Р’В¶Р В РЎвЂР РЋРІР‚С™Р РЋР Р‰ Р В РЎвЂ”Р В Р’ВµР РЋР вЂљР В Р вЂ Р РЋРЎвЂњР РЋР вЂ№ Р В Р’В·Р В Р’В°Р В РўвЂР В Р’В°Р РЋРІР‚РЋР РЋРЎвЂњ",
                "Р В РЎСџР В Р’ВµР РЋР вЂљР В Р’ВµР В РЎвЂ”Р В Р’В»Р В Р’В°Р В Р вЂ¦Р В РЎвЂР РЋР вЂљР В РЎвЂўР В Р вЂ Р В Р’В°Р РЋРІР‚С™Р РЋР Р‰ Р В Р вЂ Р РЋР С“Р В Р’Вµ",
                "Р В РЎС›Р В Р’ВµР РЋР С“Р РЋРІР‚С™Р В РЎвЂўР В Р вЂ Р В РЎвЂўР В Р’Вµ Р РЋРЎвЂњР В Р вЂ Р В Р’ВµР В РўвЂР В РЎвЂўР В РЎВР В Р’В»Р В Р’ВµР В Р вЂ¦Р В РЎвЂР В Р’Вµ"
        };
        new android.app.AlertDialog.Builder(this)
                .setTitle("Р В РЎвЂєР РЋРІР‚С™Р В Р’В»Р В Р’В°Р В РўвЂР В РЎвЂќР В Р’В°")
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
                .setTitle("Р В РЎСџР В Р’ВµР РЋР вЂљР В Р’ВµР В РЎвЂќР В Р’В»Р РЋР вЂ№Р РЋРІР‚РЋР В РЎвЂР РЋРІР‚С™Р РЋР Р‰ Р В РЎвЂР РЋР С“Р РЋРІР‚С™Р В РЎвЂўР РЋРІР‚РЋР В Р вЂ¦Р В РЎвЂР В РЎвЂќ")
                .setMessage(TaskSourceManager.switchWithoutMigrationWarning(TaskStorageMode.INTERNAL_MARKDOWN_STORAGE))
                .setNegativeButton("Р В РЎвЂєР РЋРІР‚С™Р В РЎВР В Р’ВµР В Р вЂ¦Р В Р’В°", null)
                .setPositiveButton("Р В РЎСџР В Р’ВµР РЋР вЂљР В Р’ВµР В РЎвЂќР В Р’В»Р РЋР вЂ№Р РЋРІР‚РЋР В РЎвЂР РЋРІР‚С™Р РЋР Р‰", (dialog, which) -> {
                    try {
                        TaskSourceManager.useInternalStorage(this);
                        OnboardingPreferences.markCompleted(this);
                        rescheduleAll();
                        updateStatus();
                        Toast.makeText(this, "Р В РІР‚в„ўР РЋР С“Р РЋРІР‚С™Р РЋР вЂљР В РЎвЂўР В Р’ВµР В Р вЂ¦Р В Р вЂ¦Р В РЎвЂўР В Р’Вµ Р РЋРІР‚В¦Р РЋР вЂљР В Р’В°Р В Р вЂ¦Р В РЎвЂР В Р’В»Р В РЎвЂР РЋРІР‚В°Р В Р’Вµ Р В Р вЂ Р В РЎвЂќР В Р’В»Р РЋР вЂ№Р РЋРІР‚РЋР В Р’ВµР В Р вЂ¦Р В РЎвЂў", Toast.LENGTH_SHORT).show();
                    } catch (IOException exception) {
                        ErrorLog.record(this, "Р В РЎСљР В Р’Вµ Р РЋРЎвЂњР В РўвЂР В Р’В°Р В Р’В»Р В РЎвЂўР РЋР С“Р РЋР Р‰ Р В Р вЂ Р В РЎвЂќР В Р’В»Р РЋР вЂ№Р РЋРІР‚РЋР В РЎвЂР РЋРІР‚С™Р РЋР Р‰ Р В Р вЂ Р РЋР С“Р РЋРІР‚С™Р РЋР вЂљР В РЎвЂўР В Р’ВµР В Р вЂ¦Р В Р вЂ¦Р В РЎвЂўР В Р’Вµ Р РЋРІР‚В¦Р РЋР вЂљР В Р’В°Р В Р вЂ¦Р В РЎвЂР В Р’В»Р В РЎвЂР РЋРІР‚В°Р В Р’Вµ", exception);
                        Toast.makeText(
                                this,
                                "Р В РЎСљР В Р’Вµ Р РЋРЎвЂњР В РўвЂР В Р’В°Р В Р’В»Р В РЎвЂўР РЋР С“Р РЋР Р‰ Р В Р вЂ Р В РЎвЂќР В Р’В»Р РЋР вЂ№Р РЋРІР‚РЋР В РЎвЂР РЋРІР‚С™Р РЋР Р‰ Р В Р вЂ Р РЋР С“Р РЋРІР‚С™Р РЋР вЂљР В РЎвЂўР В Р’ВµР В Р вЂ¦Р В Р вЂ¦Р В РЎвЂўР В Р’Вµ Р РЋРІР‚В¦Р РЋР вЂљР В Р’В°Р В Р вЂ¦Р В РЎвЂР В Р’В»Р В РЎвЂР РЋРІР‚В°Р В Р’Вµ: " + safeMessage(exception),
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
        Toast.makeText(this, "Р В РІР‚в„ўР В Р вЂ¦Р В Р’ВµР РЋРІвЂљВ¬Р В Р вЂ¦Р В РЎвЂР В РІвЂћвЂ“ Р В РЎвЂР РЋР С“Р РЋРІР‚С™Р В РЎвЂўР РЋРІР‚РЋР В Р вЂ¦Р В РЎвЂР В РЎвЂќ Р В Р’В°Р В РЎвЂќР РЋРІР‚С™Р В РЎвЂР В Р вЂ Р В РЎвЂР РЋР вЂљР В РЎвЂўР В Р вЂ Р В Р’В°Р В Р вЂ¦", Toast.LENGTH_SHORT).show();
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
                    "Р В РІР‚в„ўР В Р вЂ¦Р В Р’ВµР РЋРІвЂљВ¬Р В Р вЂ¦Р В РЎвЂР В Р’Вµ Р В РЎвЂ”Р В РЎвЂўР В РўвЂР В РЎвЂќР В Р’В»Р РЋР вЂ№Р РЋРІР‚РЋР В Р’ВµР В Р вЂ¦Р В РЎвЂР РЋР РЏ Р В РЎвЂўР РЋРІР‚РЋР В РЎвЂР РЋРІР‚В°Р В Р’ВµР В Р вЂ¦Р РЋРІР‚в„–, Р В РЎвЂ”Р РЋР вЂљР В РЎвЂР В Р’В»Р В РЎвЂўР В Р’В¶Р В Р’ВµР В Р вЂ¦Р В РЎвЂР В Р’Вµ Р В РЎвЂ”Р В Р’ВµР РЋР вЂљР В Р’ВµР В РЎвЂќР В Р’В»Р РЋР вЂ№Р РЋРІР‚РЋР В Р’ВµР В Р вЂ¦Р В РЎвЂў Р В Р вЂ¦Р В Р’В° Р В Р вЂ Р РЋР С“Р РЋРІР‚С™Р РЋР вЂљР В РЎвЂўР В Р’ВµР В Р вЂ¦Р В Р вЂ¦Р В РЎвЂўР В Р’Вµ Р РЋРІР‚В¦Р РЋР вЂљР В Р’В°Р В Р вЂ¦Р В РЎвЂР В Р’В»Р В РЎвЂР РЋРІР‚В°Р В Р’Вµ",
                    Toast.LENGTH_SHORT
            ).show();
        } catch (IOException exception) {
            ErrorLog.record(this, "Р В РЎСљР В Р’Вµ Р РЋРЎвЂњР В РўвЂР В Р’В°Р В Р’В»Р В РЎвЂўР РЋР С“Р РЋР Р‰ Р В РЎвЂ”Р В Р’ВµР РЋР вЂљР В Р’ВµР В РЎвЂќР В Р’В»Р РЋР вЂ№Р РЋРІР‚РЋР В РЎвЂР РЋРІР‚С™Р РЋР Р‰Р РЋР С“Р РЋР РЏ Р В Р вЂ¦Р В Р’В° Р В Р вЂ Р РЋР С“Р РЋРІР‚С™Р РЋР вЂљР В РЎвЂўР В Р’ВµР В Р вЂ¦Р В Р вЂ¦Р В РЎвЂўР В Р’Вµ Р РЋРІР‚В¦Р РЋР вЂљР В Р’В°Р В Р вЂ¦Р В РЎвЂР В Р’В»Р В РЎвЂР РЋРІР‚В°Р В Р’Вµ Р В РЎвЂ”Р В РЎвЂўР РЋР С“Р В Р’В»Р В Р’Вµ Р В РЎвЂўР РЋРІР‚РЋР В РЎвЂР РЋР С“Р РЋРІР‚С™Р В РЎвЂќР В РЎвЂ Р В РЎвЂР РЋР С“Р РЋРІР‚С™Р В РЎвЂўР РЋРІР‚РЋР В Р вЂ¦Р В РЎвЂР В РЎвЂќР В РЎвЂўР В Р вЂ ", exception);
            Toast.makeText(
                    this,
                    "Р В РІР‚в„ўР В Р вЂ¦Р В Р’ВµР РЋРІвЂљВ¬Р В Р вЂ¦Р В РЎвЂР В Р’Вµ Р В РЎвЂ”Р В РЎвЂўР В РўвЂР В РЎвЂќР В Р’В»Р РЋР вЂ№Р РЋРІР‚РЋР В Р’ВµР В Р вЂ¦Р В РЎвЂР РЋР РЏ Р В РЎвЂўР РЋРІР‚РЋР В РЎвЂР РЋРІР‚В°Р В Р’ВµР В Р вЂ¦Р РЋРІР‚в„–, Р В Р вЂ¦Р В РЎвЂў Р В Р вЂ Р РЋР С“Р РЋРІР‚С™Р РЋР вЂљР В РЎвЂўР В Р’ВµР В Р вЂ¦Р В Р вЂ¦Р В РЎвЂўР В Р’Вµ Р РЋРІР‚В¦Р РЋР вЂљР В Р’В°Р В Р вЂ¦Р В РЎвЂР В Р’В»Р В РЎвЂР РЋРІР‚В°Р В Р’Вµ Р В Р вЂ¦Р В Р’Вµ Р РЋРЎвЂњР В РўвЂР В Р’В°Р В Р’В»Р В РЎвЂўР РЋР С“Р РЋР Р‰ Р В РЎвЂ”Р В РЎвЂўР В РўвЂР В РЎвЂ“Р В РЎвЂўР РЋРІР‚С™Р В РЎвЂўР В Р вЂ Р В РЎвЂР РЋРІР‚С™Р РЋР Р‰: " + safeMessage(exception),
                    Toast.LENGTH_LONG
            ).show();
        }
        updateStatus();
    }

    private String safeMessage(Exception exception) {
        String message = exception == null ? null : exception.getMessage();
        return message == null || message.trim().isEmpty()
                ? "Р В РЎвЂ”Р РЋР вЂљР В РЎвЂўР В Р вЂ Р В Р’ВµР РЋР вЂљР РЋР Р‰Р РЋРІР‚С™Р В Р’Вµ Р В РўвЂР В РЎвЂўР РЋР С“Р РЋРІР‚С™Р РЋРЎвЂњР В РЎвЂ” Р В РЎвЂќ Р В РЎвЂР РЋР С“Р РЋРІР‚С™Р В РЎвЂўР РЋРІР‚РЋР В Р вЂ¦Р В РЎвЂР В РЎвЂќР РЋРЎвЂњ Р В Р’В·Р В Р’В°Р В РўвЂР В Р’В°Р РЋРІР‚РЋ"
                : message;
    }

    private void rescheduleAll() {
        NoteChangeMonitor.NoteSyncResult result = NoteChangeMonitor.syncNow(this, true);
        NoteChangeMonitor.ensureScheduled(this);
        if (result.isSuccess()) {
            Toast.makeText(
                    this,
                    "Р В РЎСџР В Р’ВµР РЋР вЂљР В Р’ВµР В РЎвЂ”Р В Р’В»Р В Р’В°Р В Р вЂ¦Р В РЎвЂР РЋР вЂљР В РЎвЂўР В Р вЂ Р В Р’В°Р В Р вЂ¦Р В РЎвЂў Р В Р’В·Р В Р’В°Р В РўвЂР В Р’В°Р РЋРІР‚РЋ: " + result.getTaskCount(),
                    Toast.LENGTH_SHORT
            ).show();
        } else {
            String message = result.isRestoredFromCache()
                    ? "Р В Р’ВР РЋР С“Р РЋРІР‚С™Р В РЎвЂўР РЋРІР‚РЋР В Р вЂ¦Р В РЎвЂР В РЎвЂќ Р В Р вЂ¦Р В Р’Вµ Р В РЎвЂ”Р РЋР вЂљР В РЎвЂўР РЋРІР‚РЋР В РЎвЂР РЋРІР‚С™Р В Р’В°Р В Р вЂ¦, Р РЋРЎвЂњР В Р вЂ Р В Р’ВµР В РўвЂР В РЎвЂўР В РЎВР В Р’В»Р В Р’ВµР В Р вЂ¦Р В РЎвЂР РЋР РЏ Р В Р вЂ Р В РЎвЂўР РЋР С“Р РЋР С“Р РЋРІР‚С™Р В Р’В°Р В Р вЂ¦Р В РЎвЂўР В Р вЂ Р В Р’В»Р В Р’ВµР В Р вЂ¦Р РЋРІР‚в„– Р В РЎвЂР В Р’В· Р В РЎвЂќР РЋР РЉР РЋРІвЂљВ¬Р В Р’В°"
                    : "Р В РЎСљР В Р’Вµ Р РЋРЎвЂњР В РўвЂР В Р’В°Р В Р’В»Р В РЎвЂўР РЋР С“Р РЋР Р‰ Р В РЎвЂ”Р РЋР вЂљР В РЎвЂўР РЋРІР‚РЋР В РЎвЂР РЋРІР‚С™Р В Р’В°Р РЋРІР‚С™Р РЋР Р‰ Р В РЎвЂР РЋР С“Р РЋРІР‚С™Р В РЎвЂўР РЋРІР‚РЋР В Р вЂ¦Р В РЎвЂР В РЎвЂќ: " + result.getErrorMessage();
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
        snoozeMinutesInput.setText(String.valueOf(ActionPreferences.getSnoozeMinutes(this)));
        repeatUntilDoneMinutesInput.setText(String.valueOf(ActionPreferences.getRepeatUntilDoneMinutes(this)));
        overdueGraceMinutesInput.setText(String.valueOf(ActionPreferences.getOverdueGraceMinutes(this)));
        Toast.makeText(this, "Р В РІР‚СњР В Р’ВµР В РІвЂћвЂ“Р РЋР С“Р РЋРІР‚С™Р В Р вЂ Р В РЎвЂР РЋР РЏ Р РЋРЎвЂњР В Р вЂ Р В Р’ВµР В РўвЂР В РЎвЂўР В РЎВР В Р’В»Р В Р’ВµР В Р вЂ¦Р В РЎвЂР РЋР РЏ Р РЋР С“Р В РЎвЂўР РЋРІР‚В¦Р РЋР вЂљР В Р’В°Р В Р вЂ¦Р В Р’ВµР В Р вЂ¦Р РЋРІР‚в„–", Toast.LENGTH_SHORT).show();
        updateStatus();
    }

    private void setEditMode(String mode) {
        EditPreferences.setEditMode(this, mode);
        Toast.makeText(this, "Р В Р’В Р В Р’ВµР В Р’В¶Р В РЎвЂР В РЎВ Р РЋР вЂљР В Р’ВµР В РўвЂР В Р’В°Р В РЎвЂќР РЋРІР‚С™Р В РЎвЂР РЋР вЂљР В РЎвЂўР В Р вЂ Р В Р’В°Р В Р вЂ¦Р В РЎвЂР РЋР РЏ Р РЋР С“Р В РЎвЂўР РЋРІР‚В¦Р РЋР вЂљР В Р’В°Р В Р вЂ¦Р В Р’ВµР В Р вЂ¦", Toast.LENGTH_SHORT).show();
        rebuild();
    }

    private void setGroupingMode(String mode) {
        UserPreferences.setGroupingMode(this, mode);
        Toast.makeText(this, "Р В РІР‚СљР РЋР вЂљР РЋРЎвЂњР В РЎвЂ”Р В РЎвЂ”Р В РЎвЂР РЋР вЂљР В РЎвЂўР В Р вЂ Р В РЎвЂќР В Р’В° Р РЋР С“Р В РЎвЂўР РЋРІР‚В¦Р РЋР вЂљР В Р’В°Р В Р вЂ¦Р В Р’ВµР В Р вЂ¦Р В Р’В°", Toast.LENGTH_SHORT).show();
        rebuild();
    }

    private void setThemeMode(String mode) {
        ThemePreferences.setThemeMode(this, mode);
        Toast.makeText(this, "Р В РЎС›Р В Р’ВµР В РЎВР В Р’В° Р РЋР С“Р В РЎвЂўР РЋРІР‚В¦Р РЋР вЂљР В Р’В°Р В Р вЂ¦Р В Р’ВµР В Р вЂ¦Р В Р’В°", Toast.LENGTH_SHORT).show();
        recreate();
    }

    private void savePrivateMarker() {
        UserPreferences.setPrivateMarker(this, privateMarkerInput.getText().toString());
        privateMarkerInput.setText(UserPreferences.getPrivateMarker(this));
        Toast.makeText(this, "Р В РЎСџР РЋР вЂљР В РЎвЂР В Р вЂ Р В Р’В°Р РЋРІР‚С™Р В Р вЂ¦Р РЋРІР‚в„–Р В РІвЂћвЂ“ Р В РЎВР В Р’В°Р РЋР вЂљР В РЎвЂќР В Р’ВµР РЋР вЂљ Р РЋР С“Р В РЎвЂўР РЋРІР‚В¦Р РЋР вЂљР В Р’В°Р В Р вЂ¦Р В Р’ВµР В Р вЂ¦", Toast.LENGTH_SHORT).show();
    }

    private String themeButtonText(String mode, String label) {
        return mode.equals(ThemePreferences.getThemeMode(this)) ? label + " Р В Р вЂ Р РЋРІР‚в„–Р В Р’В±Р РЋР вЂљР В Р’В°Р В Р вЂ¦Р В Р’В°" : label;
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
                    "Р В РЎв„ўР В Р’В»Р РЋР вЂ№Р РЋРІР‚РЋР В Р’ВµР В Р вЂ Р РЋРІР‚в„–Р В Р’Вµ Р РЋР С“Р В Р’В»Р В РЎвЂўР В Р вЂ Р В Р’В° Р В РўвЂР В РЎвЂўР В Р’В»Р В Р’В¶Р В Р вЂ¦Р РЋРІР‚в„– Р В Р’В±Р РЋРІР‚в„–Р РЋРІР‚С™Р РЋР Р‰ Р РЋР вЂљР В Р’В°Р В Р’В·Р В Р вЂ¦Р РЋРІР‚в„–Р В РЎВР В РЎвЂ",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        TaskFormatSettings.save(this, settings);
        populateFormatInputs(settings);
        Toast.makeText(this, "Р В Р’В¤Р В РЎвЂўР РЋР вЂљР В РЎВР В Р’В°Р РЋРІР‚С™ Р В Р’В·Р В Р’В°Р В РўвЂР В Р’В°Р РЋРІР‚РЋ Р РЋР С“Р В РЎвЂўР РЋРІР‚В¦Р РЋР вЂљР В Р’В°Р В Р вЂ¦Р В Р’ВµР В Р вЂ¦", Toast.LENGTH_SHORT).show();
        rescheduleAll();
    }

    private void resetFormatSettings() {
        TaskFormatSettings.reset(this);
        populateFormatInputs(TaskFormatSettings.defaults());
        Toast.makeText(this, "Р В Р’В¤Р В РЎвЂўР РЋР вЂљР В РЎВР В Р’В°Р РЋРІР‚С™ Р В Р’В·Р В Р’В°Р В РўвЂР В Р’В°Р РЋРІР‚РЋ Р РЋР С“Р В Р’В±Р РЋР вЂљР В РЎвЂўР РЋРІвЂљВ¬Р В Р’ВµР В Р вЂ¦", Toast.LENGTH_SHORT).show();
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
        Toast.makeText(this, "Р В РЎСџР В РЎвЂўР В РЎвЂР РЋР С“Р В РЎвЂќ Р В Р’В·Р В Р’В°Р В РўвЂР В Р’В°Р РЋРІР‚РЋ Р РЋР С“Р В РЎвЂўР РЋРІР‚В¦Р РЋР вЂљР В Р’В°Р В Р вЂ¦Р В Р’ВµР В Р вЂ¦", Toast.LENGTH_SHORT).show();
        rescheduleAll();
    }

    private void resetScanSettings() {
        NoteScanSettings.reset(this);
        populateScanInputs(NoteScanSettings.defaults());
        Toast.makeText(this, "Р В РЎСџР В РЎвЂўР В РЎвЂР РЋР С“Р В РЎвЂќ Р В Р’В·Р В Р’В°Р В РўвЂР В Р’В°Р РЋРІР‚РЋ Р РЋР С“Р В Р’В±Р РЋР вЂљР В РЎвЂўР РЋРІвЂљВ¬Р В Р’ВµР В Р вЂ¦", Toast.LENGTH_SHORT).show();
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
                sent ? "Р В РЎС›Р В Р’ВµР РЋР С“Р РЋРІР‚С™Р В РЎвЂўР В Р вЂ Р В РЎвЂўР В Р’Вµ Р РЋРЎвЂњР В Р вЂ Р В Р’ВµР В РўвЂР В РЎвЂўР В РЎВР В Р’В»Р В Р’ВµР В Р вЂ¦Р В РЎвЂР В Р’Вµ Р В РЎвЂўР РЋРІР‚С™Р В РЎвЂ”Р РЋР вЂљР В Р’В°Р В Р вЂ Р В Р’В»Р В Р’ВµР В Р вЂ¦Р В РЎвЂў" : "Р В РЎСљР В Р’ВµР РЋРІР‚С™ Р РЋР вЂљР В Р’В°Р В Р’В·Р РЋР вЂљР В Р’ВµР РЋРІвЂљВ¬Р В Р’ВµР В Р вЂ¦Р В РЎвЂР РЋР РЏ Р В Р вЂ¦Р В Р’В° Р РЋРЎвЂњР В Р вЂ Р В Р’ВµР В РўвЂР В РЎвЂўР В РЎВР В Р’В»Р В Р’ВµР В Р вЂ¦Р В РЎвЂР РЋР РЏ",
                Toast.LENGTH_SHORT
        ).show();
    }

    private void requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                || ReminderScheduler.canScheduleExactAlarms(this)) {
            Toast.makeText(this, "Р В РЎС›Р В РЎвЂўР РЋРІР‚РЋР В Р вЂ¦Р РЋРІР‚в„–Р В Р’Вµ Р В Р вЂ¦Р В Р’В°Р В РЎвЂ”Р В РЎвЂўР В РЎВР В РЎвЂР В Р вЂ¦Р В Р’В°Р В Р вЂ¦Р В РЎвЂР РЋР РЏ Р РЋРЎвЂњР В Р’В¶Р В Р’Вµ Р РЋР вЂљР В Р’В°Р В Р’В·Р РЋР вЂљР В Р’ВµР РЋРІвЂљВ¬Р В Р’ВµР В Р вЂ¦Р РЋРІР‚в„–", Toast.LENGTH_SHORT).show();
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
                ? "markdown-Р РЋРІР‚С›Р В Р’В°Р В РІвЂћвЂ“Р В Р’В»"
                : "UI-Р РЋРІР‚С›Р В РЎвЂўР РЋР вЂљР В РЎВР В Р’В°";
    }

    private String formatThemeMode(String mode) {
        if (ThemePreferences.MODE_LIGHT.equals(mode)) {
            return "Р РЋР С“Р В Р вЂ Р В Р’ВµР РЋРІР‚С™Р В Р’В»Р В Р’В°Р РЋР РЏ";
        }
        if (ThemePreferences.MODE_DARK.equals(mode)) {
            return "Р РЋРІР‚С™Р В Р’ВµР В РЎВР В Р вЂ¦Р В Р’В°Р РЋР РЏ";
        }
        return "Р РЋР С“Р В РЎвЂР РЋР С“Р РЋРІР‚С™Р В Р’ВµР В РЎВР В Р вЂ¦Р В Р’В°Р РЋР РЏ";
    }

    private void updateStatus() {
        if (statusText == null) {
            return;
        }
        String cachedAt = TaskCache.getSavedAt(this);
        String latestError = ErrorLog.latest(this);
        StringBuilder status = new StringBuilder("Р В Р’В Р В Р’ВµР В Р’В¶Р В РЎвЂР В РЎВ: " + TaskSourceManager.storageModeLabel(this)
                + "\nР В Р’ВР РЋР С“Р РЋРІР‚С™Р В РЎвЂўР РЋРІР‚РЋР В Р вЂ¦Р В РЎвЂР В РЎвЂќ: " + compactName(TaskSourceManager.activeSourceLabel(this))
                + " Р вЂ™Р’В· Р РЋРІР‚С›Р В Р’В°Р В РІвЂћвЂ“Р В Р’В»Р В РЎвЂўР В Р вЂ /Р В РЎвЂР РЋР С“Р РЋРІР‚С™Р В РЎвЂўР РЋРІР‚РЋР В Р вЂ¦Р В РЎвЂР В РЎвЂќР В РЎвЂўР В Р вЂ : " + TaskSourceManager.activeSourceCount(this)
                + " Р вЂ™Р’В· Р В Р’В·Р В Р’В°Р В РЎвЂ”Р В РЎвЂР РЋР С“Р РЋР Р‰: " + (TaskSourceManager.canWriteActiveSource(this) ? "Р В РўвЂР В Р’В°" : "Р В Р вЂ¦Р В Р’ВµР РЋРІР‚С™")
                + "\nР В Р’В¤Р В РЎвЂР В Р’В»Р РЋР Р‰Р РЋРІР‚С™Р РЋР вЂљ: " + UserPreferences.getTaskFilterLabel(this)
                + "\nР В РЎСљР В Р’В°Р В РЎвЂ”Р В РЎвЂўР В РЎВР В РЎвЂР В Р вЂ¦Р В Р’В°Р В Р вЂ¦Р В РЎвЂР РЋР РЏ: Р РЋРІР‚С™Р В РЎвЂўР РЋРІР‚РЋР В Р вЂ¦Р РЋРІР‚в„–Р В Р’Вµ "
                + (ReminderScheduler.canScheduleExactAlarms(this) ? "Р В РўвЂР В Р’В°" : "Р В Р вЂ¦Р В Р’ВµР РЋРІР‚С™")
                + " Р вЂ™Р’В· Р В РЎвЂўР РЋРІР‚С™Р В Р’В»Р В РЎвЂўР В Р’В¶Р В РЎвЂР РЋРІР‚С™Р РЋР Р‰ " + ActionPreferences.getSnoozeMinutes(this) + " Р В РЎВР В РЎвЂР В Р вЂ¦."
                + " Р вЂ™Р’В· Р В РЎвЂ”Р РЋР вЂљР В РЎвЂўР РЋР С“Р РЋР вЂљР В РЎвЂўР РЋРІР‚РЋР В РЎвЂќР В Р’В° +" + ActionPreferences.getOverdueGraceMinutes(this) + " Р В РЎВР В РЎвЂР В Р вЂ¦."
                + " Р вЂ™Р’В· Р РЋР С“Р РЋРІР‚РЋР В Р’ВµР РЋРІР‚С™Р РЋРІР‚РЋР В РЎвЂР В РЎвЂќ: " + (ActionPreferences.shouldRecordSnoozeCount(this) ? "Р В Р вЂ Р В РЎвЂќР В Р’В»." : "Р В Р вЂ Р РЋРІР‚в„–Р В РЎвЂќР В Р’В».")
                + "\nР В Р’В Р В Р’ВµР В РўвЂР В Р’В°Р В РЎвЂќР РЋРІР‚С™Р В РЎвЂР РЋР вЂљР В РЎвЂўР В Р вЂ Р В Р’В°Р В Р вЂ¦Р В РЎвЂР В Р’Вµ: " + formatEditMode(EditPreferences.getEditMode(this))
                + " Р вЂ™Р’В· Р РЋРІР‚С™Р В Р’ВµР В РЎВР В Р’В°: " + formatThemeMode(ThemePreferences.getThemeMode(this))
                + "\nР В РІР‚СљР РЋР вЂљР РЋРЎвЂњР В РЎвЂ”Р В РЎвЂ”Р В РЎвЂР РЋР вЂљР В РЎвЂўР В Р вЂ Р В РЎвЂќР В Р’В°: " + UserPreferences.getGroupingModeLabel(this)
                + "\nР В РЎв„ўР РЋР РЉР РЋРІвЂљВ¬ Р В Р’В·Р В Р’В°Р В РўвЂР В Р’В°Р РЋРІР‚РЋ: " + TaskCache.getCachedTaskCount(this)
                + (cachedAt == null ? "" : " Р вЂ™Р’В· " + cachedAt));
        if (latestError != null) {
            status.append("\nР В РЎСџР В РЎвЂўР РЋР С“Р В Р’В»Р В Р’ВµР В РўвЂР В Р вЂ¦Р РЋР РЏР РЋР РЏ Р В РЎвЂўР РЋРІвЂљВ¬Р В РЎвЂР В Р’В±Р В РЎвЂќР В Р’В° Р В Р’В·Р В Р’В°Р В РЎвЂ”Р В РЎвЂР РЋР С“Р В Р’В°Р В Р вЂ¦Р В Р’В° Р В Р вЂ  Р В Р’В»Р В РЎвЂўР В РЎвЂ“.");
        }
        statusText.setText(status.toString());
    }

    private void updateActiveFilterButton() {
        activeFilterButton.setText(UserPreferences.isActiveOnly(this)
                ? "Р В РІР‚СљР В Р’В»Р В Р’В°Р В Р вЂ Р В Р вЂ¦Р РЋРІР‚в„–Р В РІвЂћвЂ“ Р РЋР РЉР В РЎвЂќР РЋР вЂљР В Р’В°Р В Р вЂ¦: Р РЋРІР‚С™Р В РЎвЂўР В Р’В»Р РЋР Р‰Р В РЎвЂќР В РЎвЂў Р В Р’В°Р В РЎвЂќР РЋРІР‚С™Р В РЎвЂР В Р вЂ Р В Р вЂ¦Р РЋРІР‚в„–Р В Р’Вµ"
                : "Р В РІР‚СљР В Р’В»Р В Р’В°Р В Р вЂ Р В Р вЂ¦Р РЋРІР‚в„–Р В РІвЂћвЂ“ Р РЋР РЉР В РЎвЂќР РЋР вЂљР В Р’В°Р В Р вЂ¦: Р В Р вЂ Р РЋР С“Р В Р’Вµ Р В Р’В·Р В Р’В°Р В РўвЂР В Р’В°Р РЋРІР‚РЋР В РЎвЂ");
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
        chevron.setText("Р Р†Р вЂљРЎвЂќ");
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
        state.setText(selected ? "Р В РЎвЂ™Р В РЎвЂќР РЋРІР‚С™Р В РЎвЂР В Р вЂ Р В Р вЂ¦Р В РЎвЂў" : "");
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
            return "Р В Р вЂ¦Р В Р’Вµ Р В Р вЂ Р РЋРІР‚в„–Р В Р’В±Р РЋР вЂљР В Р’В°Р В Р вЂ¦";
        }
        String value = rawName.trim();
        if (value.startsWith("Р В РЎСџР В Р’В°Р В РЎвЂ”Р В РЎвЂќР В Р’В°:")) {
            value = value.substring("Р В РЎСџР В Р’В°Р В РЎвЂ”Р В РЎвЂќР В Р’В°:".length()).trim();
        } else if (value.startsWith("Р В Р’В¤Р В Р’В°Р В РІвЂћвЂ“Р В Р’В»:")) {
            value = value.substring("Р В Р’В¤Р В Р’В°Р В РІвЂћвЂ“Р В Р’В»:".length()).trim();
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

