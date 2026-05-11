package com.regstar.obsidiannotification.ui;

import com.regstar.obsidiannotification.R;

import com.regstar.obsidiannotification.core.reminders.ReminderActionReceiver;
import com.regstar.obsidiannotification.core.reminders.ReminderScheduler;
import com.regstar.obsidiannotification.core.source.NoteStore;
import com.regstar.obsidiannotification.core.tasks.RepeatMode;
import com.regstar.obsidiannotification.core.tasks.TaskEditResult;
import com.regstar.obsidiannotification.prefs.ActionPreferences;
import com.regstar.obsidiannotification.support.ErrorLog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import android.app.DatePickerDialog;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * Lightweight chooser opened from a notification action to snooze a reminder until a chosen
 * wall-clock time, without opening {@link MainActivity}.
 */
public final class ReminderTimePickerActivity extends AppCompatActivity {
    private static final int REQUEST_CODE_MASK = 0x5F000000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getIntent() == null || !ReminderScheduler.canPostNotifications(this)) {
            finish();
            return;
        }
        showChooser();
    }

    /**
     * {@link PendingIntent} target for the &quot;Время&quot; notification action.
     */
    public static PendingIntent createPendingIntent(
            Context context,
            String taskKey,
            int notificationId,
            int displayNotificationId,
            int lineNumber,
            String title,
            long repeatIntervalMillis,
            RepeatMode repeatMode
    ) {
        Intent intent = new Intent(context, ReminderTimePickerActivity.class);
        intent.putExtra(ReminderScheduler.EXTRA_TASK_KEY, taskKey);
        intent.putExtra(ReminderScheduler.EXTRA_NOTIFICATION_ID, notificationId);
        intent.putExtra(ReminderActionReceiver.EXTRA_DISPLAY_NOTIFICATION_ID, displayNotificationId);
        intent.putExtra(ReminderScheduler.EXTRA_LINE_NUMBER, lineNumber);
        intent.putExtra(ReminderScheduler.EXTRA_TITLE, title);
        intent.putExtra(ReminderScheduler.EXTRA_REPEAT_INTERVAL_MILLIS, repeatIntervalMillis);
        RepeatMode safeMode = repeatMode == null ? RepeatMode.NONE : repeatMode;
        intent.putExtra(ReminderScheduler.EXTRA_REPEAT_MODE, safeMode.name());
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        int requestCode = REQUEST_CODE_MASK ^ displayNotificationId;
        return PendingIntent.getActivity(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private void showChooser() {
        ZoneId zone = ZoneId.systemDefault();
        String[] labels = new String[]{
                getString(R.string.remind_at_time_only),
                getString(R.string.remind_at_date_time)
        };
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.reminder_remind_at_title)
                .setItems(labels, (dialog, which) -> {
                    if (which == 0) {
                        showTimeOnlyPicker(zone);
                    } else if (which == 1) {
                        showDateThenTime(zone);
                    } else {
                        finish();
                    }
                })
                .setOnCancelListener(dialog -> finish())
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> finish())
                .show();
    }

    private void showTimeOnlyPicker(ZoneId zone) {
        LocalDateTime now = LocalDateTime.now(zone);
        TimePickerDialog timeDialog = new TimePickerDialog(
                this,
                (view, hourOfDay, minute) -> {
                    LocalTime pickedTime = LocalTime.of(hourOfDay, minute);
                    LocalDate date = LocalDate.now(zone);
                    LocalDateTime candidate = date.atTime(pickedTime);
                    if (!candidate.isAfter(LocalDateTime.now(zone))) {
                        candidate = date.plusDays(1).atTime(pickedTime);
                    }
                    applyAndFinish(candidate);
                },
                now.getHour(),
                now.getMinute(),
                true
        );
        timeDialog.setOnCancelListener(dialog -> finish());
        timeDialog.show();
    }

    private void showDateThenTime(ZoneId zone) {
        LocalDateTime now = LocalDateTime.now(zone);
        LocalDate initialDate = now.toLocalDate();
        DatePickerDialog dateDialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    LocalDate date = LocalDate.of(year, month + 1, dayOfMonth);
                    showTimeForDate(zone, date);
                },
                initialDate.getYear(),
                initialDate.getMonthValue() - 1,
                initialDate.getDayOfMonth()
        );
        dateDialog.setOnCancelListener(dialog -> finish());
        dateDialog.show();
    }

    private void showTimeForDate(ZoneId zone, LocalDate date) {
        LocalDateTime now = LocalDateTime.now(zone);
        TimePickerDialog timeDialog = new TimePickerDialog(
                this,
                (view1, hourOfDay, minute) -> {
                    LocalDateTime picked = date.atTime(hourOfDay, minute);
                    if (!picked.isAfter(LocalDateTime.now(zone))) {
                        Toast.makeText(
                                this,
                                R.string.remind_at_past_error,
                                Toast.LENGTH_SHORT
                        ).show();
                        showTimeForDate(zone, date);
                        return;
                    }
                    applyAndFinish(picked);
                },
                now.getHour(),
                now.getMinute(),
                true
        );
        timeDialog.setOnCancelListener(dialog -> finish());
        timeDialog.show();
    }

    private void applyAndFinish(LocalDateTime triggerAt) {
        Intent intent = getIntent();
        String taskKey = intent.getStringExtra(ReminderScheduler.EXTRA_TASK_KEY);
        int notificationId = intent.getIntExtra(
                ReminderScheduler.EXTRA_NOTIFICATION_ID,
                (int) System.currentTimeMillis()
        );
        int displayNotificationId = intent.getIntExtra(ReminderActionReceiver.EXTRA_DISPLAY_NOTIFICATION_ID, -1);
        int lineNumber = intent.getIntExtra(ReminderScheduler.EXTRA_LINE_NUMBER, -1);
        String title = intent.getStringExtra(ReminderScheduler.EXTRA_TITLE);
        long repeatIntervalMillis = intent.getLongExtra(
                ReminderScheduler.EXTRA_REPEAT_INTERVAL_MILLIS,
                0L
        );
        RepeatMode repeatMode = RepeatMode.fromName(
                intent.getStringExtra(ReminderScheduler.EXTRA_REPEAT_MODE)
        );

        ReminderScheduler.scheduleSnoozeUntil(
                getApplicationContext(),
                taskKey,
                notificationId,
                lineNumber,
                title,
                triggerAt,
                repeatIntervalMillis,
                repeatMode
        );
        if (ActionPreferences.shouldRecordSnoozeCount(this)) {
            TaskEditResult result = NoteStore.incrementSnoozeCount(this, taskKey);
            if (result.isFailure()) {
                ErrorLog.record(
                        this,
                        getString(R.string.runtime_snooze_count_write_error, result.getMessage())
                );
            }
        }
        cancelNotification(notificationId, displayNotificationId);
        finish();
    }

    private void cancelNotification(int notificationId, int displayNotificationId) {
        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) {
            return;
        }
        if (displayNotificationId >= 0) {
            notificationManager.cancel(displayNotificationId);
        }
        if (notificationId >= 0 && notificationId != displayNotificationId) {
            notificationManager.cancel(notificationId);
        }
    }
}
