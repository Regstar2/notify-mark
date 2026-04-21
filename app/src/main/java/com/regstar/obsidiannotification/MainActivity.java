package com.regstar.obsidiannotification;

import android.Manifest;
import android.app.Activity;
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
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public final class MainActivity extends Activity {
    private static final int REQUEST_OPEN_NOTE = 1001;
    private static final int REQUEST_NOTIFICATIONS = 1002;
    private static final int REQUEST_EDIT_TASK = 1003;
    private static final int REQUEST_SOURCE_MANAGEMENT = 1004;
    private static final int REQUEST_ONBOARDING = 1005;
    private static final long FOREGROUND_REFRESH_INTERVAL_MS = 15_000L;
    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private Uri noteUri;
    private TextView statusText;
    private TextView nextReminderText;
    private TextView sourceTitleText;
    private TextView sourceMetaText;
    private TextView sourceStatsText;
    private TextView sourceErrorText;
    private LinearLayout sourceStatsRow;
    private LinearLayout groupFilterRow;
    private TextView nextReminderTimeText;
    private TextView nextReminderTitleText;
    private TextView nextReminderMetaText;
    private TextView taskSectionTitleText;
    private TextView taskSectionCountText;
    private View refreshButton;
    private Button activeFilterButton;
    private Button allFilterButton;
    private Button overdueFilterButton;
    private Button completedFilterButton;
    private Button notificationPermissionButton;
    private Button exactAlarmPermissionButton;
    private LinearLayout taskList;
    private boolean showTaskSourceNames;
    private boolean renderedShowSourceOnMain;
    private boolean renderedShowNextReminder;
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

        noteUri = NoteStore.getSavedSourceUri(this);

        buildUi();
        updateNotificationPermissionUi();
        updateExactAlarmPermissionUi();
        requestNotificationPermissionIfNeeded();

        if (noteUri == null) {
            NoteChangeMonitor.cancel(this);
            ReminderScheduler.cancelScheduled(this);
            setStatus("Выберите markdown-файл или папку с задачами Obsidian.");
            setNextReminder(null);
            renderEmptyState("Задачи появятся здесь после выбора заметки.");
            showOnboardingIfNeeded();
        } else {
            readAndRenderNote();
            NoteChangeMonitor.ensureScheduled(this);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (renderedShowSourceOnMain != UserPreferences.shouldShowSourceOnMain(this)
                || renderedShowNextReminder != UserPreferences.shouldShowNextReminder(this)) {
            buildUi();
            updateNotificationPermissionUi();
            updateExactAlarmPermissionUi();
        }
        if (exactAlarmPermissionButton != null) {
            updateExactAlarmPermissionUi();
        }
        noteUri = NoteStore.getSavedSourceUri(this);
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
            noteUri = NoteStore.getSavedSourceUri(this);
            if (noteUri == null) {
                NoteChangeMonitor.cancel(this);
                ReminderScheduler.cancelScheduled(this);
                setStatus("Источник не выбран.");
                setNextReminder(null);
                renderEmptyState("Задачи появятся здесь после выбора источника.");
            } else {
                readAndRenderNote();
                NoteChangeMonitor.ensureScheduled(this);
            }
            return;
        }

        if (requestCode == REQUEST_ONBOARDING) {
            noteUri = NoteStore.getSavedSourceUri(this);
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
                setStatus("Уведомления разрешены. Выберите markdown-файл с задачами Obsidian.");
            } else {
                readAndRenderNote();
                NoteChangeMonitor.ensureScheduled(this);
            }
        } else {
            ReminderScheduler.cancelScheduled(this);
            setStatus("Разрешение на уведомления не выдано. Напоминания не будут показаны.");
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
        title.setText("ObsidianNotification");
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
        settingsButton.setContentDescription("Настройки");
        settingsButton.setBackgroundColor(Color.TRANSPARENT);
        settingsButton.setOnClickListener(view -> openSettings());
        header.addView(settingsButton, new LinearLayout.LayoutParams(dp(48), dp(48)));

        root.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView subtitle = new TextView(this);
        subtitle.setText("Читает markdown-уведомления из Obsidian и планирует локальные напоминания.");
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
        addTaskButton.setText("Добавить уведомление");
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
        notificationPermissionButton.setText("Разрешить уведомления");
        notificationPermissionButton.setAllCaps(false);
        notificationPermissionButton.setOnClickListener(view -> requestNotificationPermission());
        LinearLayout.LayoutParams permissionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        permissionParams.setMargins(0, 0, 0, dp(12));
        root.addView(notificationPermissionButton, permissionParams);

        exactAlarmPermissionButton = new Button(this);
        exactAlarmPermissionButton.setText("Разрешить точные напоминания");
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
        renderedShowNextReminder = UserPreferences.shouldShowNextReminder(this);
        nextReminderTimeText = null;
        nextReminderTitleText = null;
        nextReminderMetaText = null;
        nextReminderText = null;

        boolean showNextReminder = UserPreferences.shouldShowNextReminder(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(14), dp(16), showNextReminder ? dp(230) : dp(96));
        root.setBackgroundColor(getColor(R.color.background));

        LinearLayout appBar = new LinearLayout(this);
        appBar.setOrientation(LinearLayout.HORIZONTAL);
        appBar.setGravity(android.view.Gravity.CENTER_VERTICAL);
        appBar.setPadding(0, 0, 0, dp(10));

        TextView title = createText("ObsidianNotification", 21, R.color.text_primary, true);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        appBar.addView(title, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        ));

        TextView overflowButton = createOverflowButton("Главное меню");
        overflowButton.setOnClickListener(view -> showMainMenu(overflowButton));
        refreshButton = overflowButton;
        appBar.addView(overflowButton, new LinearLayout.LayoutParams(dp(40), dp(40)));
        root.addView(appBar, fullWidth());

        LinearLayout sourceCard = createSourceCard();
        if (noteUri == null || UserPreferences.shouldShowSourceOnMain(this)) {
            LinearLayout.LayoutParams sourceParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            sourceParams.setMargins(0, 0, 0, dp(12));
            root.addView(sourceCard, sourceParams);
        }

        HorizontalScrollView groupScroll = new HorizontalScrollView(this);
        groupScroll.setHorizontalScrollBarEnabled(false);
        groupScroll.setVerticalScrollBarEnabled(false);
        groupScroll.setPadding(0, 0, 0, dp(10));
        groupFilterRow = new LinearLayout(this);
        groupFilterRow.setOrientation(LinearLayout.HORIZONTAL);
        groupScroll.addView(groupFilterRow, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        root.addView(groupScroll, fullWidth());

        notificationPermissionButton = createActionButton("Разрешить уведомления", true);
        notificationPermissionButton.setOnClickListener(view -> requestNotificationPermission());
        LinearLayout.LayoutParams permissionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(44)
        );
        permissionParams.setMargins(0, 0, 0, dp(10));
        root.addView(notificationPermissionButton, permissionParams);

        exactAlarmPermissionButton = createActionButton("Разрешить точные напоминания", false);
        exactAlarmPermissionButton.setOnClickListener(view -> requestExactAlarmPermission());
        LinearLayout.LayoutParams exactAlarmParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(44)
        );
        exactAlarmParams.setMargins(0, 0, 0, dp(10));
        root.addView(exactAlarmPermissionButton, exactAlarmParams);

        root.addView(createTaskSectionHeader(), fullWidthWithBottomMargin());

        taskList = new LinearLayout(this);
        taskList.setOrientation(LinearLayout.VERTICAL);
        taskList.setPadding(0, 0, 0, 0);
        taskList.setClipToPadding(false);
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

        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(getColor(R.color.background));
        frame.addView(screenScroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        LinearLayout bottomOverlay = createBottomOverlay(showNextReminder);
        FrameLayout.LayoutParams bottomParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.BOTTOM
        );
        frame.addView(bottomOverlay, bottomParams);

        setContentView(frame);
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

    private TextView createOverflowButton(String description) {
        TextView button = new TextView(this);
        button.setText("⋮");
        button.setTextSize(22);
        button.setTextColor(getColor(R.color.text_primary));
        button.setGravity(android.view.Gravity.CENTER);
        button.setContentDescription(description);
        button.setBackground(createRoundedBackground(
                getColor(R.color.icon_button_background),
                0,
                18
        ));
        return button;
    }

    private void showMainMenu(View anchor) {
        PopupMenu popupMenu = new PopupMenu(this, anchor);
        popupMenu.getMenu().add(0, 1, 0, "Обновить");
        popupMenu.getMenu().add(0, 2, 1, "Источники");
        popupMenu.getMenu().add(0, 3, 2, "Настройки");
        popupMenu.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == 1) {
                readAndRenderNote();
                return true;
            }
            if (id == 2) {
                openSourceManagement();
                return true;
            }
            if (id == 3) {
                openSettings();
                return true;
            }
            return false;
        });
        popupMenu.show();
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

    private LinearLayout createBottomOverlay(boolean showNextReminder) {
        LinearLayout overlay = new LinearLayout(this);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setPadding(dp(16), 0, dp(16), dp(16));

        Button addFab = createFabButton();
        addFab.setOnClickListener(view -> openTaskEditor(null));
        LinearLayout.LayoutParams fabParams = new LinearLayout.LayoutParams(dp(48), dp(48));
        fabParams.gravity = android.view.Gravity.RIGHT;
        fabParams.setMargins(0, 0, 0, showNextReminder ? dp(10) : 0);
        overlay.addView(addFab, fabParams);

        if (showNextReminder) {
            LinearLayout nextReminderCard = createNextReminderCard();
            overlay.addView(nextReminderCard, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));
        }

        return overlay;
    }

    private LinearLayout createNextReminderCard() {
        LinearLayout card = createCardContainer();
        card.addView(createText("Ближайшее напоминание", 13, R.color.text_secondary, false), fullWidth());
        nextReminderTitleText = createText("Нет будущих напоминаний", 16, R.color.text_primary, true);
        nextReminderTimeText = createText("", 15, R.color.text_primary, false);
        nextReminderMetaText = createText("", 12, R.color.text_secondary, false);
        nextReminderText = nextReminderTitleText;
        card.addView(nextReminderTitleText, fullWidthWithTopMargin(dp(6)));
        card.addView(nextReminderTimeText, fullWidthWithTopMargin(dp(3)));
        card.addView(nextReminderMetaText, fullWidthWithTopMargin(dp(3)));
        return card;
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
        sourceTitleText = createText("Источник", 15, R.color.text_primary, true);
        sourceMetaText = createText("Источник не выбран", 13, R.color.text_secondary, false);
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

        ImageButton manageButton = createPlainIconButton(R.drawable.ic_settings, "Управлять источниками");
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

    private LinearLayout createTaskSectionHeader() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(2), 0, 0);

        LinearLayout titleColumn = new LinearLayout(this);
        titleColumn.setOrientation(LinearLayout.VERTICAL);
        taskSectionTitleText = createText("Задачи", 18, R.color.text_primary, true);
        taskSectionCountText = createText("", 13, R.color.text_secondary, false);
        titleColumn.addView(taskSectionTitleText, fullWidth());
        titleColumn.addView(taskSectionCountText, fullWidthWithTopMargin(dp(2)));
        row.addView(titleColumn, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        ));

        ImageButton filterButton = createPlainIconButton(R.drawable.ic_filter_list, "Фильтр задач");
        filterButton.setOnClickListener(view -> showTaskFilterMenu(filterButton));
        row.addView(filterButton, new LinearLayout.LayoutParams(dp(40), dp(40)));
        return row;
    }

    private void showTaskFilterMenu(View anchor) {
        PopupMenu popupMenu = new PopupMenu(this, anchor);
        popupMenu.getMenu().add(0, 1, 0, "Все");
        popupMenu.getMenu().add(0, 2, 1, "Активные");
        popupMenu.getMenu().add(0, 3, 2, "Просроченные");
        popupMenu.getMenu().add(0, 4, 3, "Завершенные");
        popupMenu.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == 1) {
                UserPreferences.setTaskFilter(this, UserPreferences.FILTER_ALL);
            } else if (id == 2) {
                UserPreferences.setTaskFilter(this, UserPreferences.FILTER_ACTIVE);
            } else if (id == 3) {
                UserPreferences.setTaskFilter(this, UserPreferences.FILTER_OVERDUE);
            } else if (id == 4) {
                UserPreferences.setTaskFilter(this, UserPreferences.FILTER_COMPLETED);
            }
            updateActiveFilterButton();
            readAndRenderNote();
            return true;
        });
        popupMenu.show();
    }

    private void updateTaskSectionHeader(int visibleCount) {
        if (taskSectionTitleText == null || taskSectionCountText == null) {
            return;
        }
        String filterLabel = UserPreferences.getTaskFilterLabel(this);
        taskSectionTitleText.setText(UserPreferences.FILTER_ALL.equals(UserPreferences.getTaskFilter(this))
                ? "Задачи"
                : "Задачи · " + capitalize(filterLabel));
        taskSectionCountText.setText(visibleCount + " в списке");
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
        if (!OnboardingPreferences.shouldShow(this) || NoteStore.hasSavedSources(this)) {
            return;
        }
        startActivityForResult(new Intent(this, OnboardingActivity.class), REQUEST_ONBOARDING);
    }

    private void readAndRenderNote() {
        if (noteUri == null) {
            NoteChangeMonitor.cancel(this);
            ReminderScheduler.cancelScheduled(this);
            setStatus("Файл не выбран.");
            setNextReminder(null);
            renderEmptyState("Нажмите «Выбрать заметку».");
            return;
        }

        if (refreshButton != null) {
            refreshButton.setEnabled(true);
        }

        try {
            NoteStore.TaskSnapshot snapshot = NoteStore.readTaskSnapshot(this);
            if (NoteChangeMonitor.isSuspiciousPartialRead(this, snapshot)) {
                restoreAndRenderCachedTasks("Файл выглядит частично синхронизированным.");
                return;
            }

            renderParseResult(snapshot);
            NoteChangeMonitor.ensureScheduled(this);
        } catch (IOException | RuntimeException exception) {
            ErrorLog.record(this, "Не удалось прочитать источник в интерфейсе", exception);
            NoteChangeMonitor.restoreFromCache(this, exception.getMessage());
            NoteChangeMonitor.ensureScheduled(this);
            setStatus("Файл временно недоступен: " + exception.getMessage()
                    + "\nТекущие уведомления сохранены.");
        }
    }

    private void renderParseResult(NoteStore.TaskSnapshot snapshot) {
        TaskParseResult parseResult = snapshot.getParseResult();
        List<ObsidianTask> activeTasks = parseResult.getActiveTasks();
        TaskCache.saveActiveTasks(this, activeTasks);
        ReminderSchedule schedule = ReminderScheduler.schedule(this, activeTasks);
        renderedFingerprint = NoteChangeMonitor.fingerprintOf(parseResult);
        NoteChangeMonitor.recordSuccessfulSync(this, renderedFingerprint);

        renderTasks(parseResult.getTasks());
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
                restoreAndRenderCachedTasks("Файл выглядит частично синхронизированным.");
                return;
            }

            TaskParseResult parseResult = snapshot.getParseResult();
            String fingerprint = NoteChangeMonitor.fingerprintOf(parseResult);
            if (!fingerprint.equals(renderedFingerprint)) {
                renderParseResult(snapshot);
            }
        } catch (IOException | RuntimeException exception) {
            ErrorLog.record(this, "Не удалось обновить источник в интерфейсе", exception);
            NoteChangeMonitor.restoreFromCache(this, exception.getMessage());
            setStatus("Файл временно недоступен: " + exception.getMessage()
                    + "\nТекущие уведомления сохранены.");
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
                ErrorLog.record(this, "Не удалось показать ближайшее напоминание из кэша", exception);
            }
            renderTasks(cachedTasks);
            setNextReminder(schedule == null ? null : schedule.getNextReminder());
            setStatus(reason
                    + "\nИспользуется локальный кэш задач: " + cachedTasks.size()
                    + "\nУведомления " + (restored ? "восстановлены." : "оставлены без изменений."));
            return;
        }

        setStatus(reason + "\nЛокальный кэш задач пуст.");
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
                "%d файл. · обновлено %s",
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

        StringBuilder errors = new StringBuilder("Ошибки разбора: ");
        int limit = Math.min(2, parseResult.getErrors().size());
        for (int i = 0; i < limit; i++) {
            if (i > 0) {
                errors.append("\n");
            }
            errors.append(parseResult.getErrors().get(i).format());
        }
        if (parseResult.getErrors().size() > limit) {
            errors.append("\nЕще ошибок: ").append(parseResult.getErrors().size() - limit);
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
                ? "уведомления разрешены"
                : "нет разрешения на уведомления";
        String exactAlarmStatus = ReminderScheduler.canScheduleExactAlarms(this)
                ? "точные напоминания разрешены"
                : "точные напоминания не разрешены, используется неточный fallback";
        String status = String.format(
                Locale.getDefault(),
                "Источник: %s\nФайлов прочитано: %d\nВсего задач: %d\nАктивных задач: %d\nЗапланировано уведомлений: %d\n%s\n%s\nОбновлено: %s",
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
            builder.append("\nОшибки разбора:");
            int limit = Math.min(3, parseResult.getErrors().size());
            for (int i = 0; i < limit; i++) {
                builder.append("\n").append(parseResult.getErrors().get(i).format());
            }
            if (parseResult.getErrors().size() > limit) {
                builder.append("\nЕще ошибок: ").append(parseResult.getErrors().size() - limit);
            }
            status = builder.toString();
        }

        return status;
    }

    private void renderTasks(List<ObsidianTask> tasks) {
        taskList.removeAllViews();
        updateGroupFilterRow(tasks);
        showTaskSourceNames = hasMultipleSources(tasks);
        List<ObsidianTask> visibleTasks = filterVisibleTasks(tasks);
        updateTaskSectionHeader(visibleTasks.size());
        if (visibleTasks.isEmpty()) {
            renderEmptyState("В выбранных markdown-файлах нет уведомлений с @due(...) для текущего фильтра.");
            return;
        }

        String currentSource = null;
        boolean showGroupHeaders = showTaskSourceNames;
        for (ObsidianTask task : visibleTasks) {
            String sourceName = task.getSourceName();
            if (showGroupHeaders && !sourceName.equals(currentSource)) {
                currentSource = sourceName;
                taskList.addView(createSourceHeader(sourceName));
            }
            taskList.addView(createTaskView(task));
        }
    }

    private List<ObsidianTask> filterVisibleTasks(List<ObsidianTask> tasks) {
        String filter = UserPreferences.getTaskFilter(this);
        String selectedGroup = UserPreferences.getTaskGroup(this);
        ArrayList<ObsidianTask> visibleTasks = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (ObsidianTask task : tasks) {
            if (selectedGroup != null
                    && !selectedGroup.isEmpty()
                    && !selectedGroup.equals(taskGroupLabel(task))) {
                continue;
            }
            if (UserPreferences.FILTER_ALL.equals(filter)) {
                visibleTasks.add(task);
                continue;
            }
            TaskStatus status = task.getStatus(now);
            if (UserPreferences.FILTER_ACTIVE.equals(filter) && status != TaskStatus.COMPLETED) {
                visibleTasks.add(task);
            } else if (UserPreferences.FILTER_OVERDUE.equals(filter) && status == TaskStatus.OVERDUE) {
                visibleTasks.add(task);
            } else if (UserPreferences.FILTER_COMPLETED.equals(filter) && status == TaskStatus.COMPLETED) {
                visibleTasks.add(task);
            }
        }
        return visibleTasks;
    }

    private void updateGroupFilterRow(List<ObsidianTask> tasks) {
        if (groupFilterRow == null) {
            return;
        }

        Set<String> groups = new LinkedHashSet<>();
        for (ObsidianTask task : tasks) {
            groups.add(taskGroupLabel(task));
        }

        groupFilterRow.removeAllViews();
        if (groups.size() <= 1) {
            UserPreferences.setTaskGroup(this, "");
            return;
        }
        String selectedGroup = UserPreferences.getTaskGroup(this);
        if (selectedGroup != null && !selectedGroup.isEmpty() && !groups.contains(selectedGroup)) {
            UserPreferences.setTaskGroup(this, "");
        }

        addGroupChip(groupFilterRow, "Все группы", "", 0);
        for (String group : groups) {
            addGroupChip(groupFilterRow, group, group, dp(6));
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
        chip.setOnClickListener(view -> {
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
        String mode = UserPreferences.getGroupingMode(this);
        if (UserPreferences.GROUPING_GROUP.equals(mode)) {
            return emptyFallback(task.getGroup(), ObsidianTask.DEFAULT_GROUP);
        }
        if (UserPreferences.GROUPING_TAG.equals(mode)) {
            return task.getTags().isEmpty() ? "без тегов" : task.getTags().get(0);
        }
        if (UserPreferences.GROUPING_FILE.equals(mode)) {
            return compactName(task.getSourceName());
        }

        String group = task.getGroup();
        if (group != null && !group.trim().isEmpty() && !ObsidianTask.DEFAULT_GROUP.equals(group)) {
            return group;
        }
        if (!task.getTags().isEmpty()) {
            return task.getTags().get(0);
        }
        return compactName(task.getSourceName());
    }

    private String formatGroupMeta(ObsidianTask task) {
        String mode = UserPreferences.getGroupingMode(this);
        if (UserPreferences.GROUPING_TAG.equals(mode)) {
            return "тег: " + taskGroupLabel(task);
        }
        if (UserPreferences.GROUPING_FILE.equals(mode)) {
            return "файл: " + taskGroupLabel(task);
        }
        if (UserPreferences.GROUPING_SMART.equals(mode)) {
            return "контекст: " + taskGroupLabel(task);
        }
        return "группа: " + taskGroupLabel(task);
    }

    private String emptyFallback(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private View createSourceHeader(String sourceName) {
        TextView header = new TextView(this);
        header.setText(sourceName == null || sourceName.isEmpty() ? "Без имени файла" : sourceName);
        header.setTextColor(getColor(R.color.text_primary));
        header.setText(sourceName == null || sourceName.isEmpty()
                ? "Без имени файла"
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

        Button menuButton = new Button(this);
        menuButton.setText("⋮");
        menuButton.setAllCaps(false);
        menuButton.setOnClickListener(view -> showTaskMenu(menuButton, task));
        titleRow.addView(menuButton, new LinearLayout.LayoutParams(dp(48), dp(42)));

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
        LinearLayout item = createCardContainer();
        item.setOnClickListener(view -> openPreferredTaskEditor(task));
        item.setOnTouchListener(new View.OnTouchListener() {
            private float downX;
            private float downY;

            @Override
            public boolean onTouch(View view, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    downX = event.getX();
                    downY = event.getY();
                    return false;
                }
                if (event.getAction() != MotionEvent.ACTION_UP) {
                    return false;
                }

                float dx = event.getX() - downX;
                float dy = event.getY() - downY;
                if (Math.abs(dx) < dp(86) || Math.abs(dx) < Math.abs(dy) * 1.4f) {
                    return false;
                }

                if (dx > 0) {
                    snoozeTask(task);
                } else {
                    confirmDeleteTask(task);
                }
                return true;
            }
        });

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

        TextView statusChip = createStatusChip(task.getStatus(LocalDateTime.now()));
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(22)
        );
        statusParams.setMargins(dp(8), 0, dp(4), 0);
        titleRow.addView(statusChip, statusParams);

        TextView menuButton = new TextView(this);
        menuButton.setText("⋮");
        menuButton.setTextSize(24);
        menuButton.setTextColor(getColor(R.color.text_secondary));
        menuButton.setGravity(android.view.Gravity.CENTER);
        menuButton.setOnClickListener(view -> showTaskMenu(menuButton, task));
        titleRow.addView(menuButton, new LinearLayout.LayoutParams(dp(36), dp(36)));

        item.addView(titleRow, fullWidth());
        item.addView(createMetaLine(R.drawable.ic_clock, task.getReminderAt() == null
                ? "не указано"
                : DATE_TIME_FORMAT.format(task.getReminderAt())), fullWidthWithTopMargin(dp(10)));

        if (task.getRepeatInterval() != null) {
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

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, dp(10));
        item.setLayoutParams(params);
        return item;
    }

    private TextView createCompletionButton(ObsidianTask task) {
        TextView button = createText(task.isCompleted() ? "✓" : "", 16, R.color.accent, true);
        button.setGravity(android.view.Gravity.CENTER);
        button.setContentDescription(task.isCompleted() ? "Задача выполнена" : "Выполнить");
        button.setBackground(createCircleOutlineBackground(
                task.isCompleted()
                        ? getColor(R.color.status_completed_background)
                        : Color.TRANSPARENT,
                task.isCompleted()
                        ? getColor(R.color.status_completed_text)
                        : getColor(R.color.text_secondary)
        ));
        if (!task.isCompleted()) {
            button.setOnClickListener(view -> markTaskDone(task));
        }
        return button;
    }

    private TextView createStatusChip(TaskStatus status) {
        int background;
        int textColor;
        String label;
        if (status == TaskStatus.COMPLETED) {
            background = R.color.status_completed_background;
            textColor = R.color.status_completed_text;
            label = "Завершена";
        } else if (status == TaskStatus.OVERDUE) {
            background = R.color.status_overdue_background;
            textColor = R.color.status_overdue_text;
            label = "Просрочена";
        } else {
            background = R.color.status_waiting_background;
            textColor = R.color.status_waiting_text;
            label = "Ожидает";
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

    private void showTaskMenu(View anchor, ObsidianTask task) {
        PopupMenu popupMenu = new PopupMenu(this, anchor);
        popupMenu.getMenu().add(0, 1, 0, "Выполнить");
        popupMenu.getMenu().add(0, 2, 1, "Отложить");
        popupMenu.getMenu().add(0, 3, 2, "Редактировать через UI");
        popupMenu.getMenu().add(0, 4, 3, "Открыть markdown");
        popupMenu.getMenu().add(0, 5, 4, "Удалить");
        popupMenu.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == 1) {
                markTaskDone(task);
                return true;
            }
            if (id == 2) {
                snoozeTask(task);
                return true;
            }
            if (id == 3) {
                openTaskEditor(task);
                return true;
            }
            if (id == 4) {
                openMarkdownEditor(task);
                return true;
            }
            if (id == 5) {
                confirmDeleteTask(task);
                return true;
            }
            return false;
        });
        popupMenu.show();
    }

    @SuppressWarnings("deprecation")
    private void openTaskEditor(ObsidianTask task) {
        Intent intent = new Intent(this, TaskEditActivity.class);
        if (task != null) {
            intent.putExtra(TaskEditActivity.EXTRA_TASK_KEY, task.getTaskKey());
        }
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

    private void markTaskDone(ObsidianTask task) {
        TaskEditResult result = NoteStore.markTaskDone(this, task.getTaskKey());
        if (result.shouldStopReminder()) {
            ReminderScheduler.cancelReminder(this, task.getTaskKey());
            NoteChangeMonitor.syncNow(this, true);
            readAndRenderNote();
        }
        Toast.makeText(this, result.getMessage(), Toast.LENGTH_LONG).show();
    }

    private void snoozeTask(ObsidianTask task) {
        ReminderScheduler.scheduleSnooze(
                this,
                task.getTaskKey(),
                notificationIdFor(task),
                task.getLineNumber(),
                task.getTitle(),
                Duration.ofMinutes(ActionPreferences.getSnoozeMinutes(this)),
                task.getRepeatIntervalMillis(),
                task.getRepeatMode()
        );
        if (ActionPreferences.shouldRecordSnoozeCount(this)) {
            NoteStore.incrementSnoozeCount(this, task.getTaskKey());
        }
        Toast.makeText(this, "Уведомление отложено", Toast.LENGTH_SHORT).show();
        readAndRenderNote();
    }

    private void confirmDeleteTask(ObsidianTask task) {
        new AlertDialog.Builder(this)
                .setTitle("Удалить уведомление?")
                .setMessage(task.getTitle())
                .setPositiveButton("Удалить", (dialog, which) -> deleteTask(task))
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void deleteTask(ObsidianTask task) {
        TaskEditResult result = NoteStore.deleteTaskLine(this, task.getTaskKey());
        if (result.isUpdated()) {
            ReminderScheduler.cancelReminder(this, task.getTaskKey());
            NoteChangeMonitor.syncNow(this, true);
            readAndRenderNote();
        }
        Toast.makeText(this, result.getMessage(), Toast.LENGTH_LONG).show();
    }

    private void openNoteForTask(ObsidianTask task) {
        Uri uri = null;
        try {
            NoteStore.TaskDocumentMatch match = NoteStore.findTaskDocument(this, task.getTaskKey());
            if (match != null) {
                uri = match.getUri();
            }
        } catch (IOException | RuntimeException exception) {
            ErrorLog.record(this, "Не удалось найти заметку для открытия", exception);
        }

        if (uri == null) {
            uri = NoteStore.getSavedSourceUri(this);
        }
        if (uri == null) {
            Toast.makeText(this, "Источник не выбран", Toast.LENGTH_LONG).show();
            return;
        }
        if ("file".equalsIgnoreCase(uri.getScheme())) {
            Toast.makeText(this, "Прямое открытие доступно для файлов, выбранных через Android picker", Toast.LENGTH_LONG).show();
            return;
        }

        Intent openNoteIntent = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "text/markdown")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        try {
            startActivity(openNoteIntent);
        } catch (RuntimeException exception) {
            ErrorLog.record(this, "Не удалось открыть заметку напрямую", exception);
            Toast.makeText(this, "Не удалось открыть заметку напрямую", Toast.LENGTH_LONG).show();
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
        builder.append(formatStatus(task.getStatus(LocalDateTime.now())));
        if (!task.getSourceName().isEmpty()) {
            builder.append(" · ").append(task.getSourceName());
        }
        builder.append(" · строка ").append(task.getLineNumber());

        if (task.getReminderAt() != null) {
            builder.append(" · напомнить ").append(DATE_TIME_FORMAT.format(task.getReminderAt()));
        } else {
            builder.append(" · время не указано");
        }

        if (task.getRepeatInterval() != null) {
            builder.append(" · ").append(formatRepeat(task));
        }

        if (task.getPriority() != TaskPriority.NONE) {
            builder.append(" · ").append(formatPriority(task.getPriority()));
        }

        if (!task.getTags().isEmpty()) {
            builder.append(" · ").append(formatTags(task.getTags()));
        }

        return builder.toString();
    }

    private String formatStatus(TaskStatus status) {
        if (status == TaskStatus.COMPLETED) {
            return "завершена";
        }
        if (status == TaskStatus.OVERDUE) {
            return "просрочена";
        }
        return "ожидает";
    }

    private String formatRepeat(ObsidianTask task) {
        if (task.getRepeatMode() == RepeatMode.UNTIL_DONE) {
            return "повтор до выполнения " + formatDuration(task.getRepeatInterval());
        }

        return "повтор " + formatDuration(task.getRepeatInterval());
    }

    private String formatPriority(TaskPriority priority) {
        if (priority == TaskPriority.URGENT) {
            return "приоритет: срочно";
        }
        if (priority == TaskPriority.HIGH) {
            return "приоритет: высокий";
        }
        if (priority == TaskPriority.MEDIUM) {
            return "приоритет: средний";
        }
        if (priority == TaskPriority.LOW) {
            return "приоритет: низкий";
        }
        return "без приоритета";
    }

    private String formatTags(List<String> tags) {
        StringBuilder builder = new StringBuilder("теги:");
        for (String tag : tags) {
            builder.append(" #").append(tag);
        }
        return builder.toString();
    }

    private String formatDuration(Duration duration) {
        long minutes = duration.toMinutes();
        if (minutes % (24 * 60) == 0) {
            long days = minutes / (24 * 60);
            return days + " д.";
        }
        if (minutes % 60 == 0) {
            long hours = minutes / 60;
            return hours + " ч.";
        }
        return minutes + " мин.";
    }

    private void setNextReminder(ScheduledReminder reminder) {
        if (nextReminderTimeText == null && nextReminderText == null) {
            return;
        }

        if (nextReminderTimeText != null) {
            if (!hasNotificationPermission()) {
                nextReminderTitleText.setText("Разрешение нужно выдать в системе");
                nextReminderTimeText.setText("Уведомления выключены");
                nextReminderMetaText.setText("");
                return;
            }

            if (reminder == null) {
                nextReminderTitleText.setText("Нет будущих напоминаний");
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
            nextReminderText.setText("Ближайшее напоминание: уведомления не разрешены.");
            return;
        }

        if (reminder == null) {
            nextReminderText.setText("Ближайшее напоминание: нет будущих задач со временем.");
            return;
        }

        nextReminderText.setText(String.format(
                Locale.getDefault(),
                "Ближайшее напоминание: %s · %s",
                DATE_TIME_FORMAT.format(reminder.getTriggerAt()),
                reminder.getTitle()
        ));
    }

    private String formatRelativeReminder(LocalDateTime triggerAt) {
        LocalDateTime now = LocalDateTime.now();
        if (triggerAt.toLocalDate().equals(now.toLocalDate())) {
            return "сегодня";
        }
        if (triggerAt.toLocalDate().equals(now.toLocalDate().plusDays(1))) {
            return "завтра";
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
        boolean needsPermissionButton = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && !hasNotificationPermission();
        notificationPermissionButton.setVisibility(needsPermissionButton ? View.VISIBLE : View.GONE);
    }

    private void updateExactAlarmPermissionUi() {
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
                ? "Фильтр: только активные"
                : "Фильтр: все задачи");
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
            return "Без имени файла";
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
                builder.append(" · ");
            }
            builder.append(formatTags(task.getTags()));
        }
        if (builder.length() > 0) {
            builder.append(" · ");
        }
        builder.append("строка ").append(task.getLineNumber());
        return builder.toString();
    }

    private void setStatus(String message) {
        if (sourceTitleText != null) {
            sourceTitleText.setText("Источник");
            sourceMetaText.setText(message == null ? "" : message);
            sourceStatsText.setText("");
            if (sourceStatsRow != null) {
                sourceStatsRow.removeAllViews();
            }
            sourceErrorText.setText("");
            sourceErrorText.setVisibility(View.GONE);
            return;
        }
        statusText.setText(message);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
