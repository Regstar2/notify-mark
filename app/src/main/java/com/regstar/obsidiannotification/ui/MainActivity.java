package com.regstar.obsidiannotification.ui;

import com.regstar.obsidiannotification.R;

import com.regstar.obsidiannotification.core.reminders.*;
import com.regstar.obsidiannotification.core.source.*;
import com.regstar.obsidiannotification.core.tasks.*;
import com.regstar.obsidiannotification.debug.*;
import com.regstar.obsidiannotification.prefs.*;
import com.regstar.obsidiannotification.support.ErrorLog;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.snackbar.Snackbar;

import java.io.IOException;
import java.time.Duration;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Main entry point for the app.
 *
 * <p>This activity owns the task list screen, the calendar screen, top-level
 * filters, drawer navigation, and the in-memory selection/expanded state used
 * by task interactions. It intentionally sits above parser, storage, and
 * reminder layers and should coordinate them rather than re-implement them.</p>
 */
public final class MainActivity extends AppCompatActivity {
    private static final int SECTION_TASKS = 0;
    private static final int SECTION_CALENDAR = 1;
    private static final int CALENDAR_MODE_WEEK = 0;
    private static final int CALENDAR_MODE_MONTH = 1;
    private static final int CALENDAR_MODE_YEAR = 2;
    private static final int REQUEST_OPEN_NOTE = 1001;
    private static final int REQUEST_NOTIFICATIONS = 1002;
    private static final int REQUEST_EDIT_TASK = 1003;
    private static final int REQUEST_SOURCE_MANAGEMENT = 1004;
    private static final int REQUEST_ONBOARDING = 1005;
    private static final long FOREGROUND_REFRESH_INTERVAL_MS = 15_000L;
    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final DateTimeFormatter CALENDAR_MONTH_FORMAT =
            DateTimeFormatter.ofPattern("LLLL yyyy", new Locale("ru"));
    private static final DateTimeFormatter CALENDAR_DAY_HEADER_FORMAT =
            DateTimeFormatter.ofPattern("d MMMM, EEEE", new Locale("ru"));
    private static final DateTimeFormatter CALENDAR_WEEK_DAY_FORMAT =
            DateTimeFormatter.ofPattern("d MMMM", new Locale("ru"));
    private static final DateTimeFormatter CALENDAR_MONTH_NAME_FORMAT =
            DateTimeFormatter.ofPattern("LLLL", new Locale("ru"));

    private Uri noteUri;
    private TextView statusText;
    private TextView nextReminderText;
    private TextView sourceTitleText;
    private TextView sourceMetaText;
    private TextView sourceStatsText;
    private TextView sourceErrorText;
    private LinearLayout sourceStatsRow;
    private LinearLayout groupFilterRow;
    private View groupFilterContainerView;
    private TextView nextReminderTimeText;
    private TextView nextReminderTitleText;
    private TextView nextReminderMetaText;
    private TextView taskSectionTitleText;
    private View refreshButton;
    private LinearLayout topAppBar;
    private View addFabButton;
    private Button activeFilterButton;
    private Button allFilterButton;
    private Button overdueFilterButton;
    private Button completedFilterButton;
    private Button notificationPermissionButton;
    private Button exactAlarmPermissionButton;
    private LinearLayout taskList;
    private FrameLayout drawerLayer;
    private View drawerScrim;
    private View drawerPanel;
    private FrameLayout appRootContainer;
    private Snackbar snackbarView;
    private View bottomBarView;
    private FrameLayout filterSheetLayer;
    private View filterSheetScrim;
    private View filterSheetPanel;
    private LinearLayout filterSheetOptions;
    private List<ObsidianTask> latestTasks = new ArrayList<>();
    private final Set<String> selectedTaskKeys = new LinkedHashSet<>();
    private final Set<String> expandedTaskKeys = new LinkedHashSet<>();
    private YearMonth displayedCalendarMonth = YearMonth.now();
    private LocalDate selectedCalendarDate = LocalDate.now();
    private int calendarMode = CALENDAR_MODE_MONTH;
    private int selectedSection = SECTION_TASKS;
    private boolean drawerOpen;
    private boolean filterSheetOpen;
    private boolean showTaskSourceNames;
    private boolean renderedShowSourceOnMain;
    private final Handler noteRefreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable noteRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            refreshNoteIfChanged();
            noteRefreshHandler.postDelayed(this, FOREGROUND_REFRESH_INTERVAL_MS);
        }
    };
    private String renderedFingerprint;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemePreferences.apply(this);
        super.onCreate(savedInstanceState);

        ReminderScheduler.ensureNotificationChannel(this);

        try {
            TaskSourceManager.ensureReady(this);
        } catch (IOException exception) {
            ErrorLog.record(this, "Р В РЎСљР В Р’Вµ Р РЋРЎвЂњР В РўвЂР В Р’В°Р В Р’В»Р В РЎвЂўР РЋР С“Р РЋР Р‰ Р В РЎвЂР В Р вЂ¦Р В РЎвЂР РЋРІР‚В Р В РЎвЂР В Р’В°Р В Р’В»Р В РЎвЂР В Р’В·Р В РЎвЂР РЋР вЂљР В РЎвЂўР В Р вЂ Р В Р’В°Р РЋРІР‚С™Р РЋР Р‰ Р В РЎвЂР РЋР С“Р РЋРІР‚С™Р В РЎвЂўР РЋРІР‚РЋР В Р вЂ¦Р В РЎвЂР В РЎвЂќ Р В Р’В·Р В Р’В°Р В РўвЂР В Р’В°Р РЋРІР‚РЋ", exception);
        }

        noteUri = TaskSourceManager.getActiveSourceUri(this);

        buildUi();
        updateNotificationPermissionUi();
        updateExactAlarmPermissionUi();
        requestNotificationPermissionIfNeeded();

        if (noteUri == null) {
            NoteChangeMonitor.cancel(this);
            ReminderScheduler.cancelScheduled(this);
            setStatus(TaskSourceManager.getStorageMode(this) == TaskStorageMode.EXTERNAL_MARKDOWN_STORAGE
                    ? "Р В РЎСџР В РЎвЂўР В РўвЂР В РЎвЂќР В Р’В»Р РЋР вЂ№Р РЋРІР‚РЋР В РЎвЂР РЋРІР‚С™Р В Р’Вµ Р В Р вЂ Р В Р вЂ¦Р В Р’ВµР РЋРІвЂљВ¬Р В Р вЂ¦Р РЋР вЂ№Р РЋР вЂ№ markdown-Р В РЎвЂ”Р В Р’В°Р В РЎвЂ”Р В РЎвЂќР РЋРЎвЂњ Р В РЎвЂР В Р’В»Р В РЎвЂ Р В Р’В·Р В Р’В°Р В РЎВР В Р’ВµР РЋРІР‚С™Р В РЎвЂќР РЋРЎвЂњ."
                    : "Р В РЎСљР В Р’Вµ Р РЋРЎвЂњР В РўвЂР В Р’В°Р В Р’В»Р В РЎвЂўР РЋР С“Р РЋР Р‰ Р В РЎвЂўР РЋРІР‚С™Р В РЎвЂќР РЋР вЂљР РЋРІР‚в„–Р РЋРІР‚С™Р РЋР Р‰ Р В Р вЂ Р РЋР С“Р РЋРІР‚С™Р РЋР вЂљР В РЎвЂўР В Р’ВµР В Р вЂ¦Р В Р вЂ¦Р В РЎвЂўР В Р’Вµ Р РЋРІР‚В¦Р РЋР вЂљР В Р’В°Р В Р вЂ¦Р В РЎвЂР В Р’В»Р В РЎвЂР РЋРІР‚В°Р В Р’Вµ Р В Р’В·Р В Р’В°Р В РўвЂР В Р’В°Р РЋРІР‚РЋ.");
            setNextReminder(null);
            renderEmptyState(TaskSourceManager.getStorageMode(this) == TaskStorageMode.EXTERNAL_MARKDOWN_STORAGE
                    ? "Р В РІР‚вЂќР В Р’В°Р В РўвЂР В Р’В°Р РЋРІР‚РЋР В РЎвЂ Р В РЎвЂ”Р В РЎвЂўР РЋР РЏР В Р вЂ Р РЋР РЏР РЋРІР‚С™Р РЋР С“Р РЋР РЏ Р В РЎвЂ”Р В РЎвЂўР РЋР С“Р В Р’В»Р В Р’Вµ Р В Р вЂ Р РЋРІР‚в„–Р В Р’В±Р В РЎвЂўР РЋР вЂљР В Р’В° Р В Р вЂ Р В Р вЂ¦Р В Р’ВµР РЋРІвЂљВ¬Р В Р вЂ¦Р В Р’ВµР В РЎвЂ“Р В РЎвЂў Р В РЎвЂР РЋР С“Р РЋРІР‚С™Р В РЎвЂўР РЋРІР‚РЋР В Р вЂ¦Р В РЎвЂР В РЎвЂќР В Р’В°."
                    : "Р В РЎСџР В РЎвЂўР В РЎвЂ”Р РЋР вЂљР В РЎвЂўР В Р’В±Р РЋРЎвЂњР В РІвЂћвЂ“Р РЋРІР‚С™Р В Р’Вµ Р В РЎвЂўР РЋРІР‚С™Р В РЎвЂќР РЋР вЂљР РЋРІР‚в„–Р РЋРІР‚С™Р РЋР Р‰ Р В РЎвЂ”Р РЋР вЂљР В РЎвЂР В Р’В»Р В РЎвЂўР В Р’В¶Р В Р’ВµР В Р вЂ¦Р В РЎвЂР В Р’Вµ Р РЋР С“Р В Р вЂ¦Р В РЎвЂўР В Р вЂ Р В Р’В° Р В РЎвЂР В Р’В»Р В РЎвЂ Р В РЎвЂ”Р В Р’ВµР РЋР вЂљР В Р’ВµР В РЎвЂќР В Р’В»Р РЋР вЂ№Р РЋРІР‚РЋР В РЎвЂР РЋРІР‚С™Р РЋР Р‰Р РЋР С“Р РЋР РЏ Р В Р вЂ¦Р В Р’В° Р В Р вЂ Р В Р вЂ¦Р В Р’ВµР РЋРІвЂљВ¬Р В Р вЂ¦Р В РЎвЂР В РІвЂћвЂ“ Р В РЎвЂР РЋР С“Р РЋРІР‚С™Р В РЎвЂўР РЋРІР‚РЋР В Р вЂ¦Р В РЎвЂР В РЎвЂќ.");
            showOnboardingIfNeeded();
        } else {
            readAndRenderNote();
            NoteChangeMonitor.ensureScheduled(this);
            showOnboardingIfNeeded();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (renderedShowSourceOnMain != UserPreferences.shouldShowSourceOnMain(this)) {
            buildUi();
            updateNotificationPermissionUi();
            updateExactAlarmPermissionUi();
        }
        if (exactAlarmPermissionButton != null) {
            updateExactAlarmPermissionUi();
        }
        noteUri = TaskSourceManager.getActiveSourceUri(this);
        if (noteUri != null) {
            if (refreshButton != null) {
                refreshButton.setEnabled(true);
            }
            updateActiveFilterButton();
            readAndRenderNote();
            startForegroundNotePolling();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopForegroundNotePolling();
    }

    @Override
    public void onBackPressed() {
        if (filterSheetOpen) {
            closeTaskFilterSheet();
            return;
        }
        if (drawerOpen) {
            closeDrawer();
            return;
        }
        if (isSelectionMode()) {
            exitSelectionMode();
            return;
        }
        if (selectedSection == SECTION_CALENDAR) {
            selectedSection = SECTION_TASKS;
            rebuildAndRenderCurrentSection();
            return;
        }
        super.onBackPressed();
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_EDIT_TASK) {
            if (resultCode == RESULT_OK) {
                readAndRenderNote();
            }
            return;
        }

        if (requestCode == REQUEST_SOURCE_MANAGEMENT) {
            noteUri = TaskSourceManager.getActiveSourceUri(this);
            if (noteUri == null) {
                NoteChangeMonitor.cancel(this);
                ReminderScheduler.cancelScheduled(this);
                setStatus("Р В Р’ВР РЋР С“Р РЋРІР‚С™Р В РЎвЂўР РЋРІР‚РЋР В Р вЂ¦Р В РЎвЂР В РЎвЂќ Р В Р вЂ¦Р В Р’Вµ Р В Р вЂ Р РЋРІР‚в„–Р В Р’В±Р РЋР вЂљР В Р’В°Р В Р вЂ¦.");
                setNextReminder(null);
                if (selectedSection == SECTION_CALENDAR) {
                    renderCalendar(new ArrayList<>());
                    return;
                }
                renderEmptyState("Р В РІР‚вЂќР В Р’В°Р В РўвЂР В Р’В°Р РЋРІР‚РЋР В РЎвЂ Р В РЎвЂ”Р В РЎвЂўР РЋР РЏР В Р вЂ Р РЋР РЏР РЋРІР‚С™Р РЋР С“Р РЋР РЏ Р В Р’В·Р В РўвЂР В Р’ВµР РЋР С“Р РЋР Р‰ Р В РЎвЂ”Р В РЎвЂўР РЋР С“Р В Р’В»Р В Р’Вµ Р В Р вЂ Р РЋРІР‚в„–Р В Р’В±Р В РЎвЂўР РЋР вЂљР В Р’В° Р В РЎвЂР РЋР С“Р РЋРІР‚С™Р В РЎвЂўР РЋРІР‚РЋР В Р вЂ¦Р В РЎвЂР В РЎвЂќР В Р’В°.");
            } else {
                readAndRenderNote();
                NoteChangeMonitor.ensureScheduled(this);
            }
            return;
        }

        if (requestCode == REQUEST_ONBOARDING) {
            noteUri = TaskSourceManager.getActiveSourceUri(this);
            if (noteUri != null) {
                readAndRenderNote();
                NoteChangeMonitor.ensureScheduled(this);
            }
            return;
        }

        if (requestCode != REQUEST_OPEN_NOTE || resultCode != RESULT_OK || data == null) {
            return;
        }

        Uri selectedUri = data.getData();
        if (selectedUri == null) {
            return;
        }

        try {
            int persistableFlags = data.getFlags()
                    & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            if (persistableFlags == 0) {
                persistableFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
            }
            getContentResolver().takePersistableUriPermission(
                    selectedUri,
                    persistableFlags
            );
        } catch (SecurityException ignored) {
            // Some providers grant temporary read access only. The current session can still read it.
        }

        noteUri = selectedUri;
        NoteStore.saveNoteUri(this, selectedUri);
        TaskSourceManager.useExternalStorage(this);
        OnboardingPreferences.markCompleted(this);
        readAndRenderNote();
        NoteChangeMonitor.ensureScheduled(this);
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_NOTIFICATIONS) {
            return;
        }

        updateNotificationPermissionUi();
        if (hasNotificationPermission()) {
            if (noteUri == null) {
                setStatus("Р В Р в‚¬Р В Р вЂ Р В Р’ВµР В РўвЂР В РЎвЂўР В РЎВР В Р’В»Р В Р’ВµР В Р вЂ¦Р В РЎвЂР РЋР РЏ Р РЋР вЂљР В Р’В°Р В Р’В·Р РЋР вЂљР В Р’ВµР РЋРІвЂљВ¬Р В Р’ВµР В Р вЂ¦Р РЋРІР‚в„–. Р В РІР‚в„ўР РЋРІР‚в„–Р В Р’В±Р В Р’ВµР РЋР вЂљР В РЎвЂР РЋРІР‚С™Р В Р’Вµ markdown-Р РЋРІР‚С›Р В Р’В°Р В РІвЂћвЂ“Р В Р’В» Р РЋР С“ Р В Р’В·Р В Р’В°Р В РўвЂР В Р’В°Р РЋРІР‚РЋР В Р’В°Р В РЎВР В РЎвЂ Obsidian.");
            } else {
                readAndRenderNote();
                NoteChangeMonitor.ensureScheduled(this);
            }
        } else {
            ReminderScheduler.cancelScheduled(this);
            setStatus("Р В Р’В Р В Р’В°Р В Р’В·Р РЋР вЂљР В Р’ВµР РЋРІвЂљВ¬Р В Р’ВµР В Р вЂ¦Р В РЎвЂР В Р’Вµ Р В Р вЂ¦Р В Р’В° Р РЋРЎвЂњР В Р вЂ Р В Р’ВµР В РўвЂР В РЎвЂўР В РЎВР В Р’В»Р В Р’ВµР В Р вЂ¦Р В РЎвЂР РЋР РЏ Р В Р вЂ¦Р В Р’Вµ Р В Р вЂ Р РЋРІР‚в„–Р В РўвЂР В Р’В°Р В Р вЂ¦Р В РЎвЂў. Р В РЎСљР В Р’В°Р В РЎвЂ”Р В РЎвЂўР В РЎВР В РЎвЂР В Р вЂ¦Р В Р’В°Р В Р вЂ¦Р В РЎвЂР РЋР РЏ Р В Р вЂ¦Р В Р’Вµ Р В Р’В±Р РЋРЎвЂњР В РўвЂР РЋРЎвЂњР РЋРІР‚С™ Р В РЎвЂ”Р В РЎвЂўР В РЎвЂќР В Р’В°Р В Р’В·Р В Р’В°Р В Р вЂ¦Р РЋРІР‚в„–.");
            setNextReminder(null);
        }
    }

    private void buildLegacyUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(20));
        root.setBackgroundColor(getColor(R.color.background));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText(getString(R.string.app_name));
        title.setTextColor(getColor(R.color.text_primary));
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT
                , 1
        ));

        ImageButton settingsButton = new ImageButton(this);
        settingsButton.setImageResource(R.drawable.ic_settings);
        settingsButton.setContentDescription("Р В РЎСљР В Р’В°Р РЋР С“Р РЋРІР‚С™Р РЋР вЂљР В РЎвЂўР В РІвЂћвЂ“Р В РЎвЂќР В РЎвЂ");
        settingsButton.setBackgroundColor(Color.TRANSPARENT);
        settingsButton.setOnClickListener(view -> openSettings());
        header.addView(settingsButton, new LinearLayout.LayoutParams(dp(48), dp(48)));

        root.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView subtitle = new TextView(this);
        subtitle.setText("Р В Р’В§Р В РЎвЂР РЋРІР‚С™Р В Р’В°Р В Р’ВµР РЋРІР‚С™ markdown-Р РЋРЎвЂњР В Р вЂ Р В Р’ВµР В РўвЂР В РЎвЂўР В РЎВР В Р’В»Р В Р’ВµР В Р вЂ¦Р В РЎвЂР РЋР РЏ Р В РЎвЂР В Р’В· Obsidian Р В РЎвЂ Р В РЎвЂ”Р В Р’В»Р В Р’В°Р В Р вЂ¦Р В РЎвЂР РЋР вЂљР РЋРЎвЂњР В Р’ВµР РЋРІР‚С™ Р В Р’В»Р В РЎвЂўР В РЎвЂќР В Р’В°Р В Р’В»Р РЋР Р‰Р В Р вЂ¦Р РЋРІР‚в„–Р В Р’Вµ Р В Р вЂ¦Р В Р’В°Р В РЎвЂ”Р В РЎвЂўР В РЎВР В РЎвЂР В Р вЂ¦Р В Р’В°Р В Р вЂ¦Р В РЎвЂР РЋР РЏ.");
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

        Button legacyRefreshButton = new Button(this);
        legacyRefreshButton.setText(R.string.refresh_note);
        legacyRefreshButton.setAllCaps(false);
        legacyRefreshButton.setEnabled(noteUri != null);
        legacyRefreshButton.setOnClickListener(view -> readAndRenderNote());
        refreshButton = legacyRefreshButton;
        LinearLayout.LayoutParams refreshParams = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        );
        refreshParams.setMargins(dp(10), 0, 0, 0);
        actions.addView(legacyRefreshButton, refreshParams);

        root.addView(actions, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        Button addTaskButton = new Button(this);
        addTaskButton.setText("Р В РІР‚СњР В РЎвЂўР В Р’В±Р В Р’В°Р В Р вЂ Р В РЎвЂР РЋРІР‚С™Р РЋР Р‰ Р РЋРЎвЂњР В Р вЂ Р В Р’ВµР В РўвЂР В РЎвЂўР В РЎВР В Р’В»Р В Р’ВµР В Р вЂ¦Р В РЎвЂР В Р’Вµ");
        addTaskButton.setAllCaps(false);
        addTaskButton.setOnClickListener(view -> openTaskEditor(null));
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        addParams.setMargins(0, 0, 0, dp(12));
        root.addView(addTaskButton, addParams);

        activeFilterButton = new Button(this);
        activeFilterButton.setAllCaps(false);
        activeFilterButton.setOnClickListener(view -> {
            UserPreferences.setActiveOnly(this, !UserPreferences.isActiveOnly(this));
            updateActiveFilterButton();
            readAndRenderNote();
        });
        updateActiveFilterButton();
        LinearLayout.LayoutParams filterParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        filterParams.setMargins(0, 0, 0, dp(12));
        root.addView(activeFilterButton, filterParams);

        notificationPermissionButton = new Button(this);
        notificationPermissionButton.setText("Р В Р’В Р В Р’В°Р В Р’В·Р РЋР вЂљР В Р’ВµР РЋРІвЂљВ¬Р В РЎвЂР РЋРІР‚С™Р РЋР Р‰ Р РЋРЎвЂњР В Р вЂ Р В Р’ВµР В РўвЂР В РЎвЂўР В РЎВР В Р’В»Р В Р’ВµР В Р вЂ¦Р В РЎвЂР РЋР РЏ");
        notificationPermissionButton.setAllCaps(false);
        notificationPermissionButton.setOnClickListener(view -> requestNotificationPermission());
        LinearLayout.LayoutParams permissionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        permissionParams.setMargins(0, 0, 0, dp(12));
        root.addView(notificationPermissionButton, permissionParams);

        exactAlarmPermissionButton = new Button(this);
        exactAlarmPermissionButton.setText("Р В Р’В Р В Р’В°Р В Р’В·Р РЋР вЂљР В Р’ВµР РЋРІвЂљВ¬Р В РЎвЂР РЋРІР‚С™Р РЋР Р‰ Р РЋРІР‚С™Р В РЎвЂўР РЋРІР‚РЋР В Р вЂ¦Р РЋРІР‚в„–Р В Р’Вµ Р В Р вЂ¦Р В Р’В°Р В РЎвЂ”Р В РЎвЂўР В РЎВР В РЎвЂР В Р вЂ¦Р В Р’В°Р В Р вЂ¦Р В РЎвЂР РЋР РЏ");
        exactAlarmPermissionButton.setAllCaps(false);
        exactAlarmPermissionButton.setOnClickListener(view -> requestExactAlarmPermission());
        LinearLayout.LayoutParams exactAlarmParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        exactAlarmParams.setMargins(0, 0, 0, dp(12));
        root.addView(exactAlarmPermissionButton, exactAlarmParams);

        statusText = new TextView(this);
        statusText.setTextColor(getColor(R.color.text_secondary));
        statusText.setTextSize(14);
        statusText.setPadding(0, 0, 0, dp(8));
        root.addView(statusText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        nextReminderText = new TextView(this);
        nextReminderText.setTextColor(getColor(R.color.text_primary));
        nextReminderText.setTextSize(15);
        nextReminderText.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        nextReminderText.setPadding(0, 0, 0, dp(12));
        root.addView(nextReminderText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        taskList = new LinearLayout(this);
        taskList.setOrientation(LinearLayout.VERTICAL);
        root.addView(taskList, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        ScrollView screenScroll = new ScrollView(this);
        screenScroll.setFillViewport(true);
        screenScroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        setContentView(screenScroll);
    }

    private void buildUi() {
        renderedShowSourceOnMain = UserPreferences.shouldShowSourceOnMain(this);
        sourceTitleText = null;
        sourceMetaText = null;
        sourceStatsText = null;
        sourceErrorText = null;
        sourceStatsRow = null;
        groupFilterRow = null;
        taskSectionTitleText = null;
        taskList = null;
        topAppBar = null;
        addFabButton = null;
        nextReminderTimeText = null;
        nextReminderTitleText = null;
        nextReminderMetaText = null;
        nextReminderText = null;
        notificationPermissionButton = null;
        exactAlarmPermissionButton = null;
        drawerOpen = false;
        appRootContainer = null;
        snackbarView = null;
        filterSheetOpen = false;
        filterSheetLayer = null;
        filterSheetScrim = null;
        filterSheetPanel = null;
        filterSheetOptions = null;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(14), dp(16), dp(152));
        root.setBackgroundColor(getColor(R.color.background));

        root.addView(createTopAppBar(), fullWidth());
        if (selectedSection == SECTION_CALENDAR) {
            addCalendarScreenContent(root);
        } else {
            addTaskScreenContent(root);
        }

        ScrollView screenScroll = new ScrollView(this);
        screenScroll.setFillViewport(true);
        screenScroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(getColor(R.color.background));
        frame.addView(screenScroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        LinearLayout bottomOverlay = createBottomOverlay();
        FrameLayout.LayoutParams bottomParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.BOTTOM
        );
        frame.addView(bottomOverlay, bottomParams);

        FrameLayout appRoot = new FrameLayout(this);
        appRootContainer = appRoot;
        appRoot.addView(frame, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        appRoot.addView(createDrawerLayer(), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        appRoot.addView(createTaskFilterSheetLayer(), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        setContentView(appRoot);
    }

    private LinearLayout createTopAppBar() {
        LinearLayout appBar = new LinearLayout(this);
        topAppBar = appBar;
        appBar.setOrientation(LinearLayout.HORIZONTAL);
        appBar.setGravity(android.view.Gravity.CENTER_VERTICAL);
        appBar.setPadding(0, 0, 0, dp(10));
        populateTopAppBar(appBar);
        return appBar;
    }

    private void refreshTopAppBar() {
        if (topAppBar == null) {
            return;
        }
        populateTopAppBar(topAppBar);
    }

    private void populateTopAppBar(LinearLayout appBar) {
        appBar.removeAllViews();
        if (selectedSection == SECTION_TASKS && isSelectionMode()) {
            populateSelectionTopAppBar(appBar);
            return;
        }
        populateDefaultTopAppBar(appBar);
    }

    private void populateDefaultTopAppBar(LinearLayout appBar) {
        ImageButton menuButton = createPlainIconButton(R.drawable.ic_menu, "Р В РЎвЂєР РЋРІР‚С™Р В РЎвЂќР РЋР вЂљР РЋРІР‚в„–Р РЋРІР‚С™Р РЋР Р‰ Р В РЎВР В Р’ВµР В Р вЂ¦Р РЋР вЂ№");
        menuButton.setOnClickListener(view -> openDrawer());
        appBar.addView(menuButton, new LinearLayout.LayoutParams(dp(40), dp(40)));

        TextView title = createText(selectedSection == SECTION_CALENDAR ? "Р В РЎв„ўР В Р’В°Р В Р’В»Р В Р’ВµР В Р вЂ¦Р В РўвЂР В Р’В°Р РЋР вЂљР РЋР Р‰" : "Р В РІР‚вЂќР В Р’В°Р В РўвЂР В Р’В°Р РЋРІР‚РЋР В РЎвЂ",
                selectedSection == SECTION_TASKS ? 19 : 21,
                R.color.text_primary,
                true);
        if (selectedSection == SECTION_TASKS) {
            taskSectionTitleText = title;
        }
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        );
        titleParams.setMargins(dp(8), 0, dp(8), 0);
        appBar.addView(title, titleParams);

        ImageButton topRefreshButton = createPlainIconButton(R.drawable.ic_refresh, "Р В РЎвЂєР В Р’В±Р В Р вЂ¦Р В РЎвЂўР В Р вЂ Р В РЎвЂР РЋРІР‚С™Р РЋР Р‰");
        topRefreshButton.setEnabled(noteUri != null);
        topRefreshButton.setOnClickListener(view -> refreshFromTopBar());
        refreshButton = topRefreshButton;
        appBar.addView(topRefreshButton, new LinearLayout.LayoutParams(dp(40), dp(40)));
        appBar.addView(createTaskFilterButton(), new LinearLayout.LayoutParams(dp(40), dp(40)));
    }

    private void populateSelectionTopAppBar(LinearLayout appBar) {
        ImageButton closeButton = createPlainIconButton(R.drawable.ic_close, "Р В Р Р‹Р В Р вЂ¦Р РЋР РЏР РЋРІР‚С™Р РЋР Р‰ Р В Р вЂ Р РЋРІР‚в„–Р В РўвЂР В Р’ВµР В Р’В»Р В Р’ВµР В Р вЂ¦Р В РЎвЂР В Р’Вµ");
        closeButton.setOnClickListener(view -> exitSelectionMode());
        appBar.addView(closeButton, new LinearLayout.LayoutParams(dp(40), dp(40)));

        TextView title = createText(selectedTaskKeys.size() + " Р В Р вЂ Р РЋРІР‚в„–Р В Р’В±Р РЋР вЂљР В Р’В°Р В Р вЂ¦Р В РЎвЂў",
                19,
                R.color.text_primary,
                true);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        );
        titleParams.setMargins(dp(8), 0, dp(8), 0);
        appBar.addView(title, titleParams);

        ImageButton doneButton = createPlainIconButton(R.drawable.ic_check, "Р В РЎвЂєР РЋРІР‚С™Р В РЎВР В Р’ВµР РЋРІР‚С™Р В РЎвЂР РЋРІР‚С™Р РЋР Р‰ Р В Р вЂ Р РЋРІР‚в„–Р В РЎвЂ”Р В РЎвЂўР В Р’В»Р В Р вЂ¦Р В Р’ВµР В Р вЂ¦Р В Р вЂ¦Р РЋРІР‚в„–Р В РЎВР В РЎвЂ");
        doneButton.setOnClickListener(view -> bulkMarkSelectedDone());
        appBar.addView(doneButton, new LinearLayout.LayoutParams(dp(40), dp(40)));

        ImageButton skipButton = createPlainIconButton(R.drawable.ic_skip, "Р В РЎСџР РЋР вЂљР В РЎвЂўР В РЎвЂ”Р РЋРЎвЂњР РЋР С“Р РЋРІР‚С™Р В РЎвЂР РЋРІР‚С™Р РЋР Р‰");
        skipButton.setOnClickListener(view -> bulkSkipSelected());
        appBar.addView(skipButton, new LinearLayout.LayoutParams(dp(40), dp(40)));

        ImageButton snoozeButton = createPlainIconButton(R.drawable.ic_clock, "Р В РЎвЂєР РЋРІР‚С™Р В Р’В»Р В РЎвЂўР В Р’В¶Р В РЎвЂР РЋРІР‚С™Р РЋР Р‰");
        snoozeButton.setOnClickListener(view -> bulkSnoozeSelected());
        appBar.addView(snoozeButton, new LinearLayout.LayoutParams(dp(40), dp(40)));

        ImageButton deleteButton = createPlainIconButton(R.drawable.ic_delete, "Р В Р в‚¬Р В РўвЂР В Р’В°Р В Р’В»Р В РЎвЂР РЋРІР‚С™Р РЋР Р‰");
        deleteButton.setOnClickListener(view -> confirmBulkDeleteSelected());
        appBar.addView(deleteButton, new LinearLayout.LayoutParams(dp(40), dp(40)));
    }

    private void addTaskScreenContent(LinearLayout root) {
        LinearLayout groupFilterContainer = new LinearLayout(this);
        groupFilterContainer.setOrientation(LinearLayout.HORIZONTAL);
        groupFilterContainer.setGravity(android.view.Gravity.CENTER_VERTICAL);
        groupFilterContainerView = groupFilterContainer;

        HorizontalScrollView groupScroll = new HorizontalScrollView(this);
        groupScroll.setHorizontalScrollBarEnabled(false);
        groupScroll.setVerticalScrollBarEnabled(false);
        groupFilterRow = new LinearLayout(this);
        groupFilterRow.setOrientation(LinearLayout.HORIZONTAL);
        groupScroll.addView(groupFilterRow, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        groupFilterContainer.addView(groupScroll, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        ));

        root.addView(groupFilterContainer, fullWidthWithBottomMargin());
        updateGroupFilterRow(new ArrayList<>());

        LinearLayout sourceCard = createSourceCard();
        if (noteUri == null || UserPreferences.shouldShowSourceOnMain(this)) {
            LinearLayout.LayoutParams sourceParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            sourceParams.setMargins(0, 0, 0, dp(12));
            root.addView(sourceCard, sourceParams);
        }

        notificationPermissionButton = createActionButton("Р В Р’В Р В Р’В°Р В Р’В·Р РЋР вЂљР В Р’ВµР РЋРІвЂљВ¬Р В РЎвЂР РЋРІР‚С™Р РЋР Р‰ Р РЋРЎвЂњР В Р вЂ Р В Р’ВµР В РўвЂР В РЎвЂўР В РЎВР В Р’В»Р В Р’ВµР В Р вЂ¦Р В РЎвЂР РЋР РЏ", true);
        notificationPermissionButton.setOnClickListener(view -> requestNotificationPermission());
        LinearLayout.LayoutParams permissionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(44)
        );
        permissionParams.setMargins(0, 0, 0, dp(10));
        root.addView(notificationPermissionButton, permissionParams);

        exactAlarmPermissionButton = createActionButton("Р В Р’В Р В Р’В°Р В Р’В·Р РЋР вЂљР В Р’ВµР РЋРІвЂљВ¬Р В РЎвЂР РЋРІР‚С™Р РЋР Р‰ Р РЋРІР‚С™Р В РЎвЂўР РЋРІР‚РЋР В Р вЂ¦Р РЋРІР‚в„–Р В Р’Вµ Р В Р вЂ¦Р В Р’В°Р В РЎвЂ”Р В РЎвЂўР В РЎВР В РЎвЂР В Р вЂ¦Р В Р’В°Р В Р вЂ¦Р В РЎвЂР РЋР РЏ", false);
        exactAlarmPermissionButton.setOnClickListener(view -> requestExactAlarmPermission());
        LinearLayout.LayoutParams exactAlarmParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(44)
        );
        exactAlarmParams.setMargins(0, 0, 0, dp(10));
        root.addView(exactAlarmPermissionButton, exactAlarmParams);

        taskList = new LinearLayout(this);
        taskList.setOrientation(LinearLayout.VERTICAL);
        taskList.setPadding(0, 0, 0, 0);
        taskList.setClipToPadding(false);
        root.addView(taskList, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
    }

    private void addCalendarScreenContent(LinearLayout root) {
        taskList = new LinearLayout(this);
        taskList.setOrientation(LinearLayout.VERTICAL);
        taskList.setPadding(0, 0, 0, 0);
        taskList.setClipToPadding(false);
        root.addView(taskList, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
    }

    private void rebuildAndRenderCurrentSection() {
        buildUi();
        updateNotificationPermissionUi();
        updateExactAlarmPermissionUi();
        if (noteUri == null) {
            setStatus("Р В Р’В¤Р В Р’В°Р В РІвЂћвЂ“Р В Р’В» Р В Р вЂ¦Р В Р’Вµ Р В Р вЂ Р РЋРІР‚в„–Р В Р’В±Р РЋР вЂљР В Р’В°Р В Р вЂ¦.");
            setNextReminder(null);
            if (selectedSection == SECTION_CALENDAR) {
                renderCalendar(new ArrayList<>());
            } else {
                renderEmptyState("Р В РЎСљР В Р’В°Р В Р’В¶Р В РЎВР В РЎвЂР РЋРІР‚С™Р В Р’Вµ Р вЂ™Р’В«Р В РІР‚в„ўР РЋРІР‚в„–Р В Р’В±Р РЋР вЂљР В Р’В°Р РЋРІР‚С™Р РЋР Р‰ Р В Р’В·Р В Р’В°Р В РЎВР В Р’ВµР РЋРІР‚С™Р В РЎвЂќР РЋРЎвЂњР вЂ™Р’В».");
            }
            return;
        }
        readAndRenderNote();
    }

    private Button createActionButton(String text, boolean primary) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT, primary ? Typeface.BOLD : Typeface.NORMAL);
        button.setTextColor(getColor(primary
                ? R.color.primary_button_text
                : R.color.secondary_button_text));
        button.setBackground(createRoundedBackground(
                getColor(primary ? R.color.primary_button_background : R.color.secondary_button_background),
                primary ? 0 : getColor(R.color.card_stroke),
                8
        ));
        return button;
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

    private ImageButton createPlainIconButton(int iconRes, String description) {
        ImageButton button = new ImageButton(this);
        button.setImageResource(iconRes);
        button.setContentDescription(description);
        button.setColorFilter(getColor(R.color.text_secondary));
        button.setPadding(dp(9), dp(9), dp(9), dp(9));
        button.setBackground(createRoundedBackground(Color.TRANSPARENT, 0, 18));
        return button;
    }

    private Button createFabButton() {
        Button button = createActionButton("+", true);
        button.setTextSize(24);
        button.setMinHeight(0);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinimumHeight(0);
        button.setPadding(0, 0, 0, dp(2));
        button.setBackground(createCircleBackground(getColor(R.color.primary_button_background)));
        return button;
    }

    private LinearLayout createBottomOverlay() {
        LinearLayout overlay = new LinearLayout(this);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setPadding(0, 0, 0, 0);

        if (selectedSection == SECTION_TASKS || selectedSection == SECTION_CALENDAR) {
            Button addFab = createFabButton();
            addFabButton = addFab;
            addFab.setOnClickListener(view -> {
                if (selectedSection == SECTION_CALENDAR) {
                    openTaskEditorForDate(selectedCalendarDate == null
                            ? LocalDate.now()
                            : selectedCalendarDate);
                } else {
                    openTaskEditor(null);
                }
            });
            LinearLayout.LayoutParams fabParams = new LinearLayout.LayoutParams(dp(48), dp(48));
            fabParams.gravity = android.view.Gravity.RIGHT;
            fabParams.setMargins(0, 0, dp(16), dp(10));
            overlay.addView(addFab, fabParams);
            updateFabVisibility();
        }

        LinearLayout bottomBar = new LinearLayout(this);
        bottomBarView = bottomBar;
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setGravity(android.view.Gravity.CENTER_VERTICAL);
        bottomBar.setPadding(0, 0, 0, 0);
        bottomBar.setBackground(createRoundedBackground(
                getColor(R.color.card_background),
                0,
                0
        ));

        bottomBar.addView(createBottomNavItem(
                "Р В РІР‚вЂќР В Р’В°Р В РўвЂР В Р’В°Р РЋРІР‚РЋР В РЎвЂ",
                R.drawable.ic_task_list,
                selectedSection == SECTION_TASKS,
                () -> {
                    if (selectedSection != SECTION_TASKS) {
                        selectedTaskKeys.clear();
                        selectedSection = SECTION_TASKS;
                        rebuildAndRenderCurrentSection();
                    }
                }
        ), new LinearLayout.LayoutParams(0, dp(60), 1));

        LinearLayout.LayoutParams calendarParams = new LinearLayout.LayoutParams(0, dp(60), 1);
        bottomBar.addView(createBottomNavItem(
                "Р В РЎв„ўР В Р’В°Р В Р’В»Р В Р’ВµР В Р вЂ¦Р В РўвЂР В Р’В°Р РЋР вЂљР РЋР Р‰",
                R.drawable.ic_calendar,
                selectedSection == SECTION_CALENDAR,
                () -> {
                    if (selectedSection != SECTION_CALENDAR) {
                        selectedTaskKeys.clear();
                        selectedSection = SECTION_CALENDAR;
                        rebuildAndRenderCurrentSection();
                    }
                }
        ), calendarParams);

        overlay.addView(bottomBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        return overlay;
    }

    private LinearLayout createBottomNavItem(
            String text,
            int iconRes,
            boolean selected,
            Runnable action
    ) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(android.view.Gravity.CENTER);
        item.setClickable(true);
        item.setPadding(dp(10), 0, dp(10), 0);
        item.setBackground(createRoundedBackground(
                getColor(selected ? R.color.chip_selected_background : R.color.card_background),
                0,
                0
        ));
        item.setOnClickListener(view -> action.run());

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(getColor(selected ? R.color.chip_selected_text : R.color.text_secondary));
        item.addView(icon, new LinearLayout.LayoutParams(dp(20), dp(20)));

        TextView label = createText(
                text,
                13,
                selected ? R.color.chip_selected_text : R.color.text_secondary,
                selected
        );
        label.setSingleLine(true);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        labelParams.setMargins(dp(8), 0, 0, 0);
        item.addView(label, labelParams);
        return item;
    }

    private FrameLayout createDrawerLayer() {
        int drawerWidth = dp(308);

        drawerLayer = new FrameLayout(this);
        drawerLayer.setVisibility(View.GONE);

        drawerScrim = new View(this);
        drawerScrim.setBackgroundColor(Color.argb(110, 0, 0, 0));
        drawerScrim.setAlpha(0f);
        drawerScrim.setOnClickListener(view -> closeDrawer());
        drawerLayer.addView(drawerScrim, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14), dp(18), dp(14), dp(14));
        panel.setBackground(createRightRoundedBackground(getColor(R.color.card_background), 24));
        panel.setTranslationX(-drawerWidth);

        panel.addView(createDrawerHeader(), fullWidthWithBottomMargin());

        panel.addView(createDrawerItem(
                R.drawable.ic_task_list,
                "Р В РІР‚вЂќР В Р’В°Р В РўвЂР В Р’В°Р РЋРІР‚РЋР В РЎвЂ",
                "",
                selectedSection == SECTION_TASKS,
                () -> {
                    closeDrawer();
                    if (selectedSection != SECTION_TASKS) {
                        selectedTaskKeys.clear();
                        selectedSection = SECTION_TASKS;
                        rebuildAndRenderCurrentSection();
                    }
                }
        ));
        panel.addView(createDrawerItem(
                R.drawable.ic_calendar,
                "Р В РЎв„ўР В Р’В°Р В Р’В»Р В Р’ВµР В Р вЂ¦Р В РўвЂР В Р’В°Р РЋР вЂљР РЋР Р‰",
                "",
                selectedSection == SECTION_CALENDAR,
                () -> {
                    closeDrawer();
                    if (selectedSection != SECTION_CALENDAR) {
                        selectedTaskKeys.clear();
                        selectedSection = SECTION_CALENDAR;
                        rebuildAndRenderCurrentSection();
                    }
                }
        ));
        panel.addView(createDrawerItem(
                R.drawable.ic_file,
                "Р В Р’ВР РЋР С“Р РЋРІР‚С™Р В РЎвЂўР РЋРІР‚РЋР В Р вЂ¦Р В РЎвЂР В РЎвЂќР В РЎвЂ",
                "Р В Р’В¤Р В Р’В°Р В РІвЂћвЂ“Р В Р’В»Р РЋРІР‚в„– Р В РЎвЂ Р В РЎвЂ”Р В Р’В°Р В РЎвЂ”Р В РЎвЂќР В РЎвЂ Р РЋР С“ Р В Р’В·Р В Р’В°Р В РўвЂР В Р’В°Р РЋРІР‚РЋР В Р’В°Р В РЎВР В РЎвЂ",
                false,
                () -> {
                    closeDrawer();
                    openSourceManagement();
                }
        ));
        panel.addView(createDrawerItem(
                R.drawable.ic_settings,
                "Р В РЎСљР В Р’В°Р РЋР С“Р РЋРІР‚С™Р РЋР вЂљР В РЎвЂўР В РІвЂћвЂ“Р В РЎвЂќР В РЎвЂ",
                "Р В Р’В¤Р В РЎвЂўР РЋР вЂљР В РЎВР В Р’В°Р РЋРІР‚С™, Р РЋРЎвЂњР В Р вЂ Р В Р’ВµР В РўвЂР В РЎвЂўР В РЎВР В Р’В»Р В Р’ВµР В Р вЂ¦Р В РЎвЂР РЋР РЏ, Р В Р вЂ Р В Р вЂ¦Р В Р’ВµР РЋРІвЂљВ¬Р В Р вЂ¦Р В РЎвЂР В РІвЂћвЂ“ Р В Р вЂ Р В РЎвЂР В РўвЂ",
                false,
                () -> {
                    closeDrawer();
                    openSettings();
                }
        ));
        panel.addView(createDrawerItem(
                R.drawable.ic_info,
                "Р В РЎвЂє Р В РЎвЂ”Р РЋР вЂљР В РЎвЂР В Р’В»Р В РЎвЂўР В Р’В¶Р В Р’ВµР В Р вЂ¦Р В РЎвЂР В РЎвЂ",
                "",
                false,
                () -> {
                    closeDrawer();
                    showAboutDialog();
                }
        ));

        View spacer = new View(this);
        panel.addView(spacer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1
        ));
        panel.addView(createDrawerFooter(), fullWidth());

        drawerPanel = panel;
        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                drawerWidth,
                ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.Gravity.LEFT
        );
        drawerLayer.addView(panel, panelParams);
        return drawerLayer;
    }

    private LinearLayout createDrawerHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(android.view.Gravity.CENTER_VERTICAL);
        header.setPadding(dp(8), dp(6), dp(8), dp(8));

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_drawer_transparent);
        header.addView(icon, new LinearLayout.LayoutParams(dp(52), dp(52)));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView title = createText(getString(R.string.app_name), 20, R.color.text_primary, true);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(title, fullWidth());

        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        );
        textParams.setMargins(dp(12), 0, 0, 0);
        header.addView(texts, textParams);
        return header;
    }

    private TextView createDrawerFooter() {
        TextView footer = createText("v" + appVersionName(), 12, R.color.text_secondary, false);
        footer.setGravity(android.view.Gravity.CENTER);
        footer.setPadding(0, dp(10), 0, dp(2));
        return footer;
    }

    private String appVersionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception exception) {
            return "1.1.0";
        }
    }

    private LinearLayout createDrawerItem(
            int iconRes,
            String title,
            String subtitle,
            boolean selected,
            Runnable action
    ) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setClickable(true);
        row.setPadding(dp(12), dp(9), dp(12), dp(9));
        row.setBackground(createRoundedBackground(
                getColor(selected ? R.color.chip_selected_background : android.R.color.transparent),
                0,
                24
        ));
        row.setOnClickListener(view -> action.run());

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(getColor(selected ? R.color.chip_selected_text : R.color.text_secondary));
        row.addView(icon, new LinearLayout.LayoutParams(dp(24), dp(24)));

        LinearLayout textColumn = new LinearLayout(this);
        textColumn.setOrientation(LinearLayout.VERTICAL);
        TextView titleView = createText(title,
                15,
                selected ? R.color.chip_selected_text : R.color.text_primary,
                selected);
        textColumn.addView(titleView, fullWidth());
        if (subtitle != null && !subtitle.trim().isEmpty()) {
            TextView subtitleView = createText(subtitle, 12, R.color.text_secondary, false);
            subtitleView.setSingleLine(true);
            subtitleView.setEllipsize(TextUtils.TruncateAt.END);
            textColumn.addView(subtitleView, fullWidthWithTopMargin(dp(2)));
        }

        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        );
        textParams.setMargins(dp(12), 0, 0, 0);
        row.addView(textColumn, textParams);

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                subtitle == null || subtitle.trim().isEmpty() ? dp(50) : ViewGroup.LayoutParams.WRAP_CONTENT
        );
        rowParams.setMargins(0, dp(2), 0, dp(4));
        row.setLayoutParams(rowParams);
        return row;
    }

    private void openDrawer() {
        if (drawerLayer == null || drawerPanel == null) {
            return;
        }
        drawerOpen = true;
        drawerLayer.setVisibility(View.VISIBLE);
        drawerScrim.animate().alpha(1f).setDuration(160L).start();
        drawerPanel.animate().translationX(0).setDuration(180L).start();
    }

    private void closeDrawer() {
        if (drawerLayer == null || drawerPanel == null) {
            return;
        }
        drawerOpen = false;
        int drawerWidth = drawerPanel.getWidth() == 0 ? dp(308) : drawerPanel.getWidth();
        drawerScrim.animate().alpha(0f).setDuration(140L).start();
        drawerPanel.animate()
                .translationX(-drawerWidth)
                .setDuration(170L)
                .withEndAction(() -> {
                    if (!drawerOpen && drawerLayer != null) {
                        drawerLayer.setVisibility(View.GONE);
                    }
                })
                .start();
    }

    private FrameLayout createTaskFilterSheetLayer() {
        filterSheetLayer = new FrameLayout(this);
        filterSheetLayer.setVisibility(View.GONE);

        filterSheetScrim = new View(this);
        filterSheetScrim.setBackgroundColor(Color.argb(120, 0, 0, 0));
        filterSheetScrim.setAlpha(0f);
        filterSheetScrim.setOnClickListener(view -> closeTaskFilterSheet());
        filterSheetLayer.addView(filterSheetScrim, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(16), dp(10), dp(16), dp(18));
        panel.setBackground(createTopRoundedBackground(getColor(R.color.card_background), 0, 24));
        panel.setTranslationY(dp(360));

        View handle = new View(this);
        handle.setBackground(createRoundedBackground(getColor(R.color.card_stroke), 0, 4));
        LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(dp(42), dp(4));
        handleParams.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        handleParams.setMargins(0, 0, 0, dp(14));
        panel.addView(handle, handleParams);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView title = createText("Р В Р’В¤Р В РЎвЂР В Р’В»Р РЋР Р‰Р РЋРІР‚С™Р РЋР вЂљ Р В Р’В·Р В Р’В°Р В РўвЂР В Р’В°Р РЋРІР‚РЋ", 18, R.color.text_primary, true);
        header.addView(title, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        ));

        ImageButton closeButton = createPlainIconButton(R.drawable.ic_close, "Р В РІР‚вЂќР В Р’В°Р В РЎвЂќР РЋР вЂљР РЋРІР‚в„–Р РЋРІР‚С™Р РЋР Р‰ Р РЋРІР‚С›Р В РЎвЂР В Р’В»Р РЋР Р‰Р РЋРІР‚С™Р РЋР вЂљ");
        closeButton.setOnClickListener(view -> closeTaskFilterSheet());
        header.addView(closeButton, new LinearLayout.LayoutParams(dp(40), dp(40)));
        panel.addView(header, fullWidth());

        filterSheetOptions = new LinearLayout(this);
        filterSheetOptions.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams optionsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        optionsParams.setMargins(0, dp(10), 0, 0);
        panel.addView(filterSheetOptions, optionsParams);
        populateTaskFilterSheetOptions();

        filterSheetPanel = panel;
        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.BOTTOM
        );
        filterSheetLayer.addView(panel, panelParams);
        return filterSheetLayer;
    }

    private void openTaskFilterSheet() {
        if (filterSheetLayer == null || filterSheetPanel == null) {
            return;
        }
        if (drawerOpen) {
            closeDrawer();
        }
        filterSheetOpen = true;
        populateTaskFilterSheetOptions();
        filterSheetLayer.setVisibility(View.VISIBLE);
        filterSheetScrim.setAlpha(0f);
        filterSheetPanel.post(() -> {
            int sheetHeight = filterSheetPanel.getHeight() == 0 ? dp(360) : filterSheetPanel.getHeight();
            filterSheetPanel.setTranslationY(sheetHeight);
            filterSheetScrim.animate().alpha(1f).setDuration(150L).start();
            filterSheetPanel.animate().translationY(0f).setDuration(190L).start();
        });
    }

    private void closeTaskFilterSheet() {
        if (filterSheetLayer == null || filterSheetPanel == null) {
            return;
        }
        filterSheetOpen = false;
        int sheetHeight = filterSheetPanel.getHeight() == 0 ? dp(360) : filterSheetPanel.getHeight();
        filterSheetScrim.animate().alpha(0f).setDuration(130L).start();
        filterSheetPanel.animate()
                .translationY(sheetHeight)
                .setDuration(170L)
                .withEndAction(() -> {
                    if (!filterSheetOpen && filterSheetLayer != null) {
                        filterSheetLayer.setVisibility(View.GONE);
                    }
                })
                .start();
    }

    private void populateTaskFilterSheetOptions() {
        if (filterSheetOptions == null) {
            return;
        }
        filterSheetOptions.removeAllViews();
        addTaskFilterSheetItem(UserPreferences.FILTER_ALL, "Р В РІР‚в„ўР РЋР С“Р В Р’Вµ");
        addTaskFilterSheetItem(UserPreferences.FILTER_ACTIVE, "Р В РЎвЂ™Р В РЎвЂќР РЋРІР‚С™Р В РЎвЂР В Р вЂ Р В Р вЂ¦Р РЋРІР‚в„–Р В Р’Вµ");
        addTaskFilterSheetItem(UserPreferences.FILTER_OVERDUE, "Р В РЎСџР РЋР вЂљР В РЎвЂўР РЋР С“Р РЋР вЂљР В РЎвЂўР РЋРІР‚РЋР В Р’ВµР В Р вЂ¦Р В Р вЂ¦Р РЋРІР‚в„–Р В Р’Вµ");
        addTaskFilterSheetItem(UserPreferences.FILTER_COMPLETED, "Р В РІР‚вЂќР В Р’В°Р В Р вЂ Р В Р’ВµР РЋР вЂљР РЋРІвЂљВ¬Р В Р’ВµР В Р вЂ¦Р В Р вЂ¦Р РЋРІР‚в„–Р В Р’Вµ");
        addTaskFilterSheetItem(UserPreferences.FILTER_SKIPPED, "Р В РЎСџР РЋР вЂљР В РЎвЂўР В РЎвЂ”Р РЋРЎвЂњР РЋРІР‚В°Р В Р’ВµР В Р вЂ¦Р В Р вЂ¦Р РЋРІР‚в„–Р В Р’Вµ");
    }

    private void addTaskFilterSheetItem(String filter, String label) {
        boolean selected = Objects.equals(UserPreferences.getTaskFilter(this), filter);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setClickable(true);
        row.setPadding(dp(14), 0, dp(12), 0);
        row.setBackground(createRoundedBackground(
                getColor(selected ? R.color.chip_selected_background : R.color.card_background),
                0,
                10
        ));
        row.setOnClickListener(view -> {
            UserPreferences.setTaskFilter(this, filter);
            updateActiveFilterButton();
            updateTaskSectionHeader(0);
            closeTaskFilterSheet();
            readAndRenderNote();
        });

        TextView text = createText(
                label,
                15,
                selected ? R.color.chip_selected_text : R.color.text_primary,
                selected
        );
        row.addView(text, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        ));

        ImageView checkIcon = new ImageView(this);
        checkIcon.setImageResource(R.drawable.ic_check);
        checkIcon.setColorFilter(getColor(R.color.chip_selected_text));
        checkIcon.setVisibility(selected ? View.VISIBLE : View.INVISIBLE);
        row.addView(checkIcon, new LinearLayout.LayoutParams(dp(22), dp(22)));

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(52)
        );
        rowParams.setMargins(0, dp(2), 0, dp(2));
        filterSheetOptions.addView(row, rowParams);
    }

    private void showAboutDialog() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.app_name))
                .setMessage("Р В РІР‚С”Р В РЎвЂўР В РЎвЂќР В Р’В°Р В Р’В»Р РЋР Р‰Р В Р вЂ¦Р РЋРІР‚в„–Р В Р’Вµ Р В Р вЂ¦Р В Р’В°Р В РЎвЂ”Р В РЎвЂўР В РЎВР В РЎвЂР В Р вЂ¦Р В Р’В°Р В Р вЂ¦Р В РЎвЂР РЋР РЏ Р В РЎвЂР В Р’В· markdown-Р В Р’В·Р В Р’В°Р В РўвЂР В Р’В°Р РЋРІР‚РЋ.\nР В РЎС›Р В Р’ВµР В РЎвЂќР РЋРЎвЂњР РЋРІР‚В°Р В РЎвЂР В РІвЂћвЂ“ Р РЋР вЂљР В Р’ВµР В Р’В¶Р В РЎвЂР В РЎВ: "
                        + TaskSourceManager.storageModeLabel(this)
                        + "\nР В Р’ВР РЋР С“Р РЋРІР‚С™Р В РЎвЂўР РЋРІР‚РЋР В Р вЂ¦Р В РЎвЂР В РЎвЂќ: " + TaskSourceManager.activeSourceLabel(this))
                .setPositiveButton("OK", null)
                .show();
    }

    private LinearLayout createSourceCard() {
        LinearLayout card = createCardContainer();
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.setOnClickListener(view -> openSourceManagement());

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);

        ImageView fileIcon = new ImageView(this);
        fileIcon.setImageResource(R.drawable.ic_file);
        fileIcon.setColorFilter(getColor(R.color.text_secondary));
        row.addView(fileIcon, new LinearLayout.LayoutParams(dp(28), dp(28)));

        LinearLayout textColumn = new LinearLayout(this);
        textColumn.setOrientation(LinearLayout.VERTICAL);
        sourceTitleText = createText("Р В Р’ВР РЋР С“Р РЋРІР‚С™Р В РЎвЂўР РЋРІР‚РЋР В Р вЂ¦Р В РЎвЂР В РЎвЂќ", 15, R.color.text_primary, true);
        sourceMetaText = createText("Р В Р’ВР РЋР С“Р РЋРІР‚С™Р В РЎвЂўР РЋРІР‚РЋР В Р вЂ¦Р В РЎвЂР В РЎвЂќ Р В Р вЂ¦Р В Р’Вµ Р В Р вЂ Р РЋРІР‚в„–Р В Р’В±Р РЋР вЂљР В Р’В°Р В Р вЂ¦", 13, R.color.text_secondary, false);
        sourceTitleText.setSingleLine(true);
        sourceTitleText.setEllipsize(TextUtils.TruncateAt.END);
        sourceMetaText.setSingleLine(true);
        sourceMetaText.setEllipsize(TextUtils.TruncateAt.END);
        textColumn.addView(sourceTitleText, fullWidth());
        textColumn.addView(sourceMetaText, fullWidthWithTopMargin(dp(2)));
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        );
        textParams.setMargins(dp(12), 0, dp(8), 0);
        row.addView(textColumn, textParams);

        ImageButton manageButton = createPlainIconButton(R.drawable.ic_settings, "Р В Р в‚¬Р В РЎвЂ”Р РЋР вЂљР В Р’В°Р В Р вЂ Р В Р’В»Р РЋР РЏР РЋРІР‚С™Р РЋР Р‰ Р В РЎвЂР РЋР С“Р РЋРІР‚С™Р В РЎвЂўР РЋРІР‚РЋР В Р вЂ¦Р В РЎвЂР В РЎвЂќР В Р’В°Р В РЎВР В РЎвЂ");
        manageButton.setOnClickListener(view -> openSourceManagement());
        row.addView(manageButton, new LinearLayout.LayoutParams(dp(36), dp(36)));

        ImageView chevron = new ImageView(this);
        chevron.setImageResource(R.drawable.ic_chevron_right);
        chevron.setColorFilter(getColor(R.color.text_secondary));
        row.addView(chevron, new LinearLayout.LayoutParams(dp(24), dp(24)));
        card.addView(row, fullWidth());

        sourceStatsRow = new LinearLayout(this);
        sourceStatsText = createText("", 13, R.color.text_secondary, false);
        sourceStatsText.setVisibility(View.GONE);
        sourceErrorText = createText("", 13, R.color.error_text, false);
        sourceErrorText.setVisibility(View.GONE);
        card.addView(sourceErrorText, fullWidthWithTopMargin(dp(8)));
        statusText = sourceMetaText;
        return card;
    }

    private LinearLayout createCalendarYearGrid(List<ObsidianTask> visibleTasks) {
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);

        Map<YearMonth, CalendarTaskSummary> monthSummaries = summarizeTasksByMonth(visibleTasks);
        int year = displayedCalendarMonth.getYear();
        for (int rowIndex = 0; rowIndex < 6; rowIndex++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int column = 0; column < 2; column++) {
                int month = rowIndex * 2 + column + 1;
                YearMonth yearMonth = YearMonth.of(year, month);
                CalendarTaskSummary summary = monthSummaries.get(yearMonth);
                if (summary == null) {
                    summary = new CalendarTaskSummary();
                }

                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        0,
                        dp(164),
                        1
                );
                if (column > 0) {
                    params.setMargins(dp(8), 0, 0, 0);
                }
                row.addView(createYearMonthCard(yearMonth, summary), params);
            }

            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            if (rowIndex > 0) {
                rowParams.setMargins(0, dp(8), 0, 0);
            }
            grid.addView(row, rowParams);
        }
        return grid;
    }

    private LinearLayout createYearMonthCard(YearMonth month, CalendarTaskSummary summary) {
        boolean currentMonth = YearMonth.now().equals(month);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.setClickable(true);
        card.setBackground(createRoundedBackground(
                getColor(currentMonth ? R.color.calendar_today_background : R.color.card_background),
                currentMonth ? getColor(R.color.chip_selected_stroke) : getColor(R.color.card_stroke),
                8
        ));
        card.setOnClickListener(view -> openMonthFromYear(month));

        TextView title = createText(capitalize(CALENDAR_MONTH_NAME_FORMAT.format(month.atDay(1))),
                15,
                R.color.text_primary,
                true);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        card.addView(title, fullWidth());

        TextView total = createText(summary.totalTaskCount + " " + taskCountWord(summary.totalTaskCount),
                13,
                summary.totalTaskCount > 0 ? R.color.text_primary : R.color.text_secondary,
                summary.totalTaskCount > 0);
        card.addView(total, fullWidthWithTopMargin(dp(5)));

        card.addView(createYearStatusLine("Р В Р вЂ Р РЋРІР‚в„–Р В РЎвЂ”Р В РЎвЂўР В Р’В»Р В Р вЂ¦Р В Р’ВµР В Р вЂ¦Р В РЎвЂў", summary.completedCount,
                R.color.calendar_indicator_completed), fullWidthWithTopMargin(dp(8)));
        card.addView(createYearStatusLine("Р В Р’В°Р В РЎвЂќР РЋРІР‚С™Р В РЎвЂР В Р вЂ Р В Р вЂ¦Р РЋРІР‚в„–Р В Р’Вµ", summary.activeCount,
                R.color.calendar_indicator_active), fullWidthWithTopMargin(dp(4)));
        card.addView(createYearStatusLine("Р В РЎвЂ”Р РЋР вЂљР В РЎвЂўР В РЎвЂ”Р РЋРЎвЂњР РЋРІР‚В°Р В Р’ВµР В Р вЂ¦Р В РЎвЂў", summary.skippedCount,
                R.color.calendar_indicator_skipped), fullWidthWithTopMargin(dp(4)));
        card.addView(createYearStatusLine("Р В РЎвЂ”Р РЋР вЂљР В РЎвЂўР РЋР С“Р РЋР вЂљР В РЎвЂўР РЋРІР‚РЋР В Р’ВµР В Р вЂ¦Р В РЎвЂў", summary.overdueCount,
                R.color.calendar_indicator_overdue), fullWidthWithTopMargin(dp(4)));
        return card;
    }

    private LinearLayout createYearStatusLine(String label, int count, int colorRes) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);

        View dot = new View(this);
        dot.setBackground(createCircleBackground(getColor(count > 0
                ? colorRes
                : R.color.calendar_indicator_inactive)));
        row.addView(dot, new LinearLayout.LayoutParams(dp(7), dp(7)));

        TextView text = createText(count + " " + label,
                12,
                count > 0 ? R.color.text_primary : R.color.text_secondary,
                false);
        text.setSingleLine(true);
        text.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        );
        textParams.setMargins(dp(7), 0, 0, 0);
        row.addView(text, textParams);
        return row;
    }

    private LinearLayout createCalendarYearEmptyState() {
        LinearLayout card = createCardContainer();
        card.setPadding(dp(16), dp(16), dp(16), dp(16));

        TextView title = createText("Р В РІР‚в„ў Р РЋР РЉР РЋРІР‚С™Р В РЎвЂўР В РЎВ Р В РЎвЂ“Р В РЎвЂўР В РўвЂР РЋРЎвЂњ Р В Р вЂ¦Р В Р’ВµР РЋРІР‚С™ Р В Р’В·Р В Р’В°Р В РўвЂР В Р’В°Р РЋРІР‚РЋ", 15, R.color.text_primary, true);
        title.setGravity(android.view.Gravity.CENTER);
        card.addView(title, fullWidth());

        TextView body = createText("Р В РЎСџР В РЎвЂўР В РЎвЂ”Р РЋР вЂљР В РЎвЂўР В Р’В±Р РЋРЎвЂњР В РІвЂћвЂ“Р РЋРІР‚С™Р В Р’Вµ Р РЋР С“Р В РЎВР В Р’ВµР В Р вЂ¦Р В РЎвЂР РЋРІР‚С™Р РЋР Р‰ Р В РЎвЂ“Р РЋР вЂљР РЋРЎвЂњР В РЎвЂ”Р В РЎвЂ”Р РЋРЎвЂњ Р В РЎвЂР В Р’В»Р В РЎвЂ Р РЋР С“Р В РЎвЂўР В Р’В·Р В РўвЂР В Р’В°Р РЋРІР‚С™Р РЋР Р‰ Р В Р вЂ¦Р В РЎвЂўР В Р вЂ Р РЋРЎвЂњР РЋР вЂ№ Р В Р’В·Р В Р’В°Р В РўвЂР В Р’В°Р РЋРІР‚РЋР РЋРЎвЂњ",
                13,
                R.color.text_secondary,
                false);
        body.setGravity(android.view.Gravity.CENTER);
        body.setPadding(0, dp(5), 0, 0);
        card.addView(body, fullWidth());
        return card;
    }

    private Button createFilterChip(String text, String filter) {
        Button chip = new Button(this);
        chip.setText(text);
        chip.setAllCaps(false);
        chip.setTextSize(12);
        chip.setSingleLine(true);
        chip.setMinHeight(0);
        chip.setMinWidth(0);
        chip.setMinimumWidth(0);
        chip.setPadding(dp(14), 0, dp(14), 0);
        chip.setOnClickListener(view -> {
            UserPreferences.setTaskFilter(this, filter);
            updateActiveFilterButton();
            readAndRenderNote();
        });
        return chip;
    }

    private ImageButton createTaskFilterButton() {
        ImageButton filterButton = createPlainIconButton(R.drawable.ic_filter_list, "Р В Р’В¤Р В РЎвЂР В Р’В»Р РЋР Р‰Р РЋРІР‚С™Р РЋР вЂљ Р В Р’В·Р В Р’В°Р В РўвЂР В Р’В°Р РЋРІР‚РЋ");
        filterButton.setOnClickListener(view -> openTaskFilterSheet());
        return filterButton;
    }

    private void updateTaskSectionHeader(int visibleCount) {
        if (taskSectionTitleText == null) {
            return;
        }
        String filterLabel = UserPreferences.getTaskFilterLabel(this);
        taskSectionTitleText.setText(UserPreferences.FILTER_ALL.equals(UserPreferences.getTaskFilter(this))
                ? "Р В РІР‚вЂќР В Р’В°Р В РўвЂР В Р’В°Р РЋРІР‚РЋР В РЎвЂ"
                : "Р В РІР‚вЂќР В Р’В°Р В РўвЂР В Р’В°Р РЋРІР‚РЋР В РЎвЂ Р вЂ™Р’В· " + capitalize(filterLabel));
    }

    private void addChip(LinearLayout row, Button chip, int leftMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(38)
        );
        params.setMargins(leftMargin, 0, 0, 0);
        row.addView(chip, params);
    }

    private TextView createInfoChip(String text) {
        TextView chip = createText(text, 12, R.color.chip_text, false);
        chip.setSingleLine(true);
        chip.setGravity(android.view.Gravity.CENTER);
        chip.setPadding(dp(10), 0, dp(10), 0);
        chip.setBackground(createRoundedBackground(
                getColor(R.color.chip_background),
                getColor(R.color.chip_stroke),
                8
        ));
        return chip;
    }

    private void addInfoChip(LinearLayout row, String text, int leftMargin) {
        TextView chip = createInfoChip(text);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(30)
        );
        params.setMargins(leftMargin, 0, 0, 0);
        row.addView(chip, params);
    }

    private LinearLayout createCardContainer() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(createCardBackground());
        return card;
    }

    private TextView createText(String text, int sizeSp, int colorRes, boolean bold) {
        TextView textView = new TextView(this);
        textView.setText(text);
        textView.setTextSize(sizeSp);
        textView.setTextColor(getColor(colorRes));
        textView.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        return textView;
    }

    private void showSnackbar(String message, String actionLabel, Runnable action) {
        String resolvedMessage = (message == null || message.trim().isEmpty())
                ? "Р В РЎвЂєР В РЎвЂ”Р В Р’ВµР РЋР вЂљР В Р’В°Р РЋРІР‚В Р В РЎвЂР РЋР РЏ Р В Р’В·Р В Р’В°Р В Р вЂ Р В Р’ВµР РЋР вЂљР РЋРІвЂљВ¬Р В Р’ВµР В Р вЂ¦Р В Р’В°"
                : message;
        if (appRootContainer == null) {
            Toast.makeText(this, resolvedMessage, Toast.LENGTH_LONG).show();
            return;
        }
        hideSnackbar();

        Snackbar snackbar = Snackbar.make(appRootContainer, resolvedMessage, Snackbar.LENGTH_LONG);
        snackbar.setBackgroundTint(getColor(R.color.secondary_button_background));
        snackbar.setTextColor(getColor(R.color.text_primary));
        snackbar.setActionTextColor(getColor(R.color.accent));
        View anchor = snackbarAnchorView();
        if (anchor != null) {
            snackbar.setAnchorView(anchor);
        }
        if (actionLabel != null && action != null) {
            snackbar.setAction(actionLabel, view -> action.run());
        }
        snackbar.addCallback(new Snackbar.Callback() {
            @Override
            public void onDismissed(Snackbar transientBottomBar, int event) {
                if (snackbarView == transientBottomBar) {
                    snackbarView = null;
                }
            }
        });
        snackbarView = snackbar;
        snackbar.show();
    }

    private void hideSnackbar() {
        if (snackbarView != null) {
            snackbarView.dismiss();
        }
        snackbarView = null;
    }

    private View snackbarAnchorView() {
        if (addFabButton != null && addFabButton.getVisibility() == View.VISIBLE) {
            return addFabButton;
        }
        if (bottomBarView != null) {
            return bottomBarView;
        }
        return appRootContainer;
    }

    private void refreshFromTopBar() {
        readAndRenderNote();
        showSnackbar("Р В Р’ВР РЋР С“Р РЋРІР‚С™Р В РЎвЂўР РЋРІР‚РЋР В Р вЂ¦Р В РЎвЂР В РЎвЂќ Р В РЎвЂўР В Р’В±Р В Р вЂ¦Р В РЎвЂўР В Р вЂ Р В Р’В»Р В Р’ВµР В Р вЂ¦", null, null);
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
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_OPEN_NOTE);
    }

    private void openSettings() {
        startActivity(new Intent(this, SettingsActivity.class));
    }

    @SuppressWarnings("deprecation")
    private void openSourceManagement() {
        startActivityForResult(new Intent(this, SourceManagementActivity.class), REQUEST_SOURCE_MANAGEMENT);
    }

    @SuppressWarnings("deprecation")
    private void showOnboardingIfNeeded() {
        if (!OnboardingPreferences.shouldShow(this)) {
            return;
        }
        startActivityForResult(new Intent(this, OnboardingActivity.class), REQUEST_ONBOARDING);
    }

    private void readAndRenderNote() {
        if (noteUri == null) {
            NoteChangeMonitor.cancel(this);
            ReminderScheduler.cancelScheduled(this);
            setStatus(TaskSourceManager.getStorageMode(this) == TaskStorageMode.EXTERNAL_MARKDOWN_STORAGE
                    ? "Р В РІР‚в„ўР В Р вЂ¦Р В Р’ВµР РЋРІвЂљВ¬Р В Р вЂ¦Р В РЎвЂР В РІвЂћвЂ“ Р В РЎвЂР РЋР С“Р РЋРІР‚С™Р В РЎвЂўР РЋРІР‚РЋР В Р вЂ¦Р В РЎвЂР В РЎвЂќ Р В Р вЂ¦Р В Р’Вµ Р В Р вЂ Р РЋРІР‚в„–Р В Р’В±Р РЋР вЂљР В Р’В°Р В Р вЂ¦."
                    : "Р В РІР‚в„ўР РЋР С“Р РЋРІР‚С™Р РЋР вЂљР В РЎвЂўР В Р’ВµР В Р вЂ¦Р В Р вЂ¦Р В РЎвЂўР В Р’Вµ Р РЋРІР‚В¦Р РЋР вЂљР В Р’В°Р В Р вЂ¦Р В РЎвЂР В Р’В»Р В РЎвЂР РЋРІР‚В°Р В Р’Вµ Р В Р вЂ¦Р В Р’ВµР В РўвЂР В РЎвЂўР РЋР С“Р РЋРІР‚С™Р РЋРЎвЂњР В РЎвЂ”Р В Р вЂ¦Р В РЎвЂў.");
            setNextReminder(null);
            if (selectedSection == SECTION_CALENDAR) {
                renderCalendar(new ArrayList<>());
                return;
            }
            renderEmptyState(TaskSourceManager.getStorageMode(this) == TaskStorageMode.EXTERNAL_MARKDOWN_STORAGE
                    ? "Р В РЎСљР В Р’В°Р В Р’В¶Р В РЎВР В РЎвЂР РЋРІР‚С™Р В Р’Вµ Р вЂ™Р’В«Р В РІР‚в„ўР РЋРІР‚в„–Р В Р’В±Р РЋР вЂљР В Р’В°Р РЋРІР‚С™Р РЋР Р‰ Р В Р’В·Р В Р’В°Р В РЎВР В Р’ВµР РЋРІР‚С™Р В РЎвЂќР РЋРЎвЂњР вЂ™Р’В»."
                    : "Р В РЎСљР В Р’Вµ Р РЋРЎвЂњР В РўвЂР В Р’В°Р В Р’В»Р В РЎвЂўР РЋР С“Р РЋР Р‰ Р В РЎвЂ”Р РЋР вЂљР В РЎвЂўР РЋРІР‚РЋР В РЎвЂР РЋРІР‚С™Р В Р’В°Р РЋРІР‚С™Р РЋР Р‰ Р В Р вЂ Р РЋР С“Р РЋРІР‚С™Р РЋР вЂљР В РЎвЂўР В Р’ВµР В Р вЂ¦Р В Р вЂ¦Р РЋРІР‚в„–Р В Р’Вµ markdown-Р РЋРІР‚С›Р В Р’В°Р В РІвЂћвЂ“Р В Р’В»Р РЋРІР‚в„–.");
            return;
        }

        if (refreshButton != null) {
            refreshButton.setEnabled(true);
        }

        try {
            NoteStore.TaskSnapshot snapshot = NoteStore.readTaskSnapshot(this);
            if (NoteChangeMonitor.isSuspiciousPartialRead(this, snapshot)) {
                restoreAndRenderCachedTasks("Р В Р’В¤Р В Р’В°Р В РІвЂћвЂ“Р В Р’В» Р В Р вЂ Р РЋРІР‚в„–Р В РЎвЂ“Р В Р’В»Р РЋР РЏР В РўвЂР В РЎвЂР РЋРІР‚С™ Р РЋРІР‚РЋР В Р’В°Р РЋР С“Р РЋРІР‚С™Р В РЎвЂР РЋРІР‚РЋР В Р вЂ¦Р В РЎвЂў Р РЋР С“Р В РЎвЂР В Р вЂ¦Р РЋРІР‚В¦Р РЋР вЂљР В РЎвЂўР В Р вЂ¦Р В РЎвЂР В Р’В·Р В РЎвЂР РЋР вЂљР В РЎвЂўР В Р вЂ Р В Р’В°Р В Р вЂ¦Р В Р вЂ¦Р РЋРІР‚в„–Р В РЎВ.");
                return;
            }

            renderParseResult(snapshot);
            NoteChangeMonitor.ensureScheduled(this);
        } catch (IOException | RuntimeException exception) {
            ErrorLog.record(this, "Р В РЎСљР В Р’Вµ Р РЋРЎвЂњР В РўвЂР В Р’В°Р В Р’В»Р В РЎвЂўР РЋР С“Р РЋР Р‰ Р В РЎвЂ”Р РЋР вЂљР В РЎвЂўР РЋРІР‚РЋР В РЎвЂР РЋРІР‚С™Р В Р’В°Р РЋРІР‚С™Р РЋР Р‰ Р В РЎвЂР РЋР С“Р РЋРІР‚С™Р В РЎвЂўР РЋРІР‚РЋР В Р вЂ¦Р В РЎвЂР В РЎвЂќ Р В Р вЂ  Р В РЎвЂР В Р вЂ¦Р РЋРІР‚С™Р В Р’ВµР РЋР вЂљР РЋРІР‚С›Р В Р’ВµР В РІвЂћвЂ“Р РЋР С“Р В Р’Вµ", exception);
            NoteChangeMonitor.restoreFromCache(this, exception.getMessage());
            NoteChangeMonitor.ensureScheduled(this);
            setStatus("Р В Р’В¤Р В Р’В°Р В РІвЂћвЂ“Р В Р’В» Р В Р вЂ Р РЋР вЂљР В Р’ВµР В РЎВР В Р’ВµР В Р вЂ¦Р В Р вЂ¦Р В РЎвЂў Р В Р вЂ¦Р В Р’ВµР В РўвЂР В РЎвЂўР РЋР С“Р РЋРІР‚С™Р РЋРЎвЂњР В РЎвЂ”Р В Р’ВµР В Р вЂ¦: " + exception.getMessage()
                    + "\nР В РЎС›Р В Р’ВµР В РЎвЂќР РЋРЎвЂњР РЋРІР‚В°Р В РЎвЂР В Р’Вµ Р РЋРЎвЂњР В Р вЂ Р В Р’ВµР В РўвЂР В РЎвЂўР В РЎВР В Р’В»Р В Р’ВµР В Р вЂ¦Р В РЎвЂР РЋР РЏ Р РЋР С“Р В РЎвЂўР РЋРІР‚В¦Р РЋР вЂљР В Р’В°Р В Р вЂ¦Р В Р’ВµР В Р вЂ¦Р РЋРІР‚в„–.");
        }
    }

    private void renderParseResult(NoteStore.TaskSnapshot snapshot) {
        TaskParseResult parseResult = snapshot.getParseResult();
        List<ObsidianTask> activeTasks = parseResult.getActiveTasks();
        TaskCache.saveActiveTasks(this, activeTasks);
        ReminderSchedule schedule = ReminderScheduler.schedule(this, activeTasks);
        renderedFingerprint = NoteChangeMonitor.fingerprintOf(parseResult);
        NoteChangeMonitor.recordSuccessfulSync(this, renderedFingerprint);

        if (selectedSection == SECTION_CALENDAR) {
            renderCalendar(parseResult.getTasks());
        } else {
            renderTasks(parseResult.getTasks());
        }
        setNextReminder(schedule.getNextReminder());
        updateStatusCard(parseResult, schedule, snapshot);
    }

    private void refreshNoteIfChanged() {
        if (noteUri == null) {
            return;
        }

        try {
            NoteStore.TaskSnapshot snapshot = NoteStore.readTaskSnapshot(this);
            if (NoteChangeMonitor.isSuspiciousPartialRead(this, snapshot)) {
                restoreAndRenderCachedTasks("Р В Р’В¤Р В Р’В°Р В РІвЂћвЂ“Р В Р’В» Р В Р вЂ Р РЋРІР‚в„–Р В РЎвЂ“Р В Р’В»Р РЋР РЏР В РўвЂР В РЎвЂР РЋРІР‚С™ Р РЋРІР‚РЋР В Р’В°Р РЋР С“Р РЋРІР‚С™Р В РЎвЂР РЋРІР‚РЋР В Р вЂ¦Р В РЎвЂў Р РЋР С“Р В РЎвЂР В Р вЂ¦Р РЋРІР‚В¦Р РЋР вЂљР В РЎвЂўР В Р вЂ¦Р В РЎвЂР В Р’В·Р В РЎвЂР РЋР вЂљР В РЎвЂўР В Р вЂ Р В Р’В°Р В Р вЂ¦Р В Р вЂ¦Р РЋРІР‚в„–Р В РЎВ.");
                return;
            }

            TaskParseResult parseResult = snapshot.getParseResult();
            String fingerprint = NoteChangeMonitor.fingerprintOf(parseResult);
            if (!fingerprint.equals(renderedFingerprint)) {
                renderParseResult(snapshot);
            }
        } catch (IOException | RuntimeException exception) {
            ErrorLog.record(this, "Р В РЎСљР В Р’Вµ Р РЋРЎвЂњР В РўвЂР В Р’В°Р В Р’В»Р В РЎвЂўР РЋР С“Р РЋР Р‰ Р В РЎвЂўР В Р’В±Р В Р вЂ¦Р В РЎвЂўР В Р вЂ Р В РЎвЂР РЋРІР‚С™Р РЋР Р‰ Р В РЎвЂР РЋР С“Р РЋРІР‚С™Р В РЎвЂўР РЋРІР‚РЋР В Р вЂ¦Р В РЎвЂР В РЎвЂќ Р В Р вЂ  Р В РЎвЂР В Р вЂ¦Р РЋРІР‚С™Р В Р’ВµР РЋР вЂљР РЋРІР‚С›Р В Р’ВµР В РІвЂћвЂ“Р РЋР С“Р В Р’Вµ", exception);
            NoteChangeMonitor.restoreFromCache(this, exception.getMessage());
            setStatus("Р В Р’В¤Р В Р’В°Р В РІвЂћвЂ“Р В Р’В» Р В Р вЂ Р РЋР вЂљР В Р’ВµР В РЎВР В Р’ВµР В Р вЂ¦Р В Р вЂ¦Р В РЎвЂў Р В Р вЂ¦Р В Р’ВµР В РўвЂР В РЎвЂўР РЋР С“Р РЋРІР‚С™Р РЋРЎвЂњР В РЎвЂ”Р В Р’ВµР В Р вЂ¦: " + exception.getMessage()
                    + "\nР В РЎС›Р В Р’ВµР В РЎвЂќР РЋРЎвЂњР РЋРІР‚В°Р В РЎвЂР В Р’Вµ Р РЋРЎвЂњР В Р вЂ Р В Р’ВµР В РўвЂР В РЎвЂўР В РЎВР В Р’В»Р В Р’ВµР В Р вЂ¦Р В РЎвЂР РЋР РЏ Р РЋР С“Р В РЎвЂўР РЋРІР‚В¦Р РЋР вЂљР В Р’В°Р В Р вЂ¦Р В Р’ВµР В Р вЂ¦Р РЋРІР‚в„–.");
        }
    }

    private void restoreAndRenderCachedTasks(String reason) {
        ErrorLog.record(this, reason);
        boolean restored = NoteChangeMonitor.restoreFromCache(this, reason);
        List<ObsidianTask> cachedTasks = TaskCache.loadActiveTasks(this);
        if (!cachedTasks.isEmpty()) {
            ReminderSchedule schedule = null;
            try {
                schedule = ReminderScheduler.schedule(this, cachedTasks);
            } catch (RuntimeException exception) {
                ErrorLog.record(this, "Р В РЎСљР В Р’Вµ Р РЋРЎвЂњР В РўвЂР В Р’В°Р В Р’В»Р В РЎвЂўР РЋР С“Р РЋР Р‰ Р В РЎвЂ”Р В РЎвЂўР В РЎвЂќР В Р’В°Р В Р’В·Р В Р’В°Р РЋРІР‚С™Р РЋР Р‰ Р В Р’В±Р В Р’В»Р В РЎвЂР В Р’В¶Р В Р’В°Р В РІвЂћвЂ“Р РЋРІвЂљВ¬Р В Р’ВµР В Р’Вµ Р В Р вЂ¦Р В Р’В°Р В РЎвЂ”Р В РЎвЂўР В РЎВР В РЎвЂР В Р вЂ¦Р В Р’В°Р В Р вЂ¦Р В РЎвЂР В Р’Вµ Р В РЎвЂР В Р’В· Р В РЎвЂќР РЋР РЉР РЋРІвЂљВ¬Р В Р’В°", exception);
            }
            if (selectedSection == SECTION_CALENDAR) {
                renderCalendar(cachedTasks);
            } else {
                renderTasks(cachedTasks);
            }
            setNextReminder(schedule == null ? null : schedule.getNextReminder());
            setStatus(reason
                    + "\nР В Р’ВР РЋР С“Р В РЎвЂ”Р В РЎвЂўР В Р’В»Р РЋР Р‰Р В Р’В·Р РЋРЎвЂњР В Р’ВµР РЋРІР‚С™Р РЋР С“Р РЋР РЏ Р В Р’В»Р В РЎвЂўР В РЎвЂќР В Р’В°Р В Р’В»Р РЋР Р‰Р В Р вЂ¦Р РЋРІР‚в„–Р В РІвЂћвЂ“ Р В РЎвЂќР РЋР РЉР РЋРІвЂљВ¬ Р В Р’В·Р В Р’В°Р В РўвЂР В Р’В°Р РЋРІР‚РЋ: " + cachedTasks.size()
                    + "\nР В Р в‚¬Р В Р вЂ Р В Р’ВµР В РўвЂР В РЎвЂўР В РЎВР В Р’В»Р В Р’ВµР В Р вЂ¦Р В РЎвЂР РЋР РЏ " + (restored ? "Р В Р вЂ Р В РЎвЂўР РЋР С“Р РЋР С“Р РЋРІР‚С™Р В Р’В°Р В Р вЂ¦Р В РЎвЂўР В Р вЂ Р В Р’В»Р В Р’ВµР В Р вЂ¦Р РЋРІР‚в„–." : "Р В РЎвЂўР РЋР С“Р РЋРІР‚С™Р В Р’В°Р В Р вЂ Р В Р’В»Р В Р’ВµР В Р вЂ¦Р РЋРІР‚в„– Р В Р’В±Р В Р’ВµР В Р’В· Р В РЎвЂР В Р’В·Р В РЎВР В Р’ВµР В Р вЂ¦Р В Р’ВµР В Р вЂ¦Р В РЎвЂР В РІвЂћвЂ“."));
            return;
        }

        setStatus(reason + "\nР В РІР‚С”Р В РЎвЂўР В РЎвЂќР В Р’В°Р В Р’В»Р РЋР Р‰Р В Р вЂ¦Р РЋРІР‚в„–Р В РІвЂћвЂ“ Р В РЎвЂќР РЋР РЉР РЋРІвЂљВ¬ Р В Р’В·Р В Р’В°Р В РўвЂР В Р’В°Р РЋРІР‚РЋ Р В РЎвЂ”Р РЋРЎвЂњР РЋР С“Р РЋРІР‚С™.");
    }

    private void startForegroundNotePolling() {
        stopForegroundNotePolling();
        noteRefreshHandler.postDelayed(noteRefreshRunnable, FOREGROUND_REFRESH_INTERVAL_MS);
    }

    private void stopForegroundNotePolling() {
        noteRefreshHandler.removeCallbacks(noteRefreshRunnable);
    }

    private void updateStatusCard(
            TaskParseResult parseResult,
            ReminderSchedule schedule,
            NoteStore.TaskSnapshot snapshot
    ) {
        if (sourceTitleText == null) {
            setStatus(buildStatus(parseResult, schedule, snapshot));
            return;
        }

        sourceTitleText.setText(compactSourceName(parseResult));
        sourceMetaText.setText(String.format(
                Locale.getDefault(),
                "%d Р РЋРІР‚С›Р В Р’В°Р В РІвЂћвЂ“Р В Р’В». Р вЂ™Р’В· Р В РЎвЂўР В Р’В±Р В Р вЂ¦Р В РЎвЂўР В Р вЂ Р В Р’В»Р В Р’ВµР В Р вЂ¦Р В РЎвЂў %s",
                snapshot.getDocumentCount(),
                DateTimeFormatter.ofPattern("HH:mm").format(LocalDateTime.now())
        ));
        sourceStatsText.setVisibility(View.GONE);
        sourceStatsRow.removeAllViews();

        if (parseResult.getErrors().isEmpty()) {
            sourceErrorText.setVisibility(View.GONE);
            sourceErrorText.setText("");
            return;
        }

        StringBuilder errors = new StringBuilder("Р В РЎвЂєР РЋРІвЂљВ¬Р В РЎвЂР В Р’В±Р В РЎвЂќР В РЎвЂ Р РЋР вЂљР В Р’В°Р В Р’В·Р В Р’В±Р В РЎвЂўР РЋР вЂљР В Р’В°: ");
        int limit = Math.min(2, parseResult.getErrors().size());
        for (int i = 0; i < limit; i++) {
            if (i > 0) {
                errors.append("\n");
            }
            errors.append(parseResult.getErrors().get(i).format());
        }
        if (parseResult.getErrors().size() > limit) {
            errors.append("\nР В РІР‚СћР РЋРІР‚В°Р В Р’Вµ Р В РЎвЂўР РЋРІвЂљВ¬Р В РЎвЂР В Р’В±Р В РЎвЂўР В РЎвЂќ: ").append(parseResult.getErrors().size() - limit);
        }
        sourceErrorText.setVisibility(View.VISIBLE);
        sourceErrorText.setText(errors.toString());
    }

    private String buildStatus(
            TaskParseResult parseResult,
            ReminderSchedule schedule,
            NoteStore.TaskSnapshot snapshot
    ) {
        String permissionStatus = schedule.isNotificationsAllowed()
                ? "Р РЋРЎвЂњР В Р вЂ Р В Р’ВµР В РўвЂР В РЎвЂўР В РЎВР В Р’В»Р В Р’ВµР В Р вЂ¦Р В РЎвЂР РЋР РЏ Р РЋР вЂљР В Р’В°Р В Р’В·Р РЋР вЂљР В Р’ВµР РЋРІвЂљВ¬Р В Р’ВµР В Р вЂ¦Р РЋРІР‚в„–"
                : "Р В Р вЂ¦Р В Р’ВµР РЋРІР‚С™ Р РЋР вЂљР В Р’В°Р В Р’В·Р РЋР вЂљР В Р’ВµР РЋРІвЂљВ¬Р В Р’ВµР В Р вЂ¦Р В РЎвЂР РЋР РЏ Р В Р вЂ¦Р В Р’В° Р РЋРЎвЂњР В Р вЂ Р В Р’ВµР В РўвЂР В РЎвЂўР В РЎВР В Р’В»Р В Р’ВµР В Р вЂ¦Р В РЎвЂР РЋР РЏ";
        String exactAlarmStatus = ReminderScheduler.canScheduleExactAlarms(this)
                ? "Р РЋРІР‚С™Р В РЎвЂўР РЋРІР‚РЋР В Р вЂ¦Р РЋРІР‚в„–Р В Р’Вµ Р В Р вЂ¦Р В Р’В°Р В РЎвЂ”Р В РЎвЂўР В РЎВР В РЎвЂР В Р вЂ¦Р В Р’В°Р В Р вЂ¦Р В РЎвЂР РЋР РЏ Р РЋР вЂљР В Р’В°Р В Р’В·Р РЋР вЂљР В Р’ВµР РЋРІвЂљВ¬Р В Р’ВµР В Р вЂ¦Р РЋРІР‚в„–"
                : "Р РЋРІР‚С™Р В РЎвЂўР РЋРІР‚РЋР В Р вЂ¦Р РЋРІР‚в„–Р В Р’Вµ Р В Р вЂ¦Р В Р’В°Р В РЎвЂ”Р В РЎвЂўР В РЎВР В РЎвЂР В Р вЂ¦Р В Р’В°Р В Р вЂ¦Р В РЎвЂР РЋР РЏ Р В Р вЂ¦Р В Р’Вµ Р РЋР вЂљР В Р’В°Р В Р’В·Р РЋР вЂљР В Р’ВµР РЋРІвЂљВ¬Р В Р’ВµР В Р вЂ¦Р РЋРІР‚в„–, Р В РЎвЂР РЋР С“Р В РЎвЂ”Р В РЎвЂўР В Р’В»Р РЋР Р‰Р В Р’В·Р РЋРЎвЂњР В Р’ВµР РЋРІР‚С™Р РЋР С“Р РЋР РЏ Р В Р вЂ¦Р В Р’ВµР РЋРІР‚С™Р В РЎвЂўР РЋРІР‚РЋР В Р вЂ¦Р РЋРІР‚в„–Р В РІвЂћвЂ“ fallback";
        String status = String.format(
                Locale.getDefault(),
                "Р В Р’ВР РЋР С“Р РЋРІР‚С™Р В РЎвЂўР РЋРІР‚РЋР В Р вЂ¦Р В РЎвЂР В РЎвЂќ: %s\nР В Р’В¤Р В Р’В°Р В РІвЂћвЂ“Р В Р’В»Р В РЎвЂўР В Р вЂ  Р В РЎвЂ”Р РЋР вЂљР В РЎвЂўР РЋРІР‚РЋР В РЎвЂР РЋРІР‚С™Р В Р’В°Р В Р вЂ¦Р В РЎвЂў: %d\nР В РІР‚в„ўР РЋР С“Р В Р’ВµР В РЎвЂ“Р В РЎвЂў Р В Р’В·Р В Р’В°Р В РўвЂР В Р’В°Р РЋРІР‚РЋ: %d\nР В РЎвЂ™Р В РЎвЂќР РЋРІР‚С™Р В РЎвЂР В Р вЂ Р В Р вЂ¦Р РЋРІР‚в„–Р РЋРІР‚В¦ Р В Р’В·Р В Р’В°Р В РўвЂР В Р’В°Р РЋРІР‚РЋ: %d\nР В РІР‚вЂќР В Р’В°Р В РЎвЂ”Р В Р’В»Р В Р’В°Р В Р вЂ¦Р В РЎвЂР РЋР вЂљР В РЎвЂўР В Р вЂ Р В Р’В°Р В Р вЂ¦Р В РЎвЂў Р РЋРЎвЂњР В Р вЂ Р В Р’ВµР В РўвЂР В РЎвЂўР В РЎВР В Р’В»Р В Р’ВµР В Р вЂ¦Р В РЎвЂР В РІвЂћвЂ“: %d\n%s\n%s\nР В РЎвЂєР В Р’В±Р В Р вЂ¦Р В РЎвЂўР В Р вЂ Р В Р’В»Р В Р’ВµР В Р вЂ¦Р В РЎвЂў: %s",
                NoteStore.sourceLabel(this),
                snapshot.getDocumentCount(),
                parseResult.getTasks().size(),
                parseResult.getActiveTasks().size(),
                schedule.getScheduledCount(),
                permissionStatus,
                exactAlarmStatus,
                DATE_TIME_FORMAT.format(LocalDateTime.now())
        );

        if (!parseResult.getErrors().isEmpty()) {
            StringBuilder builder = new StringBuilder(status);
            builder.append("\nР В РЎвЂєР РЋРІвЂљВ¬Р В РЎвЂР В Р’В±Р В РЎвЂќР В РЎвЂ Р РЋР вЂљР В Р’В°Р В Р’В·Р В Р’В±Р В РЎвЂўР РЋР вЂљР В Р’В°:");
            int limit = Math.min(3, parseResult.getErrors().size());
            for (int i = 0; i < limit; i++) {
                builder.append("\n").append(parseResult.getErrors().get(i).format());
            }
            if (parseResult.getErrors().size() > limit) {
                builder.append("\nР В РІР‚СћР РЋРІР‚В°Р В Р’Вµ Р В РЎвЂўР РЋРІвЂљВ¬Р В РЎвЂР В Р’В±Р В РЎвЂўР В РЎвЂќ: ").append(parseResult.getErrors().size() - limit);
            }
            status = builder.toString();
        }

        return status;
    }

    private void renderTasks(List<ObsidianTask> tasks) {
        latestTasks = new ArrayList<>(tasks);
        pruneSelectedTaskKeys(tasks);
        taskList.removeAllViews();
        List<ObsidianTask> groupingCandidates = tasksForGroupRow(tasks);
        List<ObsidianTask> visibleTasks = filterVisibleTasks(tasks);
        updateGroupFilterRow(groupingCandidates);
        showTaskSourceNames = hasMultipleSources(tasks);
        List<ObsidianTask> groupedVisibleTasks = filterTasksBySelectedGroup(visibleTasks);
        List<ObsidianTask> displayTasks = rootTasksForDisplay(tasks, groupedVisibleTasks);
        displayTasks.sort(this::compareTasksForDisplay);
        updateTaskSectionHeader(displayTasks.size());
        if (displayTasks.isEmpty()) {
            renderEmptyState("Р В РІР‚в„ў Р В Р вЂ Р РЋРІР‚в„–Р В Р’В±Р РЋР вЂљР В Р’В°Р В Р вЂ¦Р В Р вЂ¦Р РЋРІР‚в„–Р РЋРІР‚В¦ markdown-Р РЋРІР‚С›Р В Р’В°Р В РІвЂћвЂ“Р В Р’В»Р В Р’В°Р РЋРІР‚В¦ Р В Р вЂ¦Р В Р’ВµР РЋРІР‚С™ Р РЋРЎвЂњР В Р вЂ Р В Р’ВµР В РўвЂР В РЎвЂўР В РЎВР В Р’В»Р В Р’ВµР В Р вЂ¦Р В РЎвЂР В РІвЂћвЂ“ Р РЋР С“ @due(...) Р В РўвЂР В Р’В»Р РЋР РЏ Р РЋРІР‚С™Р В Р’ВµР В РЎвЂќР РЋРЎвЂњР РЋРІР‚В°Р В Р’ВµР В РЎвЂ“Р В РЎвЂў Р РЋРІР‚С›Р В РЎвЂР В Р’В»Р РЋР Р‰Р РЋРІР‚С™Р РЋР вЂљР В Р’В°.");
            return;
        }

        String currentSource = null;
        boolean showGroupHeaders = showTaskSourceNames;
        for (ObsidianTask task : displayTasks) {
            String sourceName = task.getSourceName();
            if (showGroupHeaders && !sourceName.equals(currentSource)) {
                currentSource = sourceName;
                taskList.addView(createSourceHeader(sourceName));
            }
            taskList.addView(createTaskView(task));
        }
    }

    private boolean isSelectionMode() {
        return !selectedTaskKeys.isEmpty();
    }

    private List<ObsidianTask> rootTasksForDisplay(List<ObsidianTask> allTasks, List<ObsidianTask> visibleTasks) {
        Map<String, ObsidianTask> tasksByKey = new HashMap<>();
        for (ObsidianTask task : allTasks) {
            tasksByKey.put(task.getTaskKey(), task);
        }

        List<ObsidianTask> roots = new ArrayList<>();
        Set<String> addedKeys = new LinkedHashSet<>();
        for (ObsidianTask task : visibleTasks) {
            ObsidianTask displayTask = task;
            if (task.isSubtask()) {
                ObsidianTask parent = tasksByKey.get(task.getParentTaskKey());
                if (parent != null) {
                    displayTask = parent;
                }
            }
            if (addedKeys.add(displayTask.getTaskKey())) {
                roots.add(displayTask);
            }
        }
        return roots;
    }

    private void enterSelectionMode(ObsidianTask task) {
        if (task == null) {
            return;
        }
        selectedTaskKeys.add(task.getTaskKey());
        updateSelectionUi();
    }

    private void toggleTaskSelection(ObsidianTask task) {
        if (task == null) {
            return;
        }
        String taskKey = task.getTaskKey();
        if (selectedTaskKeys.contains(taskKey)) {
            selectedTaskKeys.remove(taskKey);
        } else {
            selectedTaskKeys.add(taskKey);
        }
        if (selectedTaskKeys.isEmpty()) {
            exitSelectionMode();
            return;
        }
        updateSelectionUi();
    }

    private void exitSelectionMode() {
        if (selectedTaskKeys.isEmpty()) {
            refreshTopAppBar();
            updateFabVisibility();
            return;
        }
        selectedTaskKeys.clear();
        updateSelectionUi();
    }

    private void updateSelectionUi() {
        refreshTopAppBar();
        updateFabVisibility();
        if (selectedSection == SECTION_TASKS) {
            renderTasks(latestTasks);
        }
    }

    private void updateFabVisibility() {
        if (addFabButton == null) {
            return;
        }
        addFabButton.setVisibility(selectedSection == SECTION_TASKS && isSelectionMode()
                ? View.GONE
                : View.VISIBLE);
    }

    private void pruneSelectedTaskKeys(List<ObsidianTask> tasks) {
        if (selectedTaskKeys.isEmpty()) {
            return;
        }
        Set<String> availableKeys = new LinkedHashSet<>();
        for (ObsidianTask task : tasks) {
            availableKeys.add(task.getTaskKey());
        }
        if (selectedTaskKeys.retainAll(availableKeys) && selectedTaskKeys.isEmpty()) {
            refreshTopAppBar();
            updateFabVisibility();
        }
    }

    private List<ObsidianTask> selectedTasks() {
        List<ObsidianTask> selectedTasks = new ArrayList<>();
        for (ObsidianTask task : latestTasks) {
            if (selectedTaskKeys.contains(task.getTaskKey())) {
                selectedTasks.add(task);
            }
        }
        return selectedTasks;
    }

    private void renderCalendar(List<ObsidianTask> tasks) {
        latestTasks = new ArrayList<>(tasks);
        if (taskList == null) {
            return;
        }
        taskList.removeAllViews();
        showTaskSourceNames = hasMultipleSources(tasks);

        List<ObsidianTask> visibleTasks = calendarMode == CALENDAR_MODE_YEAR
                ? filterCalendarContextTasks(tasks)
                : filterVisibleTasks(tasks);
        Map<LocalDate, List<ObsidianTask>> tasksByDate = tasksByDate(visibleTasks);

        taskList.addView(createCalendarModeToggle(), fullWidthWithBottomMargin());
        taskList.addView(createCalendarPeriodHeader(tasksByDate), fullWidthWithBottomMargin());
        if (calendarMode == CALENDAR_MODE_YEAR) {
            taskList.addView(createCalendarYearGrid(visibleTasks), fullWidthWithBottomMargin());
            if (periodTaskCount(tasksByDate) == 0) {
                taskList.addView(createCalendarYearEmptyState(), fullWidthWithBottomMargin());
            }
        } else if (calendarMode == CALENDAR_MODE_WEEK) {
            taskList.addView(createCalendarWeekGrid(tasksByDate), fullWidthWithBottomMargin());
            taskList.addView(createSelectedDayTaskList(tasksByDate), fullWidthWithBottomMargin());
        } else {
            taskList.addView(createCalendarGrid(tasksByDate), fullWidthWithBottomMargin());
            taskList.addView(createSelectedDayTaskList(tasksByDate), fullWidthWithBottomMargin());
        }
    }

    private LinearLayout createCalendarModeToggle() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);

        row.addView(createCalendarModeButton("Р В РЎСљР В Р’ВµР В РўвЂР В Р’ВµР В Р’В»Р РЋР РЏ", CALENDAR_MODE_WEEK), new LinearLayout.LayoutParams(
                0,
                dp(38),
                1
        ));

        LinearLayout.LayoutParams monthParams = new LinearLayout.LayoutParams(0, dp(38), 1);
        monthParams.setMargins(dp(7), 0, 0, 0);
        row.addView(createCalendarModeButton("Р В РЎС™Р В Р’ВµР РЋР С“Р РЋР РЏР РЋРІР‚В ", CALENDAR_MODE_MONTH), monthParams);

        LinearLayout.LayoutParams yearParams = new LinearLayout.LayoutParams(0, dp(38), 1);
        yearParams.setMargins(dp(7), 0, 0, 0);
        row.addView(createCalendarModeButton("Р В РІР‚СљР В РЎвЂўР В РўвЂ", CALENDAR_MODE_YEAR), yearParams);
        return row;
    }

    private Button createCalendarModeButton(String label, int mode) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(13);
        button.setMinHeight(0);
        button.setMinimumWidth(0);
        button.setPadding(dp(12), 0, dp(12), 0);
        styleFilterChip(button, calendarMode == mode);
        button.setOnClickListener(view -> {
            if (calendarMode == mode) {
                return;
            }
            calendarMode = mode;
            if (selectedCalendarDate == null) {
                selectedCalendarDate = LocalDate.now();
            }
            displayedCalendarMonth = YearMonth.from(selectedCalendarDate);
            renderCalendar(latestTasks);
        });
        return button;
    }

    private LinearLayout createCalendarPeriodHeader(Map<LocalDate, List<ObsidianTask>> tasksByDate) {
        LinearLayout card = createCardContainer();
        card.setPadding(dp(12), dp(10), dp(12), dp(10));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView previous = createMonthNavButton("Р Р†Р вЂљРІвЂћвЂ“", calendarMode == CALENDAR_MODE_YEAR
                ? "Р В РЎСџР РЋР вЂљР В Р’ВµР В РўвЂР РЋРІР‚в„–Р В РўвЂР РЋРЎвЂњР РЋРІР‚В°Р В РЎвЂР В РІвЂћвЂ“ Р В РЎвЂ“Р В РЎвЂўР В РўвЂ"
                : calendarMode == CALENDAR_MODE_WEEK
                        ? "Р В РЎСџР РЋР вЂљР В Р’ВµР В РўвЂР РЋРІР‚в„–Р В РўвЂР РЋРЎвЂњР РЋРІР‚В°Р В Р’В°Р РЋР РЏ Р В Р вЂ¦Р В Р’ВµР В РўвЂР В Р’ВµР В Р’В»Р РЋР РЏ"
                        : "Р В РЎСџР РЋР вЂљР В Р’ВµР В РўвЂР РЋРІР‚в„–Р В РўвЂР РЋРЎвЂњР РЋРІР‚В°Р В РЎвЂР В РІвЂћвЂ“ Р В РЎВР В Р’ВµР РЋР С“Р РЋР РЏР РЋРІР‚В ");
        previous.setOnClickListener(view -> moveCalendarPeriod(-1));
        row.addView(previous, new LinearLayout.LayoutParams(dp(42), dp(42)));

        TextView title = createText(calendarPeriodTitle(),
                17,
                R.color.text_primary,
                true);
        title.setGravity(android.view.Gravity.CENTER);
        row.addView(title, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        ));

        TextView next = createMonthNavButton("Р Р†Р вЂљРЎвЂќ", calendarMode == CALENDAR_MODE_YEAR
                ? "Р В Р Р‹Р В Р’В»Р В Р’ВµР В РўвЂР РЋРЎвЂњР РЋР вЂ№Р РЋРІР‚В°Р В РЎвЂР В РІвЂћвЂ“ Р В РЎвЂ“Р В РЎвЂўР В РўвЂ"
                : calendarMode == CALENDAR_MODE_WEEK
                        ? "Р В Р Р‹Р В Р’В»Р В Р’ВµР В РўвЂР РЋРЎвЂњР РЋР вЂ№Р РЋРІР‚В°Р В Р’В°Р РЋР РЏ Р В Р вЂ¦Р В Р’ВµР В РўвЂР В Р’ВµР В Р’В»Р РЋР РЏ"
                        : "Р В Р Р‹Р В Р’В»Р В Р’ВµР В РўвЂР РЋРЎвЂњР РЋР вЂ№Р РЋРІР‚В°Р В РЎвЂР В РІвЂћвЂ“ Р В РЎВР В Р’ВµР РЋР С“Р РЋР РЏР РЋРІР‚В ");
        next.setOnClickListener(view -> moveCalendarPeriod(1));
        row.addView(next, new LinearLayout.LayoutParams(dp(42), dp(42)));
        card.addView(row, fullWidth());

        int taskCount = periodTaskCount(tasksByDate);
        String periodLabel = calendarMode == CALENDAR_MODE_YEAR
                ? "Р В Р вЂ  Р РЋР РЉР РЋРІР‚С™Р В РЎвЂўР В РЎВ Р В РЎвЂ“Р В РЎвЂўР В РўвЂР РЋРЎвЂњ"
                : calendarMode == CALENDAR_MODE_WEEK
                        ? "Р В Р вЂ¦Р В Р’В° Р РЋР РЉР РЋРІР‚С™Р В РЎвЂўР В РІвЂћвЂ“ Р В Р вЂ¦Р В Р’ВµР В РўвЂР В Р’ВµР В Р’В»Р В Р’Вµ"
                        : "Р В Р вЂ  Р РЋР РЉР РЋРІР‚С™Р В РЎвЂўР В РЎВ Р В РЎВР В Р’ВµР РЋР С“Р РЋР РЏР РЋРІР‚В Р В Р’Вµ";
        TextView meta = createText(taskCount == 0
                        ? "Р В РЎСљР В Р’ВµР РЋРІР‚С™ Р В Р’В·Р В Р’В°Р В РўвЂР В Р’В°Р РЋРІР‚РЋ Р В РЎвЂ”Р В РЎвЂў Р РЋРІР‚С™Р В Р’ВµР В РЎвЂќР РЋРЎвЂњР РЋРІР‚В°Р В РЎвЂР В РЎВ Р РЋРІР‚С›Р В РЎвЂР В Р’В»Р РЋР Р‰Р РЋРІР‚С™Р РЋР вЂљР В Р’В°Р В РЎВ"
                        : taskCount + " " + taskCountWord(taskCount) + " " + periodLabel,
                13,
                R.color.text_secondary,
                false);
        meta.setGravity(android.view.Gravity.CENTER);
        card.addView(meta, fullWidthWithTopMargin(dp(4)));
        return card;
    }

    private TextView createMonthNavButton(String text, String description) {
        TextView button = createText(text, 28, R.color.text_primary, true);
        button.setGravity(android.view.Gravity.CENTER);
        button.setContentDescription(description);
        button.setClickable(true);
        button.setBackground(createRoundedBackground(
                getColor(R.color.secondary_button_background),
                0,
                8
        ));
        return button;
    }

    private LinearLayout createCalendarGrid(Map<LocalDate, List<ObsidianTask>> tasksByDate) {
        LinearLayout card = createCardContainer();
        card.setPadding(dp(8), dp(8), dp(8), dp(8));

        LinearLayout weekdays = new LinearLayout(this);
        weekdays.setOrientation(LinearLayout.HORIZONTAL);
        String[] labels = new String[]{"Р В РЎвЂ”Р В Р вЂ¦", "Р В Р вЂ Р РЋРІР‚С™", "Р РЋР С“Р РЋР вЂљ", "Р РЋРІР‚РЋР РЋРІР‚С™", "Р В РЎвЂ”Р РЋРІР‚С™", "Р РЋР С“Р В Р’В±", "Р В Р вЂ Р РЋР С“"};
        for (String label : labels) {
            TextView text = createText(label, 12, R.color.text_secondary, true);
            text.setGravity(android.view.Gravity.CENTER);
            weekdays.addView(text, new LinearLayout.LayoutParams(
                    0,
                    dp(22),
                    1
            ));
        }
        card.addView(weekdays, fullWidth());

        LocalDate firstVisibleDay = firstVisibleCalendarDay(displayedCalendarMonth);
        for (int week = 0; week < 6; week++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int day = 0; day < 7; day++) {
                LocalDate date = firstVisibleDay.plusDays(week * 7L + day);
                row.addView(createDayCell(date, tasksByDate), new LinearLayout.LayoutParams(
                        0,
                        dp(60),
                        1
                ));
            }
            card.addView(row, fullWidthWithTopMargin(dp(1)));
        }
        return card;
    }

    private LinearLayout createCalendarWeekGrid(Map<LocalDate, List<ObsidianTask>> tasksByDate) {
        LinearLayout card = createCardContainer();
        card.setPadding(dp(8), dp(8), dp(8), dp(8));

        LinearLayout weekdays = new LinearLayout(this);
        weekdays.setOrientation(LinearLayout.HORIZONTAL);
        String[] labels = new String[]{"Р В РЎвЂ”Р В Р вЂ¦", "Р В Р вЂ Р РЋРІР‚С™", "Р РЋР С“Р РЋР вЂљ", "Р РЋРІР‚РЋР РЋРІР‚С™", "Р В РЎвЂ”Р РЋРІР‚С™", "Р РЋР С“Р В Р’В±", "Р В Р вЂ Р РЋР С“"};
        for (String label : labels) {
            TextView text = createText(label, 12, R.color.text_secondary, true);
            text.setGravity(android.view.Gravity.CENTER);
            weekdays.addView(text, new LinearLayout.LayoutParams(
                    0,
                    dp(22),
                    1
            ));
        }
        card.addView(weekdays, fullWidth());

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LocalDate weekStart = currentWeekStart();
        for (int day = 0; day < 7; day++) {
            LocalDate date = weekStart.plusDays(day);
            row.addView(createDayCell(date, tasksByDate), new LinearLayout.LayoutParams(
                    0,
                    dp(62),
                    1
            ));
        }
        card.addView(row, fullWidthWithTopMargin(dp(1)));
        return card;
    }

    private View createDayCell(LocalDate date, Map<LocalDate, List<ObsidianTask>> tasksByDate) {
        boolean inDisplayedMonth = calendarMode == CALENDAR_MODE_WEEK
                || YearMonth.from(date).equals(displayedCalendarMonth);
        boolean selected = date.equals(selectedCalendarDate);
        boolean today = date.equals(LocalDate.now());

        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(android.view.Gravity.CENTER);
        cell.setClickable(true);
        cell.setPadding(dp(2), dp(4), dp(2), dp(4));
        cell.setBackground(createRoundedBackground(
                getColor(today ? R.color.calendar_today_background : R.color.card_background),
                selected ? getColor(R.color.chip_selected_stroke) : 0,
                8
        ));
        cell.setOnClickListener(view -> selectCalendarDate(date));

        CalendarTaskSummary summary = summarizeCalendarDay(tasksByDate.get(date));
        TextView dayNumber = createText(String.valueOf(date.getDayOfMonth()),
                14,
                inDisplayedMonth || selected ? R.color.text_primary : R.color.text_secondary,
                selected || today);
        dayNumber.setGravity(android.view.Gravity.CENTER);
        cell.addView(dayNumber, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(24)
        ));

        TextView count = createText(String.valueOf(summary.totalTaskCount),
                10,
                summary.totalTaskCount > 0 ? R.color.text_secondary : R.color.calendar_indicator_inactive,
                summary.totalTaskCount > 0);
        count.setGravity(android.view.Gravity.CENTER);
        cell.addView(count, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(12)
        ));

        cell.addView(createDayStatusIndicators(summary), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(10)
        ));
        return cell;
    }

    private LinearLayout createDayStatusIndicators(CalendarTaskSummary summary) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER);
        addStatusIndicator(row, summary.completedCount > 0, R.color.calendar_indicator_completed, 0);
        addStatusIndicator(row, summary.activeCount > 0, R.color.calendar_indicator_active, dp(3));
        addStatusIndicator(row, summary.skippedCount > 0, R.color.calendar_indicator_skipped, dp(3));
        addStatusIndicator(row, summary.overdueCount > 0, R.color.calendar_indicator_overdue, dp(3));
        return row;
    }

    private void addStatusIndicator(LinearLayout row, boolean active, int activeColor, int leftMargin) {
        View indicator = new View(this);
        indicator.setBackground(createCircleBackground(getColor(active
                ? activeColor
                : R.color.calendar_indicator_inactive)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(6), dp(6));
        params.setMargins(leftMargin, 0, 0, 0);
        row.addView(indicator, params);
    }

    private LinearLayout createSelectedDayTaskList(Map<LocalDate, List<ObsidianTask>> tasksByDate) {
        LinearLayout card = createCardContainer();
        card.setPadding(dp(14), dp(12), dp(14), dp(14));

        LocalDate date = selectedCalendarDate == null ? LocalDate.now() : selectedCalendarDate;
        List<ObsidianTask> dayTasks = new ArrayList<>();
        List<ObsidianTask> rawDayTasks = tasksByDate.get(date);
        if (rawDayTasks != null) {
            dayTasks.addAll(rawDayTasks);
        }
        dayTasks.sort(this::compareTasksByTimeOnly);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        TextView title = createText(capitalize(CALENDAR_DAY_HEADER_FORMAT.format(date)),
                17,
                R.color.text_primary,
                true);
        header.addView(title, fullWidth());
        TextView count = createText(dayTasks.size() + " " + taskCountWord(dayTasks.size()),
                13,
                R.color.text_secondary,
                false);
        header.addView(count, fullWidthWithTopMargin(dp(3)));
        card.addView(header, fullWidth());

        if (dayTasks.isEmpty()) {
            LinearLayout empty = new LinearLayout(this);
            empty.setOrientation(LinearLayout.VERTICAL);
            empty.setGravity(android.view.Gravity.CENTER);
            empty.setPadding(0, dp(18), 0, dp(10));

            TextView emptyTitle = createText("Р В РЎСљР В Р’В° Р РЋР РЉР РЋРІР‚С™Р В РЎвЂўР РЋРІР‚С™ Р В РўвЂР В Р’ВµР В Р вЂ¦Р РЋР Р‰ Р В Р’В·Р В Р’В°Р В РўвЂР В Р’В°Р РЋРІР‚РЋ Р В Р вЂ¦Р В Р’ВµР РЋРІР‚С™", 15, R.color.text_primary, true);
            emptyTitle.setGravity(android.view.Gravity.CENTER);
            empty.addView(emptyTitle, fullWidth());

            TextView emptyBody = createText("Р В РЎСљР В Р’В°Р В Р’В¶Р В РЎВР В РЎвЂР РЋРІР‚С™Р В Р’Вµ +, Р РЋРІР‚РЋР РЋРІР‚С™Р В РЎвЂўР В Р’В±Р РЋРІР‚в„– Р РЋР С“Р В РЎвЂўР В Р’В·Р В РўвЂР В Р’В°Р РЋРІР‚С™Р РЋР Р‰ Р В Р’В·Р В Р’В°Р В РўвЂР В Р’В°Р РЋРІР‚РЋР РЋРЎвЂњ", 13, R.color.text_secondary, false);
            emptyBody.setGravity(android.view.Gravity.CENTER);
            empty.addView(emptyBody, fullWidthWithTopMargin(dp(5)));
            card.addView(empty, fullWidth());
            return card;
        }

        for (ObsidianTask task : dayTasks) {
            card.addView(createCalendarTaskView(task), fullWidthWithTopMargin(dp(10)));
        }
        return card;
    }

    private View createCalendarTaskView(ObsidianTask task) {
        FrameLayout wrapper = new FrameLayout(this);
        wrapper.addView(createSwipeActionBackground(task), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setPadding(dp(12), dp(10), dp(12), dp(10));
        item.setBackground(createRoundedBackground(
                getColor(R.color.chip_background),
                getColor(R.color.chip_stroke),
                8
        ));
        item.setClickable(true);
        item.setOnTouchListener(createSwipeTouchListener(
                item,
                () -> {
                    if (isSelectionMode()) {
                        toggleTaskSelection(task);
                    } else {
                        openPreferredTaskEditor(task);
                    }
                },
                () -> {
                    if (task.isSkipped()) {
                        unskipTask(task);
                    } else {
                        skipTask(task);
                    }
                },
                () -> deleteTask(task),
                () -> enterSelectionMode(task),
                task.getSubtasks().isEmpty() ? null : () -> setTaskExpanded(task, true),
                task.getSubtasks().isEmpty() ? null : () -> setTaskExpanded(task, false)
        ));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView completeButton = createCompletionButton(task);
        LinearLayout.LayoutParams completeParams = new LinearLayout.LayoutParams(dp(28), dp(28));
        completeParams.setMargins(0, 0, dp(10), 0);
        row.addView(completeButton, completeParams);

        String time = task.getReminderAt() == null ? "Р В Р’В±Р В Р’ВµР В Р’В· Р В Р вЂ Р РЋР вЂљР В Р’ВµР В РЎВР В Р’ВµР В Р вЂ¦Р В РЎвЂ" : task.getReminderAt().toLocalTime().toString();
        TextView timeView = createText(time, 13, R.color.text_secondary, true);
        timeView.setGravity(android.view.Gravity.CENTER);
        row.addView(timeView, new LinearLayout.LayoutParams(dp(72), ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = createText(task.getTitle(), 15, R.color.text_primary, true);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(title, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        ));

        if (!task.getSubtasks().isEmpty()) {
            TextView expandButton = createSubtaskExpandButton(task);
            LinearLayout.LayoutParams expandParams = new LinearLayout.LayoutParams(dp(30), dp(30));
            expandParams.setMargins(dp(8), 0, 0, 0);
            row.addView(expandButton, expandParams);
        }

        item.addView(row, fullWidth());

        String meta = calendarTaskMeta(task);
        if (!meta.isEmpty()) {
            TextView metaView = createText(meta, 12, R.color.text_secondary, false);
            metaView.setSingleLine(true);
            metaView.setEllipsize(TextUtils.TruncateAt.END);
            item.addView(metaView, fullWidthWithTopMargin(dp(6)));
        }

        if (!task.getSubtasks().isEmpty()) {
            item.addView(createSubtaskSummaryRow(task), fullWidthWithTopMargin(dp(8)));
            if (expandedTaskKeys.contains(task.getTaskKey())) {
                item.addView(createSubtaskList(task), fullWidthWithTopMargin(dp(6)));
            }
        }

        wrapper.addView(item, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        return wrapper;
    }

    private Map<LocalDate, List<ObsidianTask>> tasksByDate(List<ObsidianTask> tasks) {
        Map<LocalDate, List<ObsidianTask>> byDate = new HashMap<>();
        for (ObsidianTask task : tasks) {
            if (task.getReminderAt() == null) {
                continue;
            }
            LocalDate date = task.getReminderAt().toLocalDate();
            List<ObsidianTask> dayTasks = byDate.get(date);
            if (dayTasks == null) {
                dayTasks = new ArrayList<>();
                byDate.put(date, dayTasks);
            }
            dayTasks.add(task);
        }
        return byDate;
    }

    private LocalDate firstVisibleCalendarDay(YearMonth month) {
        LocalDate firstDay = month.atDay(1);
        int mondayBasedOffset = firstDay.getDayOfWeek().getValue() - DayOfWeek.MONDAY.getValue();
        return firstDay.minusDays(mondayBasedOffset);
    }

    private void selectCalendarDate(LocalDate date) {
        selectedCalendarDate = date;
        YearMonth dateMonth = YearMonth.from(date);
        if (!dateMonth.equals(displayedCalendarMonth)) {
            displayedCalendarMonth = dateMonth;
        }
        renderCalendar(latestTasks);
    }

    private void moveCalendarMonth(int monthDelta) {
        displayedCalendarMonth = displayedCalendarMonth.plusMonths(monthDelta);
        selectedCalendarDate = bestCalendarSelectionForMonth(displayedCalendarMonth, filterVisibleTasks(latestTasks));
        renderCalendar(latestTasks);
    }

    private void moveCalendarPeriod(int delta) {
        if (calendarMode == CALENDAR_MODE_YEAR) {
            displayedCalendarMonth = displayedCalendarMonth.plusYears(delta);
            LocalDate selected = selectedCalendarDate == null ? LocalDate.now() : selectedCalendarDate;
            YearMonth selectedMonth = YearMonth.of(displayedCalendarMonth.getYear(), selected.getMonthValue());
            selectedCalendarDate = selectedMonth.atDay(Math.min(
                    selected.getDayOfMonth(),
                    selectedMonth.lengthOfMonth()
            ));
            renderCalendar(latestTasks);
            return;
        }
        if (calendarMode == CALENDAR_MODE_WEEK) {
            LocalDate selected = selectedCalendarDate == null ? LocalDate.now() : selectedCalendarDate;
            selectedCalendarDate = selected.plusWeeks(delta);
            displayedCalendarMonth = YearMonth.from(selectedCalendarDate);
            renderCalendar(latestTasks);
            return;
        }
        moveCalendarMonth(delta);
    }

    private void openMonthFromYear(YearMonth month) {
        calendarMode = CALENDAR_MODE_MONTH;
        displayedCalendarMonth = month;
        selectedCalendarDate = bestCalendarSelectionForMonth(month, filterVisibleTasks(latestTasks));
        renderCalendar(latestTasks);
    }

    private LocalDate bestCalendarSelectionForMonth(YearMonth month, List<ObsidianTask> visibleTasks) {
        LocalDate today = LocalDate.now();
        if (YearMonth.from(today).equals(month)) {
            return today;
        }
        LocalDate firstTaskDate = null;
        for (ObsidianTask task : visibleTasks) {
            if (task.getReminderAt() == null) {
                continue;
            }
            LocalDate taskDate = task.getReminderAt().toLocalDate();
            if (!YearMonth.from(taskDate).equals(month)) {
                continue;
            }
            if (firstTaskDate == null || taskDate.isBefore(firstTaskDate)) {
                firstTaskDate = taskDate;
            }
        }
        return firstTaskDate == null ? month.atDay(1) : firstTaskDate;
    }

    private int compareTasksByTimeOnly(ObsidianTask first, ObsidianTask second) {
        int timeCompare = Comparator
                .nullsLast(LocalDateTime::compareTo)
                .compare(first.getReminderAt(), second.getReminderAt());
        if (timeCompare != 0) {
            return timeCompare;
        }
        return first.getTitle().compareToIgnoreCase(second.getTitle());
    }

    private CalendarTaskSummary summarizeCalendarDay(List<ObsidianTask> tasks) {
        CalendarTaskSummary summary = new CalendarTaskSummary();
        if (tasks == null || tasks.isEmpty()) {
            return summary;
        }
        for (ObsidianTask task : tasks) {
            addTaskToSummary(summary, task);
        }
        return summary;
    }

    private Map<YearMonth, CalendarTaskSummary> summarizeTasksByMonth(List<ObsidianTask> tasks) {
        Map<YearMonth, CalendarTaskSummary> summaries = new HashMap<>();
        int displayedYear = displayedCalendarMonth.getYear();
        for (ObsidianTask task : tasks) {
            if (task.getReminderAt() == null) {
                continue;
            }
            YearMonth month = YearMonth.from(task.getReminderAt());
            if (month.getYear() != displayedYear) {
                continue;
            }
            CalendarTaskSummary summary = summaries.get(month);
            if (summary == null) {
                summary = new CalendarTaskSummary();
                summaries.put(month, summary);
            }
            addTaskToSummary(summary, task);
        }
        return summaries;
    }

    private void addTaskToSummary(CalendarTaskSummary summary, ObsidianTask task) {
        summary.totalTaskCount++;
        TaskStatus status = taskStatus(task);
        if (status == TaskStatus.COMPLETED) {
            summary.completedCount++;
        } else if (status == TaskStatus.SKIPPED) {
            summary.skippedCount++;
        } else if (status == TaskStatus.OVERDUE) {
            summary.overdueCount++;
        } else {
            summary.activeCount++;
        }
    }

    private String calendarPeriodTitle() {
        if (calendarMode == CALENDAR_MODE_YEAR) {
            return String.valueOf(displayedCalendarMonth.getYear());
        }
        if (calendarMode == CALENDAR_MODE_WEEK) {
            LocalDate start = currentWeekStart();
            LocalDate end = start.plusDays(6);
            return formatWeekRange(start, end);
        }
        return capitalize(CALENDAR_MONTH_FORMAT.format(displayedCalendarMonth.atDay(1)));
    }

    private int periodTaskCount(Map<LocalDate, List<ObsidianTask>> tasksByDate) {
        int count = 0;
        if (calendarMode == CALENDAR_MODE_YEAR) {
            int year = displayedCalendarMonth.getYear();
            for (Map.Entry<LocalDate, List<ObsidianTask>> entry : tasksByDate.entrySet()) {
                if (entry.getKey().getYear() == year) {
                    count += entry.getValue().size();
                }
            }
            return count;
        }
        if (calendarMode == CALENDAR_MODE_WEEK) {
            LocalDate start = currentWeekStart();
            LocalDate end = start.plusDays(6);
            for (Map.Entry<LocalDate, List<ObsidianTask>> entry : tasksByDate.entrySet()) {
                LocalDate date = entry.getKey();
                if (!date.isBefore(start) && !date.isAfter(end)) {
                    count += entry.getValue().size();
                }
            }
            return count;
        }
        for (Map.Entry<LocalDate, List<ObsidianTask>> entry : tasksByDate.entrySet()) {
            if (YearMonth.from(entry.getKey()).equals(displayedCalendarMonth)) {
                count += entry.getValue().size();
            }
        }
        return count;
    }

    private LocalDate currentWeekStart() {
        LocalDate date = selectedCalendarDate == null ? LocalDate.now() : selectedCalendarDate;
        int mondayBasedOffset = date.getDayOfWeek().getValue() - DayOfWeek.MONDAY.getValue();
        return date.minusDays(mondayBasedOffset);
    }

    private String formatWeekRange(LocalDate start, LocalDate end) {
        if (start.getYear() == end.getYear() && start.getMonth() == end.getMonth()) {
            return start.getDayOfMonth()
                    + "-"
                    + capitalize(CALENDAR_WEEK_DAY_FORMAT.format(end))
                    + " "
                    + end.getYear();
        }
        if (start.getYear() == end.getYear()) {
            return capitalize(CALENDAR_WEEK_DAY_FORMAT.format(start))
                    + " - "
                    + CALENDAR_WEEK_DAY_FORMAT.format(end)
                    + " "
                    + end.getYear();
        }
        return capitalize(CALENDAR_WEEK_DAY_FORMAT.format(start))
                + " "
                + start.getYear()
                + " - "
                + CALENDAR_WEEK_DAY_FORMAT.format(end)
                + " "
                + end.getYear();
    }

    private String calendarTaskMeta(ObsidianTask task) {
        List<String> parts = new ArrayList<>();
        if (hasRepeatInfo(task)) {
            parts.add(formatRepeat(task));
        }
        String group = taskGroupLabel(task);
        if (!group.isEmpty()) {
            parts.add(group);
        }
        if (showTaskSourceNames) {
            parts.add(compactName(task.getSourceName()));
        }
        parts.add(formatStatus(taskStatus(task)));
        return TextUtils.join(" Р вЂ™Р’В· ", parts);
    }

    private String taskCountWord(int count) {
        int normalized = Math.abs(count) % 100;
        int lastDigit = normalized % 10;
        if (normalized >= 11 && normalized <= 14) {
            return "Р В Р’В·Р В Р’В°Р В РўвЂР В Р’В°Р РЋРІР‚РЋ";
        }
        if (lastDigit == 1) {
            return "Р В Р’В·Р В Р’В°Р В РўвЂР В Р’В°Р РЋРІР‚РЋР В Р’В°";
        }
        if (lastDigit >= 2 && lastDigit <= 4) {
            return "Р В Р’В·Р В Р’В°Р В РўвЂР В Р’В°Р РЋРІР‚РЋР В РЎвЂ";
        }
        return "Р В Р’В·Р В Р’В°Р В РўвЂР В Р’В°Р РЋРІР‚РЋ";
    }

    private static final class CalendarTaskSummary {
        private int totalTaskCount;
        private int completedCount;
        private int activeCount;
        private int skippedCount;
        private int overdueCount;
    }

    private Duration overdueGracePeriod() {
        return Duration.ofMinutes(ActionPreferences.getOverdueGraceMinutes(this));
    }

    private TaskStatus taskStatus(ObsidianTask task) {
        return task.getStatus(LocalDateTime.now(), overdueGracePeriod());
    }

    private List<ObsidianTask> filterVisibleTasks(List<ObsidianTask> tasks) {
        return TaskGrouping.filterVisibleTasks(
                tasks,
                UserPreferences.getTaskFilter(this),
                shouldHidePrivateTasks(),
                UserPreferences.getPrivateMarker(this),
                LocalDateTime.now(),
                overdueGracePeriod()
        );
    }

    private List<ObsidianTask> filterTasksBySelectedGroup(List<ObsidianTask> tasks) {
        return TaskGrouping.filterBySelectedBucket(
                tasks,
                UserPreferences.getTaskGroup(this),
                UserPreferences.getGroupingMode(this),
                this::sourceBucketLabel
        );
    }

    private List<ObsidianTask> tasksForGroupRow(List<ObsidianTask> tasks) {
        return TaskGrouping.filterVisibleTasks(
                tasks,
                UserPreferences.getTaskFilter(this),
                false,
                UserPreferences.getPrivateMarker(this),
                LocalDateTime.now(),
                overdueGracePeriod()
        );
    }

    private List<ObsidianTask> filterCalendarContextTasks(List<ObsidianTask> tasks) {
        List<ObsidianTask> visibleTasks = TaskGrouping.filterCalendarContextTasks(
                tasks,
                shouldHidePrivateTasks(),
                UserPreferences.getPrivateMarker(this)
        );
        return filterTasksBySelectedGroup(visibleTasks);
    }

    private int compareTasksForDisplay(ObsidianTask first, ObsidianTask second) {
        int statusCompare = Integer.compare(displayStatusRank(first), displayStatusRank(second));
        if (statusCompare != 0) {
            return statusCompare;
        }

        int timeCompare = Comparator
                .nullsLast(LocalDateTime::compareTo)
                .compare(first.getReminderAt(), second.getReminderAt());
        if (timeCompare != 0) {
            return timeCompare;
        }

        int sourceCompare = compactName(first.getSourceName())
                .compareToIgnoreCase(compactName(second.getSourceName()));
        if (sourceCompare != 0) {
            return sourceCompare;
        }

        return Integer.compare(first.getLineNumber(), second.getLineNumber());
    }

    private int displayStatusRank(ObsidianTask task) {
        if (task.isCompleted()) {
            return 3;
        }
        if (task.isSkipped()) {
            return 2;
        }
        return 0;
    }

    private void updateGroupFilterRow(List<ObsidianTask> tasks) {
        if (groupFilterRow == null) {
            return;
        }

        List<TaskGrouping.Bucket> buckets = TaskGrouping.collectBuckets(
                tasks,
                UserPreferences.getGroupingMode(this),
                this::sourceBucketLabel
        );

        groupFilterRow.removeAllViews();
        String selectedGroup = UserPreferences.getTaskGroup(this);
        if (buckets.isEmpty()) {
            if (selectedGroup != null && !selectedGroup.isEmpty()) {
                UserPreferences.setTaskGroup(this, "");
            }
            if (groupFilterContainerView != null) {
                groupFilterContainerView.setVisibility(View.GONE);
            }
            return;
        }
        if (groupFilterContainerView != null) {
            groupFilterContainerView.setVisibility(View.VISIBLE);
        }
        boolean selectedBucketStillExists = false;
        for (TaskGrouping.Bucket bucket : buckets) {
            if (bucket.getKey().equals(selectedGroup)) {
                selectedBucketStillExists = true;
                break;
            }
        }
        if (selectedGroup != null && !selectedGroup.isEmpty() && !selectedBucketStillExists) {
            UserPreferences.setTaskGroup(this, "");
        }

        addGroupChip(groupFilterRow, "Р В РІР‚в„ўР РЋР С“Р В Р’Вµ Р В РЎвЂ“Р РЋР вЂљР РЋРЎвЂњР В РЎвЂ”Р В РЎвЂ”Р РЋРІР‚в„–", "", 0);
        for (TaskGrouping.Bucket bucket : buckets) {
            addGroupChip(groupFilterRow, bucket.getLabel(), bucket.getKey(), dp(6));
        }
    }

    private void addGroupChip(LinearLayout row, String label, String group, int leftMargin) {
        Button chip = new Button(this);
        chip.setText(label);
        chip.setAllCaps(false);
        chip.setTextSize(12);
        chip.setSingleLine(true);
        chip.setMinHeight(0);
        chip.setMinimumWidth(0);
        chip.setPadding(dp(14), 0, dp(14), 0);
        boolean selected = Objects.equals(UserPreferences.getTaskGroup(this), group);
        styleFilterChip(chip, selected);
        chip.setEnabled(!isSelectionMode());
        chip.setAlpha(isSelectionMode() ? 0.55f : 1f);
        chip.setOnClickListener(view -> {
            if (isSelectionMode()) {
                return;
            }
            UserPreferences.setTaskGroup(this, group);
            readAndRenderNote();
        });
        addChip(row, chip, leftMargin);
    }

    private boolean hasMultipleSources(List<ObsidianTask> tasks) {
        String firstSource = null;
        for (ObsidianTask task : tasks) {
            String sourceName = task.getSourceName();
            if (firstSource == null) {
                firstSource = sourceName;
            } else if (!firstSource.equals(sourceName)) {
                return true;
            }
        }
        return false;
    }

    private String taskGroupLabel(ObsidianTask task) {
        return TaskGrouping.bucketFor(
                task,
                UserPreferences.getGroupingMode(this),
                this::sourceBucketLabel
        ).getLabel();
    }

    private String formatGroupMeta(ObsidianTask task) {
        String mode = UserPreferences.getGroupingMode(this);
        if (UserPreferences.GROUPING_TAG.equals(mode)) {
            return "Р РЋРІР‚С™Р В Р’ВµР В РЎвЂ“: " + taskGroupLabel(task);
        }
        if (UserPreferences.GROUPING_FILE.equals(mode)) {
            return "Р РЋРІР‚С›Р В Р’В°Р В РІвЂћвЂ“Р В Р’В»: " + taskGroupLabel(task);
        }
        if (UserPreferences.GROUPING_SMART.equals(mode)) {
            return "Р В РЎвЂќР В РЎвЂўР В Р вЂ¦Р РЋРІР‚С™Р В Р’ВµР В РЎвЂќР РЋР С“Р РЋРІР‚С™: " + taskGroupLabel(task);
        }
        return "Р В РЎвЂ“Р РЋР вЂљР РЋРЎвЂњР В РЎвЂ”Р В РЎвЂ”Р В Р’В°: " + taskGroupLabel(task);
    }

    private boolean shouldHidePrivateTasks() {
        String selectedGroup = UserPreferences.getTaskGroup(this);
        return selectedGroup == null || selectedGroup.isEmpty();
    }

    private String sourceBucketLabel(ObsidianTask task) {
        return compactName(task.getSourceName());
    }

    private View createSourceHeader(String sourceName) {
        TextView header = new TextView(this);
        header.setText(sourceName == null || sourceName.isEmpty() ? "Р В РІР‚ВР В Р’ВµР В Р’В· Р В РЎвЂР В РЎВР В Р’ВµР В Р вЂ¦Р В РЎвЂ Р РЋРІР‚С›Р В Р’В°Р В РІвЂћвЂ“Р В Р’В»Р В Р’В°" : sourceName);
        header.setTextColor(getColor(R.color.text_primary));
        header.setText(sourceName == null || sourceName.isEmpty()
                ? "Р В РІР‚ВР В Р’ВµР В Р’В· Р В РЎвЂР В РЎВР В Р’ВµР В Р вЂ¦Р В РЎвЂ Р РЋРІР‚С›Р В Р’В°Р В РІвЂћвЂ“Р В Р’В»Р В Р’В°"
                : compactName(sourceName));
        header.setTextSize(16);
        header.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        header.setPadding(0, dp(14), 0, dp(8));
        return header;
    }

    private View createLegacyTaskView(ObsidianTask task) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setPadding(dp(14), dp(12), dp(14), dp(12));
        item.setBackground(createCardBackground());

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText(task.getTitle());
        title.setTextColor(getColor(R.color.text_primary));
        title.setTextSize(17);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        titleRow.addView(title, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        ));

        item.addView(titleRow, new LinearLayout.LayoutParams(
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
        drawable.setColor(getColor(R.color.card_background));
        drawable.setCornerRadius(dp(8));
        drawable.setStroke(dp(1), getColor(R.color.card_stroke));
        return drawable;
    }

    private View createTaskView(ObsidianTask task) {
        FrameLayout wrapper = new FrameLayout(this);
        boolean selected = selectedTaskKeys.contains(task.getTaskKey());

        FrameLayout swipeBackground = createSwipeActionBackground(task);
        wrapper.addView(swipeBackground, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        LinearLayout item = createCardContainer();
        if (selected) {
            item.setBackground(createRoundedBackground(
                    getColor(R.color.chip_selected_background),
                    getColor(R.color.chip_selected_stroke),
                    8
            ));
        }
        item.setClickable(true);
        item.setOnTouchListener(createSwipeTouchListener(
                item,
                () -> {
                    if (isSelectionMode()) {
                        toggleTaskSelection(task);
                    } else {
                        openPreferredTaskEditor(task);
                    }
                },
                () -> {
                    if (task.isSkipped()) {
                        unskipTask(task);
                    } else {
                        skipTask(task);
                    }
                },
                () -> deleteTask(task),
                () -> enterSelectionMode(task),
                task.getSubtasks().isEmpty() ? null : () -> setTaskExpanded(task, true),
                task.getSubtasks().isEmpty() ? null : () -> setTaskExpanded(task, false)
        ));

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView completeButton = createCompletionButton(task);
        LinearLayout.LayoutParams completeParams = new LinearLayout.LayoutParams(dp(30), dp(30));
        completeParams.setMargins(0, 0, dp(10), 0);
        titleRow.addView(completeButton, completeParams);

        TextView title = createText(task.getTitle(), 16, R.color.text_primary, true);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        titleRow.addView(title, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        ));

        TextView statusChip = createStatusChip(taskStatus(task));
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(22)
        );
        statusParams.setMargins(dp(8), 0, 0, 0);
        titleRow.addView(statusChip, statusParams);

        if (!task.getSubtasks().isEmpty()) {
            TextView expandButton = createSubtaskExpandButton(task);
            LinearLayout.LayoutParams expandParams = new LinearLayout.LayoutParams(dp(30), dp(30));
            expandParams.setMargins(dp(8), 0, 0, 0);
            titleRow.addView(expandButton, expandParams);
        }

        item.addView(titleRow, fullWidth());
        item.addView(createMetaLine(R.drawable.ic_clock, task.getReminderAt() == null
                ? "Р В Р вЂ¦Р В Р’Вµ Р РЋРЎвЂњР В РЎвЂќР В Р’В°Р В Р’В·Р В Р’В°Р В Р вЂ¦Р В РЎвЂў"
                : DATE_TIME_FORMAT.format(task.getReminderAt())), fullWidthWithTopMargin(dp(10)));

        if (hasRepeatInfo(task)) {
            item.addView(createMetaLine(R.drawable.ic_repeat, formatRepeat(task)), fullWidthWithTopMargin(dp(6)));
        }

        if (showTaskSourceNames) {
            item.addView(createMetaLine(R.drawable.ic_file, compactName(task.getSourceName())), fullWidthWithTopMargin(dp(6)));
        }
        item.addView(createMetaLine(R.drawable.ic_label, formatGroupMeta(task)), fullWidthWithTopMargin(dp(6)));

        String secondary = formatSecondaryTaskMeta(task);
        if (!secondary.isEmpty()) {
            TextView secondaryMeta = createText(secondary, 11, R.color.text_secondary, false);
            secondaryMeta.setMaxLines(2);
            secondaryMeta.setEllipsize(TextUtils.TruncateAt.END);
            item.addView(secondaryMeta, fullWidthWithTopMargin(dp(8)));
        }

        if (!task.getSubtasks().isEmpty()) {
            item.addView(createSubtaskSummaryRow(task), fullWidthWithTopMargin(dp(8)));
            if (expandedTaskKeys.contains(task.getTaskKey())) {
                item.addView(createSubtaskList(task), fullWidthWithTopMargin(dp(6)));
            }
        }

        wrapper.addView(item, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, dp(10));
        wrapper.setLayoutParams(params);
        return wrapper;
    }

    private FrameLayout createSwipeActionBackground(ObsidianTask task) {
        FrameLayout background = new FrameLayout(this);
        background.setBackground(createRoundedBackground(
                getColor(R.color.card_background),
                getColor(R.color.card_stroke),
                8
        ));

        TextView skipAction = createSwipeActionLabel(
                task.isSkipped() ? "Р В РЎвЂєР РЋРІР‚С™Р В РЎВР В Р’ВµР В Р вЂ¦Р В РЎвЂР РЋРІР‚С™Р РЋР Р‰" : "Р В РЎСџР РЋР вЂљР В РЎвЂўР В РЎвЂ”Р РЋРЎвЂњР РЋР С“Р РЋРІР‚С™Р В РЎвЂР РЋРІР‚С™Р РЋР Р‰",
                R.color.status_skipped_background,
                R.color.status_skipped_text
        );
        skipAction.setGravity(android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.LEFT);
        skipAction.setPadding(dp(16), 0, dp(16), 0);
        skipAction.setOnClickListener(view -> {
            if (task.isSkipped()) {
                unskipTask(task);
            } else {
                skipTask(task);
            }
        });
        FrameLayout.LayoutParams skipParams = new FrameLayout.LayoutParams(
                dp(132),
                ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.Gravity.LEFT
        );
        background.addView(skipAction, skipParams);

        TextView deleteAction = createSwipeActionLabel(
                "Р В Р в‚¬Р В РўвЂР В Р’В°Р В Р’В»Р В РЎвЂР РЋРІР‚С™Р РЋР Р‰",
                R.color.status_overdue_background,
                R.color.status_overdue_text
        );
        deleteAction.setGravity(android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.RIGHT);
        deleteAction.setPadding(dp(16), 0, dp(16), 0);
        deleteAction.setOnClickListener(view -> deleteTask(task));
        FrameLayout.LayoutParams deleteParams = new FrameLayout.LayoutParams(
                dp(132),
                ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.Gravity.RIGHT
        );
        background.addView(deleteAction, deleteParams);
        return background;
    }

    private TextView createSubtaskExpandButton(ObsidianTask task) {
        boolean expanded = expandedTaskKeys.contains(task.getTaskKey());
        TextView button = createText(expanded ? "\u2304" : "\u203a", 22, R.color.text_secondary, true);
        button.setGravity(android.view.Gravity.CENTER);
        button.setContentDescription(expanded
                ? "\u0421\u0432\u0435\u0440\u043d\u0443\u0442\u044c \u043f\u043e\u0434\u0437\u0430\u0434\u0430\u0447\u0438"
                : "\u041f\u043e\u043a\u0430\u0437\u0430\u0442\u044c \u043f\u043e\u0434\u0437\u0430\u0434\u0430\u0447\u0438");
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setOnClickListener(view -> toggleTaskExpanded(task));
        return button;
    }

    private String formatSubtaskProgress(ObsidianTask task) {
        int total = task.getSubtasks().size();
        int completed = 0;
        for (ObsidianTask subtask : task.getSubtasks()) {
            if (subtask.isCompleted()) {
                completed++;
            }
        }
        return completed + "/" + total + " Р В РЎвЂ”Р В РЎвЂўР В РўвЂР В Р’В·Р В Р’В°Р В РўвЂР В Р’В°Р РЋРІР‚РЋ Р В Р вЂ Р РЋРІР‚в„–Р В РЎвЂ”Р В РЎвЂўР В Р’В»Р В Р вЂ¦Р В Р’ВµР В Р вЂ¦Р В РЎвЂў";
    }

    private View createSubtaskSummaryRow(ObsidianTask task) {
        TextView progress = createText(formatSubtaskProgress(task), 12, R.color.text_secondary, false);
        progress.setSingleLine(true);
        progress.setEllipsize(TextUtils.TruncateAt.END);
        progress.setPadding(0, dp(2), 0, 0);
        progress.setClickable(false);
        progress.setFocusable(false);
        return progress;
    }

    private void toggleTaskExpanded(ObsidianTask task) {
        if (task == null || task.getSubtasks().isEmpty()) {
            return;
        }
        if (expandedTaskKeys.contains(task.getTaskKey())) {
            expandedTaskKeys.remove(task.getTaskKey());
        } else {
            expandedTaskKeys.add(task.getTaskKey());
        }
        rerenderCurrentSection();
    }

    private void setTaskExpanded(ObsidianTask task, boolean expanded) {
        if (task == null || task.getSubtasks().isEmpty()) {
            return;
        }
        boolean changed;
        if (expanded) {
            changed = expandedTaskKeys.add(task.getTaskKey());
        } else {
            changed = expandedTaskKeys.remove(task.getTaskKey());
        }
        if (changed) {
            rerenderCurrentSection();
        }
    }

    private void rerenderCurrentSection() {
        if (selectedSection == SECTION_CALENDAR) {
            renderCalendar(latestTasks);
        } else {
            renderTasks(latestTasks);
        }
    }

    private LinearLayout createSubtaskList(ObsidianTask task) {
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.HORIZONTAL);
        list.setPadding(dp(10), dp(2), 0, 0);
        View rail = new View(this);
        rail.setBackground(createRoundedBackground(
                getColor(R.color.card_stroke),
                0,
                99
        ));
        LinearLayout.LayoutParams railParams = new LinearLayout.LayoutParams(dp(2), ViewGroup.LayoutParams.MATCH_PARENT);
        railParams.setMargins(dp(2), dp(4), dp(8), dp(4));
        list.addView(rail, railParams);

        LinearLayout items = new LinearLayout(this);
        items.setOrientation(LinearLayout.VERTICAL);
        for (ObsidianTask subtask : task.getSubtasks()) {
            items.addView(createSubtaskRow(subtask), fullWidthWithBottomMargin(dp(5)));
        }
        list.addView(items, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        ));
        return list;
    }

    private View createSubtaskRow(ObsidianTask subtask) {
        FrameLayout wrapper = new FrameLayout(this);
        wrapper.addView(createSwipeActionBackground(subtask), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(dp(9), dp(7), dp(8), dp(7));
        row.setBackground(createRoundedBackground(
                getColor(R.color.background),
                getColor(R.color.chip_stroke),
                8
        ));
        row.setClickable(true);
        row.setOnTouchListener(createSwipeTouchListener(
                row,
                () -> openPreferredTaskEditor(subtask),
                () -> {
                    if (subtask.isSkipped()) {
                        unskipTask(subtask);
                    } else {
                        skipTask(subtask);
                    }
                },
                () -> deleteTask(subtask),
                () -> enterSelectionMode(subtask)
        ));

        TextView status = createCompletionButton(subtask);
        row.addView(status, new LinearLayout.LayoutParams(dp(24), dp(24)));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView title = createText(subtask.getTitle(), 13, R.color.text_primary, true);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(title, fullWidth());
        String meta = subtaskMeta(subtask);
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
        textParams.setMargins(dp(10), 0, dp(8), 0);
        row.addView(texts, textParams);

        TextView chevron = createText("\u203a", 18, R.color.text_secondary, true);
        chevron.setGravity(android.view.Gravity.CENTER);
        row.addView(chevron, new LinearLayout.LayoutParams(dp(18), dp(18)));

        wrapper.addView(row, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        return wrapper;
    }

    private String subtaskMeta(ObsidianTask subtask) {
        List<String> parts = new ArrayList<>();
        if (subtask.getReminderAt() != null) {
            parts.add(DATE_TIME_FORMAT.format(subtask.getReminderAt()));
        }
        if (hasRepeatInfo(subtask)) {
            parts.add(formatRepeat(subtask));
        }
        parts.add(formatStatus(taskStatus(subtask)));
        return TextUtils.join(" Р вЂ™Р’В· ", parts);
    }

    private TextView createSwipeActionLabel(String text, int backgroundColor, int textColor) {
        TextView label = createText(text, 13, textColor, true);
        label.setSingleLine(true);
        label.setBackground(createRoundedBackground(getColor(backgroundColor), 0, 8));
        return label;
    }

    private View.OnTouchListener createSwipeTouchListener(
            View foreground,
            Runnable clickAction,
            Runnable rightAction,
            Runnable leftAction,
            Runnable longPressAction
    ) {
        return createSwipeTouchListener(
                foreground,
                clickAction,
                rightAction,
                leftAction,
                longPressAction,
                null,
                null
        );
    }

    private View.OnTouchListener createSwipeTouchListener(
            View foreground,
            Runnable clickAction,
            Runnable rightAction,
            Runnable leftAction,
            Runnable longPressAction,
            Runnable swipeDownAction,
            Runnable swipeUpAction
    ) {
        int touchSlop = ViewConfiguration.get(this).getScaledTouchSlop();
        int actionWidth = dp(132);
        int revealThreshold = dp(56);
        int verticalThreshold = dp(24);
        int longPressTimeout = ViewConfiguration.getLongPressTimeout();

        return new View.OnTouchListener() {
            private float downX;
            private float downY;
            private float startTranslationX;
            private boolean dragging;
            private boolean horizontalDragging;
            private boolean verticalDragging;
            private boolean longPressed;
            private final Handler longPressHandler = new Handler(Looper.getMainLooper());
            private final Runnable longPressRunnable = () -> {
                longPressed = true;
                foreground.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                if (longPressAction != null) {
                    longPressAction.run();
                }
            };

            @Override
            public boolean onTouch(View view, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    downX = event.getRawX();
                    downY = event.getRawY();
                    startTranslationX = foreground.getTranslationX();
                    dragging = false;
                    horizontalDragging = false;
                    verticalDragging = false;
                    longPressed = false;
                    foreground.animate().cancel();
                    longPressHandler.postDelayed(longPressRunnable, longPressTimeout);
                    return true;
                }

                if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    float dx = event.getRawX() - downX;
                    float dy = event.getRawY() - downY;
                    if (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop) {
                        longPressHandler.removeCallbacks(longPressRunnable);
                    }
                    if (isSelectionMode()) {
                        return true;
                    }
                    if (!dragging) {
                        boolean supportsVertical = swipeDownAction != null || swipeUpAction != null;
                        if (Math.abs(dx) > touchSlop && Math.abs(dx) > Math.abs(dy) * 1.2f) {
                            dragging = true;
                            horizontalDragging = true;
                            view.getParent().requestDisallowInterceptTouchEvent(true);
                        } else if (supportsVertical
                                && Math.abs(dy) > touchSlop
                                && Math.abs(dy) > Math.abs(dx) * 1.05f) {
                            dragging = true;
                            verticalDragging = true;
                            view.getParent().requestDisallowInterceptTouchEvent(true);
                        } else {
                            return true;
                        }
                    }

                    if (horizontalDragging) {
                        float target = clamp(startTranslationX + dx, -actionWidth, actionWidth);
                        foreground.setTranslationX(target);
                    }
                    return true;
                }

                if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                    longPressHandler.removeCallbacks(longPressRunnable);
                    animateSwipeTo(foreground, 0, null);
                    return true;
                }

                if (event.getAction() != MotionEvent.ACTION_UP) {
                    return true;
                }

                longPressHandler.removeCallbacks(longPressRunnable);
                if (longPressed) {
                    return true;
                }

                float dx = event.getRawX() - downX;
                float dy = event.getRawY() - downY;
                if (!dragging && Math.abs(dx) < touchSlop && Math.abs(dy) < touchSlop) {
                    if (Math.abs(foreground.getTranslationX()) > 0.5f) {
                        animateSwipeTo(foreground, 0, null);
                    } else {
                        clickAction.run();
                    }
                    return true;
                }

                if (verticalDragging) {
                    animateSwipeTo(foreground, 0, null);
                    if (dy >= verticalThreshold && swipeDownAction != null) {
                        swipeDownAction.run();
                    } else if (dy <= -verticalThreshold && swipeUpAction != null) {
                        swipeUpAction.run();
                    }
                    return true;
                }

                float translation = foreground.getTranslationX();
                if (Math.abs(translation) >= actionWidth * 0.92f) {
                    Runnable action = translation > 0 ? rightAction : leftAction;
                    animateSwipeTo(foreground, 0, action);
                } else if (Math.abs(translation) >= revealThreshold) {
                    animateSwipeTo(foreground, translation > 0 ? actionWidth : -actionWidth, null);
                } else {
                    animateSwipeTo(foreground, 0, null);
                }
                return true;
            }
        };
    }

    private void animateSwipeTo(View view, float target, Runnable endAction) {
        view.animate()
                .translationX(target)
                .setDuration(160L)
                .withEndAction(endAction)
                .start();
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private TextView createCompletionButton(ObsidianTask task) {
        TaskStatus status = taskStatus(task);
        TextView button = createText(statusIcon(status), 16, statusIconColor(status), true);
        button.setGravity(android.view.Gravity.CENTER);
        button.setContentDescription(isSelectionMode()
                ? "Р В РІР‚в„ўР РЋРІР‚в„–Р В Р’В±Р РЋР вЂљР В Р’В°Р РЋРІР‚С™Р РЋР Р‰"
                : statusIconDescription(status));
        button.setBackground(createCircleOutlineBackground(
                getColor(statusIconBackground(status)),
                getColor(statusIconStroke(status))
        ));
        button.setOnLongClickListener(view -> {
            enterSelectionMode(task);
            return true;
        });
        button.setOnClickListener(view -> {
            if (isSelectionMode()) {
                toggleTaskSelection(task);
                return;
            }
            if (status == TaskStatus.COMPLETED) {
                unmarkTaskDone(task);
            } else if (status == TaskStatus.SKIPPED) {
                unskipTask(task);
            } else {
                markTaskDone(task);
            }
        });
        return button;
    }

    private String statusIcon(TaskStatus status) {
        if (status == TaskStatus.COMPLETED) {
            return "Р Р†РЎС™РІР‚Сљ";
        }
        if (status == TaskStatus.SKIPPED) {
            return "Р В РІР‚СљР Р†Р вЂљРІР‚Сњ";
        }
        if (status == TaskStatus.OVERDUE) {
            return "!";
        }
        return "";
    }

    private int statusIconBackground(TaskStatus status) {
        if (status == TaskStatus.COMPLETED) {
            return R.color.status_completed_background;
        }
        if (status == TaskStatus.SKIPPED) {
            return R.color.status_skipped_background;
        }
        if (status == TaskStatus.OVERDUE) {
            return R.color.status_overdue_background;
        }
        return android.R.color.transparent;
    }

    private int statusIconStroke(TaskStatus status) {
        if (status == TaskStatus.COMPLETED) {
            return R.color.status_completed_text;
        }
        if (status == TaskStatus.SKIPPED) {
            return R.color.status_skipped_text;
        }
        if (status == TaskStatus.OVERDUE) {
            return R.color.status_overdue_text;
        }
        return R.color.text_secondary;
    }

    private int statusIconColor(TaskStatus status) {
        return statusIconStroke(status);
    }

    private String statusIconDescription(TaskStatus status) {
        if (status == TaskStatus.COMPLETED) {
            return "Р В Р Р‹Р В Р вЂ¦Р РЋР РЏР РЋРІР‚С™Р РЋР Р‰ Р В Р вЂ Р РЋРІР‚в„–Р В РЎвЂ”Р В РЎвЂўР В Р’В»Р В Р вЂ¦Р В Р’ВµР В Р вЂ¦Р В РЎвЂР В Р’Вµ";
        }
        if (status == TaskStatus.SKIPPED) {
            return "Р В РЎвЂєР РЋРІР‚С™Р В РЎВР В Р’ВµР В Р вЂ¦Р В РЎвЂР РЋРІР‚С™Р РЋР Р‰ Р В РЎвЂ”Р РЋР вЂљР В РЎвЂўР В РЎвЂ”Р РЋРЎвЂњР РЋР С“Р В РЎвЂќ";
        }
        if (status == TaskStatus.OVERDUE) {
            return "Р В РІР‚в„ўР РЋРІР‚в„–Р В РЎвЂ”Р В РЎвЂўР В Р’В»Р В Р вЂ¦Р В РЎвЂР РЋРІР‚С™Р РЋР Р‰ Р В РЎвЂ”Р РЋР вЂљР В РЎвЂўР РЋР С“Р РЋР вЂљР В РЎвЂўР РЋРІР‚РЋР В Р’ВµР В Р вЂ¦Р В Р вЂ¦Р РЋРЎвЂњР РЋР вЂ№ Р В Р’В·Р В Р’В°Р В РўвЂР В Р’В°Р РЋРІР‚РЋР РЋРЎвЂњ";
        }
        return "Р В РІР‚в„ўР РЋРІР‚в„–Р В РЎвЂ”Р В РЎвЂўР В Р’В»Р В Р вЂ¦Р В РЎвЂР РЋРІР‚С™Р РЋР Р‰";
    }

    private TextView createStatusChip(TaskStatus status) {
        int background;
        int textColor;
        String label;
        if (status == TaskStatus.COMPLETED) {
            background = R.color.status_completed_background;
            textColor = R.color.status_completed_text;
            label = "Р В РІР‚вЂќР В Р’В°Р В Р вЂ Р В Р’ВµР РЋР вЂљР РЋРІвЂљВ¬Р В Р’ВµР В Р вЂ¦Р В Р’В°";
        } else if (status == TaskStatus.SKIPPED) {
            background = R.color.status_skipped_background;
            textColor = R.color.status_skipped_text;
            label = "Р В РЎСџР РЋР вЂљР В РЎвЂўР В РЎвЂ”Р РЋРЎвЂњР РЋРІР‚В°Р В Р’ВµР В Р вЂ¦Р В Р’В°";
        } else if (status == TaskStatus.OVERDUE) {
            background = R.color.status_overdue_background;
            textColor = R.color.status_overdue_text;
            label = "Р В РЎСџР РЋР вЂљР В РЎвЂўР РЋР С“Р РЋР вЂљР В РЎвЂўР РЋРІР‚РЋР В Р’ВµР В Р вЂ¦Р В Р’В°";
        } else {
            background = R.color.status_waiting_background;
            textColor = R.color.status_waiting_text;
            label = "Р В РЎвЂєР В Р’В¶Р В РЎвЂР В РўвЂР В Р’В°Р В Р’ВµР РЋРІР‚С™";
        }

        TextView chip = createText(label, 10, textColor, true);
        chip.setGravity(android.view.Gravity.CENTER);
        chip.setPadding(dp(7), 0, dp(7), 0);
        chip.setBackground(createRoundedBackground(getColor(background), 0, 8));
        return chip;
    }

    private LinearLayout createMetaLine(int iconRes, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(getColor(R.color.text_secondary));
        row.addView(icon, new LinearLayout.LayoutParams(dp(15), dp(15)));

        TextView textView = createText(value, 12, R.color.text_secondary, false);
        textView.setSingleLine(true);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        );
        textParams.setMargins(dp(8), 0, 0, 0);
        row.addView(textView, textParams);
        return row;
    }

    @SuppressWarnings("deprecation")
    private void openTaskEditor(ObsidianTask task) {
        Intent intent = new Intent(this, TaskEditActivity.class);
        if (task != null) {
            intent.putExtra(TaskEditActivity.EXTRA_TASK_KEY, task.getTaskKey());
        }
        startActivityForResult(intent, REQUEST_EDIT_TASK);
    }

    @SuppressWarnings("deprecation")
    private void openTaskEditorForDate(LocalDate date) {
        Intent intent = new Intent(this, TaskEditActivity.class);
        intent.putExtra(TaskEditActivity.EXTRA_DUE_DATE,
                (date == null ? LocalDate.now() : date).toString());
        startActivityForResult(intent, REQUEST_EDIT_TASK);
    }

    private void openPreferredTaskEditor(ObsidianTask task) {
        if (task != null && EditPreferences.MODE_MARKDOWN.equals(EditPreferences.getEditMode(this))) {
            openMarkdownEditor(task);
            return;
        }
        openTaskEditor(task);
    }

    @SuppressWarnings("deprecation")
    private void openMarkdownEditor(ObsidianTask task) {
        Intent intent = new Intent(this, MarkdownFileEditActivity.class);
        if (task != null) {
            intent.putExtra(MarkdownFileEditActivity.EXTRA_TASK_KEY, task.getTaskKey());
        }
        startActivityForResult(intent, REQUEST_EDIT_TASK);
    }

    private void bulkMarkSelectedDone() {
        List<String> taskKeys = selectedTaskKeyList();
        if (taskKeys.isEmpty()) {
            exitSelectionMode();
            return;
        }

        NoteStore.BulkEditResult result = NoteStore.markTasksDone(this, taskKeys);
        for (String taskKey : result.getUpdatedTaskKeys()) {
            ReminderScheduler.cancelReminder(this, taskKey);
        }
        finishBulkOperation(result, "Р В РЎвЂўР РЋРІР‚С™Р В РЎВР В Р’ВµР РЋРІР‚РЋР В Р’ВµР В Р вЂ¦Р В РЎвЂў Р В Р вЂ Р РЋРІР‚в„–Р В РЎвЂ”Р В РЎвЂўР В Р’В»Р В Р вЂ¦Р В Р’ВµР В Р вЂ¦Р В Р вЂ¦Р РЋРІР‚в„–Р В РЎВР В РЎвЂ");
    }

    private void bulkSkipSelected() {
        List<String> taskKeys = selectedTaskKeyList();
        if (taskKeys.isEmpty()) {
            exitSelectionMode();
            return;
        }

        NoteStore.BulkEditResult result = NoteStore.markTasksSkipped(this, taskKeys);
        for (String taskKey : result.getUpdatedTaskKeys()) {
            ReminderScheduler.cancelReminder(this, taskKey);
        }
        finishBulkOperation(result, "Р В РЎвЂ”Р РЋР вЂљР В РЎвЂўР В РЎвЂ”Р РЋРЎвЂњР РЋРІР‚В°Р В Р’ВµР В Р вЂ¦Р В РЎвЂў");
    }

    private void bulkSnoozeSelected() {
        List<ObsidianTask> tasks = selectedTasks();
        if (tasks.isEmpty()) {
            exitSelectionMode();
            return;
        }

        int updatedCount = 0;
        int skippedCount = 0;
        List<String> snoozedTaskKeys = new ArrayList<>();
        for (ObsidianTask task : tasks) {
            if (task.isCompleted() || task.isSkipped()) {
                skippedCount++;
                continue;
            }
            Duration snoozeDuration = effectiveSnoozeDuration(task);
            ReminderScheduler.scheduleSnooze(
                    this,
                    task.getTaskKey(),
                    notificationIdFor(task),
                    task.getLineNumber(),
                    task.getTitle(),
                    snoozeDuration,
                    task.getRepeatIntervalMillis(),
                    task.getRepeatMode()
            );
            snoozedTaskKeys.add(task.getTaskKey());
            updatedCount++;
        }

        NoteStore.BulkEditResult recordResult = null;
        if (ActionPreferences.shouldRecordSnoozeCount(this) && !snoozedTaskKeys.isEmpty()) {
            recordResult = NoteStore.incrementSnoozeCounts(this, snoozedTaskKeys);
        }

        selectedTaskKeys.clear();
        refreshTopAppBar();
        readAndRenderNote();
        String message = updatedCount + " " + taskCountWord(updatedCount)
                + " Р В РЎвЂўР РЋРІР‚С™Р В Р’В»Р В РЎвЂўР В Р’В¶Р В Р’ВµР В Р вЂ¦Р В РЎвЂў";
        if (skippedCount > 0) {
            message += ", Р В РЎвЂ”Р РЋР вЂљР В РЎвЂўР В РЎвЂ”Р РЋРЎвЂњР РЋРІР‚В°Р В Р’ВµР В Р вЂ¦Р В РЎвЂў: " + skippedCount;
        }
        if (recordResult != null && recordResult.hasFailures()) {
            message += ", Р РЋР С“Р РЋРІР‚РЋР В Р’ВµР РЋРІР‚С™Р РЋРІР‚РЋР В РЎвЂР В РЎвЂќ Р В Р’В·Р В Р’В°Р В РЎвЂ”Р В РЎвЂР РЋР С“Р В Р’В°Р В Р вЂ¦ Р В Р вЂ¦Р В Р’Вµ Р В РЎвЂ”Р В РЎвЂўР В Р’В»Р В Р вЂ¦Р В РЎвЂўР РЋР С“Р РЋРІР‚С™Р РЋР Р‰Р РЋР вЂ№";
        }
        showSnackbar(message, null, null);
    }

    private void confirmBulkDeleteSelected() {
        List<ObsidianTask> tasks = selectedTasks();
        if (tasks.isEmpty()) {
            exitSelectionMode();
            return;
        }

        Set<String> sourceNames = new LinkedHashSet<>();
        for (ObsidianTask task : tasks) {
            sourceNames.add(task.getSourceName());
        }
        String message = "Р В РІР‚ВР РЋРЎвЂњР В РўвЂР В Р’ВµР РЋРІР‚С™ Р РЋРЎвЂњР В РўвЂР В Р’В°Р В Р’В»Р В Р’ВµР В Р вЂ¦Р В РЎвЂў: " + tasks.size() + " " + taskCountWord(tasks.size()) + ".";
        if (sourceNames.size() > 1) {
            message += "\nР В РІР‚вЂќР В Р’В°Р В РўвЂР В Р’В°Р РЋРІР‚РЋР В РЎвЂ Р В Р вЂ¦Р В Р’В°Р РЋРІР‚В¦Р В РЎвЂўР В РўвЂР РЋР РЏР РЋРІР‚С™Р РЋР С“Р РЋР РЏ Р В Р вЂ  Р РЋР вЂљР В Р’В°Р В Р’В·Р В Р вЂ¦Р РЋРІР‚в„–Р РЋРІР‚В¦ markdown-Р РЋРІР‚С›Р В Р’В°Р В РІвЂћвЂ“Р В Р’В»Р В Р’В°Р РЋРІР‚В¦.";
        }

        new AlertDialog.Builder(this)
                .setTitle("Р В Р в‚¬Р В РўвЂР В Р’В°Р В Р’В»Р В РЎвЂР РЋРІР‚С™Р РЋР Р‰ Р В Р вЂ Р РЋРІР‚в„–Р В Р’В±Р РЋР вЂљР В Р’В°Р В Р вЂ¦Р В Р вЂ¦Р РЋРІР‚в„–Р В Р’Вµ Р В Р’В·Р В Р’В°Р В РўвЂР В Р’В°Р РЋРІР‚РЋР В РЎвЂ?")
                .setMessage(message)
                .setNegativeButton("Р В РЎвЂєР РЋРІР‚С™Р В РЎВР В Р’ВµР В Р вЂ¦Р В Р’В°", null)
                .setPositiveButton("Р В Р в‚¬Р В РўвЂР В Р’В°Р В Р’В»Р В РЎвЂР РЋРІР‚С™Р РЋР Р‰", (dialog, which) -> bulkDeleteSelected())
                .show();
    }

    private void bulkDeleteSelected() {
        List<String> taskKeys = selectedTaskKeyList();
        if (taskKeys.isEmpty()) {
            exitSelectionMode();
            return;
        }

        NoteStore.BulkEditResult result = NoteStore.deleteTaskLines(this, taskKeys);
        for (String taskKey : result.getUpdatedTaskKeys()) {
            ReminderScheduler.cancelReminder(this, taskKey);
        }
        finishBulkOperation(result, "Р РЋРЎвЂњР В РўвЂР В Р’В°Р В Р’В»Р В Р’ВµР В Р вЂ¦Р В РЎвЂў");
    }

    private List<String> selectedTaskKeyList() {
        return new ArrayList<>(selectedTaskKeys);
    }

    private void finishBulkOperation(NoteStore.BulkEditResult result, String successAction) {
        selectedTaskKeys.clear();
        if (result.hasUpdates()) {
            NoteChangeMonitor.syncNow(this, true);
        }
        refreshTopAppBar();
        readAndRenderNote();
        showSnackbar(formatBulkResult(result, successAction), null, null);
    }

    private String formatBulkResult(NoteStore.BulkEditResult result, String successAction) {
        StringBuilder message = new StringBuilder();
        message.append(result.getUpdatedCount())
                .append(' ')
                .append(taskCountWord(result.getUpdatedCount()))
                .append(' ')
                .append(successAction);
        if (result.getSkippedCount() > 0) {
            message.append(", Р В РЎвЂ”Р РЋР вЂљР В РЎвЂўР В РЎвЂ”Р РЋРЎвЂњР РЋРІР‚В°Р В Р’ВµР В Р вЂ¦Р В РЎвЂў: ").append(result.getSkippedCount());
        }
        int failed = result.getFailedCount() + result.getNotFoundCount();
        if (failed > 0) {
            message.append(", Р В Р вЂ¦Р В Р’Вµ Р РЋРЎвЂњР В РўвЂР В Р’В°Р В Р’В»Р В РЎвЂўР РЋР С“Р РЋР Р‰: ").append(failed);
        }
        return message.toString();
    }

    private void markTaskDone(ObsidianTask task) {
        TaskEditResult result = NoteStore.markTaskDone(this, task.getTaskKey());
        if (result.shouldStopReminder()) {
            ReminderScheduler.cancelReminder(this, task.getTaskKey());
            NoteChangeMonitor.syncNow(this, true);
            readAndRenderNote();
        }
        showSnackbar(result.getMessage(), null, null);
    }

    private void unmarkTaskDone(ObsidianTask task) {
        TaskEditResult result = NoteStore.unmarkTaskDone(this, task.getTaskKey());
        if (result.isUpdated()) {
            NoteChangeMonitor.syncNow(this, true);
            readAndRenderNote();
        }
        showSnackbar(result.isUpdated() ? "Р В РІР‚в„ўР РЋРІР‚в„–Р В РЎвЂ”Р В РЎвЂўР В Р’В»Р В Р вЂ¦Р В Р’ВµР В Р вЂ¦Р В РЎвЂР В Р’Вµ Р РЋР С“Р В Р вЂ¦Р РЋР РЏР РЋРІР‚С™Р В РЎвЂў" : result.getMessage(), null, null);
    }

    private void snoozeTask(ObsidianTask task) {
        ReminderScheduler.scheduleSnooze(
                this,
                task.getTaskKey(),
                notificationIdFor(task),
                task.getLineNumber(),
                task.getTitle(),
                effectiveSnoozeDuration(task),
                task.getRepeatIntervalMillis(),
                task.getRepeatMode()
        );
        if (ActionPreferences.shouldRecordSnoozeCount(this)) {
            NoteStore.incrementSnoozeCount(this, task.getTaskKey());
        }
        showSnackbar("Р В Р в‚¬Р В Р вЂ Р В Р’ВµР В РўвЂР В РЎвЂўР В РЎВР В Р’В»Р В Р’ВµР В Р вЂ¦Р В РЎвЂР В Р’Вµ Р В РЎвЂўР РЋРІР‚С™Р В Р’В»Р В РЎвЂўР В Р’В¶Р В Р’ВµР В Р вЂ¦Р В РЎвЂў", null, null);
        readAndRenderNote();
    }

    private Duration effectiveSnoozeDuration(ObsidianTask task) {
        if (task != null && task.getSnoozeDuration() != null && !task.getSnoozeDuration().isZero()) {
            return task.getSnoozeDuration();
        }
        return Duration.ofMinutes(ActionPreferences.getSnoozeMinutes(this));
    }

    private void skipTask(ObsidianTask task) {
        TaskEditResult result = NoteStore.markTaskSkipped(this, task.getTaskKey());
        if (result.shouldStopReminder()) {
            ReminderScheduler.cancelReminder(this, task.getTaskKey());
            NoteChangeMonitor.syncNow(this, true);
            readAndRenderNote();
        }
        if (result.isUpdated()) {
            showSnackbar("Р В РІР‚вЂќР В Р’В°Р В РўвЂР В Р’В°Р РЋРІР‚РЋР В Р’В° Р В РЎвЂ”Р РЋР вЂљР В РЎвЂўР В РЎвЂ”Р РЋРЎвЂњР РЋРІР‚В°Р В Р’ВµР В Р вЂ¦Р В Р’В°", "Р В РЎвЂєР РЋРІР‚С™Р В РЎВР В Р’ВµР В Р вЂ¦Р В РЎвЂР РЋРІР‚С™Р РЋР Р‰", () -> unskipTask(task));
        } else {
            showSnackbar(result.getMessage(), null, null);
        }
    }

    private void unskipTask(ObsidianTask task) {
        TaskEditResult result = NoteStore.unmarkTaskSkipped(this, task.getTaskKey());
        if (result.isUpdated()) {
            NoteChangeMonitor.syncNow(this, true);
            readAndRenderNote();
        }
        showSnackbar(result.isUpdated() ? "Р В РЎСџР РЋР вЂљР В РЎвЂўР В РЎвЂ”Р РЋРЎвЂњР РЋР С“Р В РЎвЂќ Р В РЎвЂўР РЋРІР‚С™Р В РЎВР В Р’ВµР В Р вЂ¦Р В Р’ВµР В Р вЂ¦" : result.getMessage(), null, null);
    }

    private void deleteTask(ObsidianTask task) {
        NoteStore.TaskBlockSnapshot snapshot = null;
        try {
            snapshot = NoteStore.captureTaskBlockSnapshot(this, task.getTaskKey());
        } catch (IOException | RuntimeException exception) {
            ErrorLog.record(this, "Р В РЎСљР В Р’Вµ Р РЋРЎвЂњР В РўвЂР В Р’В°Р В Р’В»Р В РЎвЂўР РЋР С“Р РЋР Р‰ Р В РЎвЂ”Р В РЎвЂўР В РўвЂР В РЎвЂ“Р В РЎвЂўР РЋРІР‚С™Р В РЎвЂўР В Р вЂ Р В РЎвЂР РЋРІР‚С™Р РЋР Р‰ Р В РЎвЂўР РЋРІР‚С™Р В РЎвЂќР В Р’В°Р РЋРІР‚С™ Р РЋРЎвЂњР В РўвЂР В Р’В°Р В Р’В»Р В Р’ВµР В Р вЂ¦Р В РЎвЂР РЋР РЏ Р В Р’В·Р В Р’В°Р В РўвЂР В Р’В°Р РЋРІР‚РЋР В РЎвЂ", exception);
        }

        TaskEditResult result = NoteStore.deleteTaskBlock(this, task.getTaskKey());
        if (result.isUpdated()) {
            ReminderScheduler.cancelReminder(this, task.getTaskKey());
            NoteChangeMonitor.syncNow(this, true);
            readAndRenderNote();
            if (snapshot != null) {
                NoteStore.TaskBlockSnapshot finalSnapshot = snapshot;
                showSnackbar("Р В РІР‚вЂќР В Р’В°Р В РўвЂР В Р’В°Р РЋРІР‚РЋР В Р’В° Р РЋРЎвЂњР В РўвЂР В Р’В°Р В Р’В»Р В Р’ВµР В Р вЂ¦Р В Р’В°", "Р В РЎвЂєР РЋРІР‚С™Р В РЎВР В Р’ВµР В Р вЂ¦Р В РЎвЂР РЋРІР‚С™Р РЋР Р‰", () -> undoDeleteTask(finalSnapshot));
            } else {
                showSnackbar("Р В РІР‚вЂќР В Р’В°Р В РўвЂР В Р’В°Р РЋРІР‚РЋР В Р’В° Р РЋРЎвЂњР В РўвЂР В Р’В°Р В Р’В»Р В Р’ВµР В Р вЂ¦Р В Р’В°", null, null);
            }
            return;
        }
        showSnackbar(result.getMessage(), null, null);
    }

    private void undoDeleteTask(NoteStore.TaskBlockSnapshot snapshot) {
        TaskEditResult result = NoteStore.restoreTaskBlock(this, snapshot);
        if (result.isUpdated()) {
            NoteChangeMonitor.syncNow(this, true);
            readAndRenderNote();
            showSnackbar("Р В Р в‚¬Р В РўвЂР В Р’В°Р В Р’В»Р В Р’ВµР В Р вЂ¦Р В РЎвЂР В Р’Вµ Р В РЎвЂўР РЋРІР‚С™Р В РЎВР В Р’ВµР В Р вЂ¦Р В Р’ВµР В Р вЂ¦Р В РЎвЂў", null, null);
            return;
        }
        showSnackbar(result.getMessage(), null, null);
    }

    private void openNoteForTask(ObsidianTask task) {
        Uri uri = null;
        try {
            NoteStore.TaskDocumentMatch match = NoteStore.findTaskDocument(this, task.getTaskKey());
            if (match != null) {
                uri = match.getUri();
            }
        } catch (IOException | RuntimeException exception) {
            ErrorLog.record(this, "Р В РЎСљР В Р’Вµ Р РЋРЎвЂњР В РўвЂР В Р’В°Р В Р’В»Р В РЎвЂўР РЋР С“Р РЋР Р‰ Р В Р вЂ¦Р В Р’В°Р В РІвЂћвЂ“Р РЋРІР‚С™Р В РЎвЂ Р В Р’В·Р В Р’В°Р В РЎВР В Р’ВµР РЋРІР‚С™Р В РЎвЂќР РЋРЎвЂњ Р В РўвЂР В Р’В»Р РЋР РЏ Р В РЎвЂўР РЋРІР‚С™Р В РЎвЂќР РЋР вЂљР РЋРІР‚в„–Р РЋРІР‚С™Р В РЎвЂР РЋР РЏ", exception);
        }

        if (uri == null) {
            uri = TaskSourceManager.getActiveSourceUri(this);
        }
        if (uri == null) {
            Toast.makeText(this, "Р В Р’ВР РЋР С“Р РЋРІР‚С™Р В РЎвЂўР РЋРІР‚РЋР В Р вЂ¦Р В РЎвЂР В РЎвЂќ Р В Р вЂ¦Р В Р’Вµ Р В Р вЂ Р РЋРІР‚в„–Р В Р’В±Р РЋР вЂљР В Р’В°Р В Р вЂ¦", Toast.LENGTH_LONG).show();
            return;
        }
        if ("file".equalsIgnoreCase(uri.getScheme())) {
            Toast.makeText(this, "Р В РЎСџР РЋР вЂљР РЋР РЏР В РЎВР В РЎвЂўР В Р’Вµ Р В РЎвЂўР РЋРІР‚С™Р В РЎвЂќР РЋР вЂљР РЋРІР‚в„–Р РЋРІР‚С™Р В РЎвЂР В Р’Вµ Р В РўвЂР В РЎвЂўР РЋР С“Р РЋРІР‚С™Р РЋРЎвЂњР В РЎвЂ”Р В Р вЂ¦Р В РЎвЂў Р В РўвЂР В Р’В»Р РЋР РЏ Р РЋРІР‚С›Р В Р’В°Р В РІвЂћвЂ“Р В Р’В»Р В РЎвЂўР В Р вЂ , Р В Р вЂ Р РЋРІР‚в„–Р В Р’В±Р РЋР вЂљР В Р’В°Р В Р вЂ¦Р В Р вЂ¦Р РЋРІР‚в„–Р РЋРІР‚В¦ Р РЋРІР‚РЋР В Р’ВµР РЋР вЂљР В Р’ВµР В Р’В· Android picker", Toast.LENGTH_LONG).show();
            return;
        }

        Intent openNoteIntent = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "text/markdown")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        try {
            startActivity(openNoteIntent);
        } catch (RuntimeException exception) {
            ErrorLog.record(this, "Р В РЎСљР В Р’Вµ Р РЋРЎвЂњР В РўвЂР В Р’В°Р В Р’В»Р В РЎвЂўР РЋР С“Р РЋР Р‰ Р В РЎвЂўР РЋРІР‚С™Р В РЎвЂќР РЋР вЂљР РЋРІР‚в„–Р РЋРІР‚С™Р РЋР Р‰ Р В Р’В·Р В Р’В°Р В РЎВР В Р’ВµР РЋРІР‚С™Р В РЎвЂќР РЋРЎвЂњ Р В Р вЂ¦Р В Р’В°Р В РЎвЂ”Р РЋР вЂљР РЋР РЏР В РЎВР РЋРЎвЂњР РЋР вЂ№", exception);
            Toast.makeText(this, "Р В РЎСљР В Р’Вµ Р РЋРЎвЂњР В РўвЂР В Р’В°Р В Р’В»Р В РЎвЂўР РЋР С“Р РЋР Р‰ Р В РЎвЂўР РЋРІР‚С™Р В РЎвЂќР РЋР вЂљР РЋРІР‚в„–Р РЋРІР‚С™Р РЋР Р‰ Р В Р’В·Р В Р’В°Р В РЎВР В Р’ВµР РЋРІР‚С™Р В РЎвЂќР РЋРЎвЂњ Р В Р вЂ¦Р В Р’В°Р В РЎвЂ”Р РЋР вЂљР РЋР РЏР В РЎВР РЋРЎвЂњР РЋР вЂ№", Toast.LENGTH_LONG).show();
        }
    }

    private int notificationIdFor(ObsidianTask task) {
        int hash = Objects.hash(task.getTaskKey());
        if (hash == Integer.MIN_VALUE) {
            hash = 0;
        }

        int id = Math.abs(hash);
        return id == 0 ? task.getLineNumber() + 1 : id;
    }

    private String formatMeta(ObsidianTask task) {
        StringBuilder builder = new StringBuilder();
        builder.append(formatStatus(taskStatus(task)));
        if (!task.getSourceName().isEmpty()) {
            builder.append(" Р вЂ™Р’В· ").append(task.getSourceName());
        }
        if (task.getReminderAt() != null) {
            builder.append(" Р вЂ™Р’В· Р В Р вЂ¦Р В Р’В°Р В РЎвЂ”Р В РЎвЂўР В РЎВР В Р вЂ¦Р В РЎвЂР РЋРІР‚С™Р РЋР Р‰ ").append(DATE_TIME_FORMAT.format(task.getReminderAt()));
        } else {
            builder.append(" Р вЂ™Р’В· Р В Р вЂ Р РЋР вЂљР В Р’ВµР В РЎВР РЋР РЏ Р В Р вЂ¦Р В Р’Вµ Р РЋРЎвЂњР В РЎвЂќР В Р’В°Р В Р’В·Р В Р’В°Р В Р вЂ¦Р В РЎвЂў");
        }

        if (hasRepeatInfo(task)) {
            builder.append(" Р вЂ™Р’В· ").append(formatRepeat(task));
        }

        if (task.getPriority() != TaskPriority.NONE) {
            builder.append(" Р вЂ™Р’В· ").append(formatPriority(task.getPriority()));
        }

        if (!task.getTags().isEmpty()) {
            builder.append(" Р вЂ™Р’В· ").append(formatTags(task.getTags()));
        }

        return builder.toString();
    }

    private String formatStatus(TaskStatus status) {
        if (status == TaskStatus.COMPLETED) {
            return "Р В Р’В·Р В Р’В°Р В Р вЂ Р В Р’ВµР РЋР вЂљР РЋРІвЂљВ¬Р В Р’ВµР В Р вЂ¦Р В Р’В°";
        }
        if (status == TaskStatus.SKIPPED) {
            return "Р В РЎвЂ”Р РЋР вЂљР В РЎвЂўР В РЎвЂ”Р РЋРЎвЂњР РЋРІР‚В°Р В Р’ВµР В Р вЂ¦Р В Р’В°";
        }
        if (status == TaskStatus.OVERDUE) {
            return "Р В РЎвЂ”Р РЋР вЂљР В РЎвЂўР РЋР С“Р РЋР вЂљР В РЎвЂўР РЋРІР‚РЋР В Р’ВµР В Р вЂ¦Р В Р’В°";
        }
        return "Р В РЎвЂўР В Р’В¶Р В РЎвЂР В РўвЂР В Р’В°Р В Р’ВµР РЋРІР‚С™";
    }

    private String formatRepeat(ObsidianTask task) {
        List<String> parts = new ArrayList<>();
        if (task.getRepeatRule() != null) {
            parts.add("Р В РЎвЂ”Р В РЎвЂўР В Р вЂ Р РЋРІР‚С™Р В РЎвЂўР РЋР вЂљ " + task.getRepeatRule().formatForUi());
        }
        if (task.getResolvedRepeatUntilDoneInterval() != null) {
            parts.add("Р В РўвЂР В РЎвЂў Р В Р вЂ Р РЋРІР‚в„–Р В РЎвЂ”Р В РЎвЂўР В Р’В»Р В Р вЂ¦Р В Р’ВµР В Р вЂ¦Р В РЎвЂР РЋР РЏ " + formatDuration(task.getResolvedRepeatUntilDoneInterval()));
        }
        if (parts.isEmpty() && task.getRepeatInterval() != null) {
            parts.add("Р В РЎвЂ”Р В РЎвЂўР В Р вЂ Р РЋРІР‚С™Р В РЎвЂўР РЋР вЂљ " + formatDuration(task.getRepeatInterval()));
        }
        return TextUtils.join(" Р вЂ™Р’В· ", parts);
    }

    private boolean hasRepeatInfo(ObsidianTask task) {
        return task != null
                && (task.getRepeatRule() != null || task.getResolvedRepeatUntilDoneInterval() != null);
    }

    private String formatPriority(TaskPriority priority) {
        if (priority == TaskPriority.URGENT) {
            return "Р В РЎвЂ”Р РЋР вЂљР В РЎвЂР В РЎвЂўР РЋР вЂљР В РЎвЂР РЋРІР‚С™Р В Р’ВµР РЋРІР‚С™: Р РЋР С“Р РЋР вЂљР В РЎвЂўР РЋРІР‚РЋР В Р вЂ¦Р В РЎвЂў";
        }
        if (priority == TaskPriority.HIGH) {
            return "Р В РЎвЂ”Р РЋР вЂљР В РЎвЂР В РЎвЂўР РЋР вЂљР В РЎвЂР РЋРІР‚С™Р В Р’ВµР РЋРІР‚С™: Р В Р вЂ Р РЋРІР‚в„–Р РЋР С“Р В РЎвЂўР В РЎвЂќР В РЎвЂР В РІвЂћвЂ“";
        }
        if (priority == TaskPriority.MEDIUM) {
            return "Р В РЎвЂ”Р РЋР вЂљР В РЎвЂР В РЎвЂўР РЋР вЂљР В РЎвЂР РЋРІР‚С™Р В Р’ВµР РЋРІР‚С™: Р РЋР С“Р РЋР вЂљР В Р’ВµР В РўвЂР В Р вЂ¦Р В РЎвЂР В РІвЂћвЂ“";
        }
        if (priority == TaskPriority.LOW) {
            return "Р В РЎвЂ”Р РЋР вЂљР В РЎвЂР В РЎвЂўР РЋР вЂљР В РЎвЂР РЋРІР‚С™Р В Р’ВµР РЋРІР‚С™: Р В Р вЂ¦Р В РЎвЂР В Р’В·Р В РЎвЂќР В РЎвЂР В РІвЂћвЂ“";
        }
        return "Р В Р’В±Р В Р’ВµР В Р’В· Р В РЎвЂ”Р РЋР вЂљР В РЎвЂР В РЎвЂўР РЋР вЂљР В РЎвЂР РЋРІР‚С™Р В Р’ВµР РЋРІР‚С™Р В Р’В°";
    }

    private String formatTags(List<String> tags) {
        StringBuilder builder = new StringBuilder("Р РЋРІР‚С™Р В Р’ВµР В РЎвЂ“Р В РЎвЂ:");
        for (String tag : tags) {
            builder.append(" #").append(tag);
        }
        return builder.toString();
    }

    private String formatDuration(Duration duration) {
        long minutes = duration.toMinutes();
        if (minutes % (24 * 60) == 0) {
            long days = minutes / (24 * 60);
            return days + " Р В РўвЂ.";
        }
        if (minutes % 60 == 0) {
            long hours = minutes / 60;
            return hours + " Р РЋРІР‚РЋ.";
        }
        return minutes + " Р В РЎВР В РЎвЂР В Р вЂ¦.";
    }

    private void setNextReminder(ScheduledReminder reminder) {
        if (nextReminderTimeText == null && nextReminderText == null) {
            return;
        }

        if (nextReminderTimeText != null) {
            if (!hasNotificationPermission()) {
                nextReminderTitleText.setText("Р В Р’В Р В Р’В°Р В Р’В·Р РЋР вЂљР В Р’ВµР РЋРІвЂљВ¬Р В Р’ВµР В Р вЂ¦Р В РЎвЂР В Р’Вµ Р В Р вЂ¦Р РЋРЎвЂњР В Р’В¶Р В Р вЂ¦Р В РЎвЂў Р В Р вЂ Р РЋРІР‚в„–Р В РўвЂР В Р’В°Р РЋРІР‚С™Р РЋР Р‰ Р В Р вЂ  Р РЋР С“Р В РЎвЂР РЋР С“Р РЋРІР‚С™Р В Р’ВµР В РЎВР В Р’Вµ");
                nextReminderTimeText.setText("Р В Р в‚¬Р В Р вЂ Р В Р’ВµР В РўвЂР В РЎвЂўР В РЎВР В Р’В»Р В Р’ВµР В Р вЂ¦Р В РЎвЂР РЋР РЏ Р В Р вЂ Р РЋРІР‚в„–Р В РЎвЂќР В Р’В»Р РЋР вЂ№Р РЋРІР‚РЋР В Р’ВµР В Р вЂ¦Р РЋРІР‚в„–");
                nextReminderMetaText.setText("");
                return;
            }

            if (reminder == null) {
                nextReminderTitleText.setText("Р В РЎСљР В Р’ВµР РЋРІР‚С™ Р В Р’В±Р РЋРЎвЂњР В РўвЂР РЋРЎвЂњР РЋРІР‚В°Р В РЎвЂР РЋРІР‚В¦ Р В Р вЂ¦Р В Р’В°Р В РЎвЂ”Р В РЎвЂўР В РЎВР В РЎвЂР В Р вЂ¦Р В Р’В°Р В Р вЂ¦Р В РЎвЂР В РІвЂћвЂ“");
                nextReminderTimeText.setText("");
                nextReminderMetaText.setText("");
                return;
            }

            nextReminderTitleText.setText(reminder.getTitle());
            nextReminderTimeText.setText(DATE_TIME_FORMAT.format(reminder.getTriggerAt()));
            nextReminderMetaText.setText(formatRelativeReminder(reminder.getTriggerAt()));
            return;
        }

        if (!hasNotificationPermission()) {
            nextReminderText.setText("Р В РІР‚ВР В Р’В»Р В РЎвЂР В Р’В¶Р В Р’В°Р В РІвЂћвЂ“Р РЋРІвЂљВ¬Р В Р’ВµР В Р’Вµ Р В Р вЂ¦Р В Р’В°Р В РЎвЂ”Р В РЎвЂўР В РЎВР В РЎвЂР В Р вЂ¦Р В Р’В°Р В Р вЂ¦Р В РЎвЂР В Р’Вµ: Р РЋРЎвЂњР В Р вЂ Р В Р’ВµР В РўвЂР В РЎвЂўР В РЎВР В Р’В»Р В Р’ВµР В Р вЂ¦Р В РЎвЂР РЋР РЏ Р В Р вЂ¦Р В Р’Вµ Р РЋР вЂљР В Р’В°Р В Р’В·Р РЋР вЂљР В Р’ВµР РЋРІвЂљВ¬Р В Р’ВµР В Р вЂ¦Р РЋРІР‚в„–.");
            return;
        }

        if (reminder == null) {
            nextReminderText.setText("Р В РІР‚ВР В Р’В»Р В РЎвЂР В Р’В¶Р В Р’В°Р В РІвЂћвЂ“Р РЋРІвЂљВ¬Р В Р’ВµР В Р’Вµ Р В Р вЂ¦Р В Р’В°Р В РЎвЂ”Р В РЎвЂўР В РЎВР В РЎвЂР В Р вЂ¦Р В Р’В°Р В Р вЂ¦Р В РЎвЂР В Р’Вµ: Р В Р вЂ¦Р В Р’ВµР РЋРІР‚С™ Р В Р’В±Р РЋРЎвЂњР В РўвЂР РЋРЎвЂњР РЋРІР‚В°Р В РЎвЂР РЋРІР‚В¦ Р В Р’В·Р В Р’В°Р В РўвЂР В Р’В°Р РЋРІР‚РЋ Р РЋР С“Р В РЎвЂў Р В Р вЂ Р РЋР вЂљР В Р’ВµР В РЎВР В Р’ВµР В Р вЂ¦Р В Р’ВµР В РЎВ.");
            return;
        }

        nextReminderText.setText(String.format(
                Locale.getDefault(),
                "Р В РІР‚ВР В Р’В»Р В РЎвЂР В Р’В¶Р В Р’В°Р В РІвЂћвЂ“Р РЋРІвЂљВ¬Р В Р’ВµР В Р’Вµ Р В Р вЂ¦Р В Р’В°Р В РЎвЂ”Р В РЎвЂўР В РЎВР В РЎвЂР В Р вЂ¦Р В Р’В°Р В Р вЂ¦Р В РЎвЂР В Р’Вµ: %s Р вЂ™Р’В· %s",
                DATE_TIME_FORMAT.format(reminder.getTriggerAt()),
                reminder.getTitle()
        ));
    }

    private String formatRelativeReminder(LocalDateTime triggerAt) {
        LocalDateTime now = LocalDateTime.now();
        if (triggerAt.toLocalDate().equals(now.toLocalDate())) {
            return "Р РЋР С“Р В Р’ВµР В РЎвЂ“Р В РЎвЂўР В РўвЂР В Р вЂ¦Р РЋР РЏ";
        }
        if (triggerAt.toLocalDate().equals(now.toLocalDate().plusDays(1))) {
            return "Р В Р’В·Р В Р’В°Р В Р вЂ Р РЋРІР‚С™Р РЋР вЂљР В Р’В°";
        }
        return "";
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

    private void requestNotificationPermissionIfNeeded() {
        if (!hasNotificationPermission() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission();
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || hasNotificationPermission()) {
            updateNotificationPermissionUi();
            return;
        }

        requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
    }

    private void requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                || ReminderScheduler.canScheduleExactAlarms(this)) {
            updateExactAlarmPermissionUi();
            return;
        }

        Intent intent = new Intent(
                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                Uri.parse("package:" + getPackageName())
        );
        startActivity(intent);
    }

    private boolean hasNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true;
        }
        return checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void updateNotificationPermissionUi() {
        if (notificationPermissionButton == null) {
            return;
        }
        boolean needsPermissionButton = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && !hasNotificationPermission();
        notificationPermissionButton.setVisibility(needsPermissionButton ? View.VISIBLE : View.GONE);
    }

    private void updateExactAlarmPermissionUi() {
        if (exactAlarmPermissionButton == null) {
            return;
        }
        boolean needsPermissionButton = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && !ReminderScheduler.canScheduleExactAlarms(this);
        exactAlarmPermissionButton.setVisibility(needsPermissionButton ? View.VISIBLE : View.GONE);
    }

    private void updateActiveFilterButton() {
        if (allFilterButton != null) {
            String filter = UserPreferences.getTaskFilter(this);
            styleFilterChip(allFilterButton, UserPreferences.FILTER_ALL.equals(filter));
            styleFilterChip(activeFilterButton, UserPreferences.FILTER_ACTIVE.equals(filter));
            styleFilterChip(overdueFilterButton, UserPreferences.FILTER_OVERDUE.equals(filter));
            styleFilterChip(completedFilterButton, UserPreferences.FILTER_COMPLETED.equals(filter));
            return;
        }

        if (activeFilterButton == null) {
            return;
        }

        activeFilterButton.setText(UserPreferences.isActiveOnly(this)
                ? "Р В Р’В¤Р В РЎвЂР В Р’В»Р РЋР Р‰Р РЋРІР‚С™Р РЋР вЂљ: Р РЋРІР‚С™Р В РЎвЂўР В Р’В»Р РЋР Р‰Р В РЎвЂќР В РЎвЂў Р В Р’В°Р В РЎвЂќР РЋРІР‚С™Р В РЎвЂР В Р вЂ Р В Р вЂ¦Р РЋРІР‚в„–Р В Р’Вµ"
                : "Р В Р’В¤Р В РЎвЂР В Р’В»Р РЋР Р‰Р РЋРІР‚С™Р РЋР вЂљ: Р В Р вЂ Р РЋР С“Р В Р’Вµ Р В Р’В·Р В Р’В°Р В РўвЂР В Р’В°Р РЋРІР‚РЋР В РЎвЂ");
    }

    private void styleFilterChip(Button chip, boolean selected) {
        if (chip == null) {
            return;
        }
        chip.setTextColor(getColor(selected ? R.color.chip_selected_text : R.color.chip_text));
        chip.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
        chip.setBackground(createRoundedBackground(
                getColor(selected ? R.color.chip_selected_background : R.color.chip_background),
                getColor(selected ? R.color.chip_selected_stroke : R.color.chip_stroke),
                8
        ));
    }

    private LinearLayout.LayoutParams fullWidth() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    private LinearLayout.LayoutParams fullWidthWithBottomMargin() {
        LinearLayout.LayoutParams params = fullWidth();
        params.setMargins(0, 0, 0, dp(12));
        return params;
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

    private String capitalize(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return text.substring(0, 1).toUpperCase(Locale.getDefault()) + text.substring(1);
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

    private GradientDrawable createTopRoundedBackground(int color, int strokeColor, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        float radius = dp(radiusDp);
        drawable.setCornerRadii(new float[]{
                radius, radius,
                radius, radius,
                0f, 0f,
                0f, 0f
        });
        if (strokeColor != 0) {
            drawable.setStroke(dp(1), strokeColor);
        }
        return drawable;
    }

    private GradientDrawable createRightRoundedBackground(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        float radius = dp(radiusDp);
        drawable.setCornerRadii(new float[]{
                0f, 0f,
                radius, radius,
                radius, radius,
                0f, 0f
        });
        return drawable;
    }

    private GradientDrawable createCircleBackground(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color);
        return drawable;
    }

    private GradientDrawable createCircleOutlineBackground(int color, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color);
        drawable.setStroke(dp(2), strokeColor);
        return drawable;
    }

    private String compactSourceName(TaskParseResult parseResult) {
        if (parseResult != null && !parseResult.getTasks().isEmpty()) {
            return compactName(parseResult.getTasks().get(0).getSourceName());
        }
        return compactName(noteUri == null ? "" : noteUri.toString());
    }

    private String compactName(String rawName) {
        if (rawName == null || rawName.trim().isEmpty()) {
            return "Р В РІР‚ВР В Р’ВµР В Р’В· Р В РЎвЂР В РЎВР В Р’ВµР В Р вЂ¦Р В РЎвЂ Р РЋРІР‚С›Р В Р’В°Р В РІвЂћвЂ“Р В Р’В»Р В Р’В°";
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

    private String formatSecondaryTaskMeta(ObsidianTask task) {
        StringBuilder builder = new StringBuilder();
        if (task.getPriority() != TaskPriority.NONE) {
            builder.append(formatPriority(task.getPriority()));
        }
        if (!task.getTags().isEmpty()) {
            if (builder.length() > 0) {
                builder.append(" Р вЂ™Р’В· ");
            }
            builder.append(formatTags(task.getTags()));
        }
        return builder.toString();
    }

    private void setStatus(String message) {
        if (sourceTitleText != null) {
            sourceTitleText.setText("Р В Р’ВР РЋР С“Р РЋРІР‚С™Р В РЎвЂўР РЋРІР‚РЋР В Р вЂ¦Р В РЎвЂР В РЎвЂќ");
            sourceMetaText.setText(message == null ? "" : message);
            sourceStatsText.setText("");
            if (sourceStatsRow != null) {
                sourceStatsRow.removeAllViews();
            }
            sourceErrorText.setText("");
            sourceErrorText.setVisibility(View.GONE);
            return;
        }
        if (statusText != null) {
            statusText.setText(message);
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}

