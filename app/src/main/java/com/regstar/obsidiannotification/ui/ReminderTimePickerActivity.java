package com.regstar.obsidiannotification.ui;

import com.regstar.obsidiannotification.R;

import com.regstar.obsidiannotification.core.reminders.ReminderActionReceiver;
import com.regstar.obsidiannotification.core.reminders.ReminderScheduler;
import com.regstar.obsidiannotification.core.source.NoteStore;
import com.regstar.obsidiannotification.core.tasks.RepeatMode;
import com.regstar.obsidiannotification.core.tasks.TaskEditResult;
import com.regstar.obsidiannotification.prefs.ActionPreferences;
import com.regstar.obsidiannotification.support.ErrorLog;

import android.app.DatePickerDialog;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * Lightweight chooser opened from a notification action to snooze a reminder until a preset
 * or user-selected date and time.
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
        showPresetChooser();
    }

    /**
     * {@link PendingIntent} target for the &quot;Remind at&quot; notification action.
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

    private void showPresetChooser() {
        ZoneId zone = ZoneId.systemDefault();
        LocalDateTime now = LocalDateTime.now(zone);
        String[] labels = new String[]{
                getString(R.string.remind_at_15m),
                getString(R.string.remind_at_1h),
                getString(R.string.remind_at_tonight),
                getString(R.string.remind_at_tomorrow_morning),
                getString(R.string.remind_at_custom)
        };
        new AlertDialog.Builder(this)
                .setTitle(R.string.reminder_remind_at_title)
                .setItems(labels, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            applyAndFinish(now.plusMinutes(15));
                            break;
                        case 1:
                            applyAndFinish(now.plusHours(1));
                            break;
                        case 2:
                            applyAndFinish(tonightAt(LocalTime.of(19, 0), now, zone));
                            break;
                        case 3:
                            applyAndFinish(tomorrowMorningAt(LocalTime.of(9, 0), now, zone));
                            break;
                        case 4:
                            showCustomDateTime(zone);
                            break;
                        default:
                            finish();
                    }
                })
                .setOnCancelListener(dialog -> finish())
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> finish())
                .show();
    }

    private static LocalDateTime tonightAt(LocalTime time, LocalDateTime now, ZoneId zone) {
        LocalDateTime candidate = LocalDate.now(zone).atTime(time);
        if (!candidate.isAfter(now)) {
            candidate = candidate.plusDays(1);
        }
        return candidate;
    }

    private static LocalDateTime tomorrowMorningAt(LocalTime time, LocalDateTime now, ZoneId zone) {
        LocalDateTime candidate = LocalDate.now(zone).plusDays(1).atTime(time);
        while (!candidate.isAfter(now)) {
            candidate = candidate.plusDays(1);
        }
        return candidate;
    }

    private void showCustomDateTime(ZoneId zone) {
        LocalDateTime now = LocalDateTime.now(zone);
        LocalDate initialDate = now.toLocalDate();
        DatePickerDialog dateDialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    LocalDate date = LocalDate.of(year, month + 1, dayOfMonth);
                    TimePickerDialog timeDialog = new TimePickerDialog(
                            ReminderTimePickerActivity.this,
                            (view1, hourOfDay, minute) -> {
                                LocalDateTime picked = date.atTime(hourOfDay, minute);
                                LocalDateTime safe = ReminderScheduler.ensureFutureTriggerAt(
                                        picked,
                                        LocalDateTime.now(zone),
                                        zone
                                );
                                applyAndFinish(safe);
                            },
                            now.getHour(),
                            now.getMinute(),
                            true
                    );
                    timeDialog.setOnCancelListener(dialog -> finish());
                    timeDialog.show();
                },
                initialDate.getYear(),
                initialDate.getMonthValue() - 1,
                initialDate.getDayOfMonth()
        );
        dateDialog.setOnCancelListener(dialog -> finish());
        dateDialog.show();
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
