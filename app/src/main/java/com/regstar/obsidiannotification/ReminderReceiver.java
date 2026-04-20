package com.regstar.obsidiannotification;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public final class ReminderReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        ReminderScheduler.ensureNotificationChannel(context);
        if (!ReminderScheduler.canPostNotifications(context)) {
            return;
        }

        int notificationId = intent.getIntExtra(
                ReminderScheduler.EXTRA_NOTIFICATION_ID,
                (int) System.currentTimeMillis()
        );
        String title = intent.getStringExtra(ReminderScheduler.EXTRA_TITLE);
        int lineNumber = intent.getIntExtra(ReminderScheduler.EXTRA_LINE_NUMBER, -1);
        long triggerAtMillis = intent.getLongExtra(
                ReminderScheduler.EXTRA_TRIGGER_AT_MILLIS,
                System.currentTimeMillis()
        );

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) {
            return;
        }

        notificationManager.notify(
                notificationId,
                buildNotification(context, safeTitle(title), lineNumber, triggerAtMillis)
        );
    }

    @SuppressWarnings("deprecation")
    private Notification buildNotification(
            Context context,
            String title,
            int lineNumber,
            long triggerAtMillis
    ) {
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(context, ReminderScheduler.CHANNEL_ID)
                : new Notification.Builder(context);

        builder.setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Напоминание Obsidian")
                .setContentText(title)
                .setStyle(new Notification.BigTextStyle().bigText(title))
                .setContentIntent(createOpenAppIntent(context))
                .setAutoCancel(true)
                .setWhen(triggerAtMillis)
                .setShowWhen(true)
                .setPriority(Notification.PRIORITY_DEFAULT);

        if (lineNumber > 0) {
            builder.setSubText("Строка " + lineNumber);
        }

        return builder.build();
    }

    private PendingIntent createOpenAppIntent(Context context) {
        Intent openAppIntent = new Intent(context, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        return PendingIntent.getActivity(
                context,
                0,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private String safeTitle(String title) {
        if (title == null || title.trim().isEmpty()) {
            return "Задача без текста";
        }
        return title;
    }
}
