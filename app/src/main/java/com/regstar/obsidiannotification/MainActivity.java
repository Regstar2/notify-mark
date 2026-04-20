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
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class MainActivity extends Activity {
    private static final int REQUEST_OPEN_NOTE = 1001;
    private static final int REQUEST_NOTIFICATIONS = 1002;
    private static final int REQUEST_EDIT_TASK = 1003;
    private static final long FOREGROUND_REFRESH_INTERVAL_MS = 15_000L;
    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private Uri noteUri;
    private TextView statusText;
    private TextView nextReminderText;
    private Button refreshButton;
    private Button activeFilterButton;
    private Button notificationPermissionButton;
    private Button exactAlarmPermissionButton;
    private LinearLayout taskList;
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
        } else {
            readAndRenderNote();
            NoteChangeMonitor.ensureScheduled(this);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (exactAlarmPermissionButton != null) {
            updateExactAlarmPermissionUi();
        }
        noteUri = NoteStore.getSavedSourceUri(this);
        if (noteUri != null) {
            refreshButton.setEnabled(true);
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

    private void buildUi() {
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

        refreshButton = new Button(this);
        refreshButton.setText(R.string.refresh_note);
        refreshButton.setAllCaps(false);
        refreshButton.setEnabled(noteUri != null);
        refreshButton.setOnClickListener(view -> readAndRenderNote());
        LinearLayout.LayoutParams refreshParams = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        );
        refreshParams.setMargins(dp(10), 0, 0, 0);
        actions.addView(refreshButton, refreshParams);

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

        ScrollView scrollView = new ScrollView(this);
        taskList = new LinearLayout(this);
        taskList.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(taskList, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        root.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        setContentView(root);
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

    private void readAndRenderNote() {
        if (noteUri == null) {
            NoteChangeMonitor.cancel(this);
            ReminderScheduler.cancelScheduled(this);
            setStatus("Файл не выбран.");
            setNextReminder(null);
            renderEmptyState("Нажмите «Выбрать заметку».");
            return;
        }

        refreshButton.setEnabled(true);

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
        setStatus(buildStatus(parseResult, schedule, snapshot));
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
        List<ObsidianTask> visibleTasks = filterVisibleTasks(tasks);
        if (visibleTasks.isEmpty()) {
            renderEmptyState("В выбранных markdown-файлах нет уведомлений с @due(...) для текущего фильтра.");
            return;
        }

        String currentSource = null;
        boolean showGroupHeaders = hasMultipleSources(visibleTasks);
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
        if (!UserPreferences.isActiveOnly(this)) {
            return tasks;
        }

        java.util.ArrayList<ObsidianTask> activeTasks = new java.util.ArrayList<>();
        for (ObsidianTask task : tasks) {
            if (!task.isCompleted()) {
                activeTasks.add(task);
            }
        }
        return activeTasks;
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

    private View createSourceHeader(String sourceName) {
        TextView header = new TextView(this);
        header.setText(sourceName == null || sourceName.isEmpty() ? "Без имени файла" : sourceName);
        header.setTextColor(getColor(R.color.text_primary));
        header.setTextSize(16);
        header.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        header.setPadding(0, dp(14), 0, dp(8));
        return header;
    }

    private View createTaskView(ObsidianTask task) {
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

    private void showTaskMenu(Button anchor, ObsidianTask task) {
        PopupMenu popupMenu = new PopupMenu(this, anchor);
        popupMenu.getMenu().add(0, 1, 0, "Открыть заметку");
        popupMenu.getMenu().add(0, 2, 1, "Отложить");
        popupMenu.getMenu().add(0, 3, 2, "Отметить выполненной");
        popupMenu.getMenu().add(0, 4, 3, "Редактировать");
        popupMenu.getMenu().add(0, 5, 4, "Удалить");
        popupMenu.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == 1) {
                openNoteForTask(task);
                return true;
            }
            if (id == 2) {
                snoozeTask(task);
                return true;
            }
            if (id == 3) {
                markTaskDone(task);
                return true;
            }
            if (id == 4) {
                openTaskEditor(task);
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
        if (activeFilterButton == null) {
            return;
        }

        activeFilterButton.setText(UserPreferences.isActiveOnly(this)
                ? "Фильтр: только активные"
                : "Фильтр: все задачи");
    }

    private void setStatus(String message) {
        statusText.setText(message);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
