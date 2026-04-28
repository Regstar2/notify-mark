package com.regstar.obsidiannotification.core.reminders;

import com.regstar.obsidiannotification.R;
import com.regstar.obsidiannotification.ui.MainActivity;

import com.regstar.obsidiannotification.core.source.*;
import com.regstar.obsidiannotification.core.tasks.*;
import com.regstar.obsidiannotification.prefs.*;
import com.regstar.obsidiannotification.support.ErrorLog;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public final class TestNotificationSender {
    private TestNotificationSender() {
    }

    @SuppressWarnings("deprecation")
    public static boolean send(Context context) {
        ReminderScheduler.ensureNotificationChannel(context);
        if (!ReminderScheduler.canPostNotifications(context)) {
            return false;
        }

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) {
            return false;
        }

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(context, ReminderScheduler.CHANNEL_ID)
                : new Notification.Builder(context);

        builder.setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Р В РЎС›Р В Р’ВµР РЋР С“Р РЋРІР‚С™Р В РЎвЂўР В Р вЂ Р В РЎвЂўР В Р’Вµ Р В Р вЂ¦Р В Р’В°Р В РЎвЂ”Р В РЎвЂўР В РЎВР В РЎвЂР В Р вЂ¦Р В Р’В°Р В Р вЂ¦Р В РЎвЂР В Р’Вµ")
                .setContentText("Р В РІР‚СћР РЋР С“Р В Р’В»Р В РЎвЂ Р РЋР РЉР РЋРІР‚С™Р В РЎвЂў Р В Р вЂ Р В РЎвЂР В РўвЂР В Р вЂ¦Р В РЎвЂў Р В РЎвЂ Р РЋР С“Р В Р’В»Р РЋРІР‚в„–Р РЋРІвЂљВ¬Р В Р вЂ¦Р В РЎвЂў, Р В РЎвЂќР В Р’В°Р В Р вЂ¦Р В Р’В°Р В Р’В» Р РЋРЎвЂњР В Р вЂ Р В Р’ВµР В РўвЂР В РЎвЂўР В РЎВР В Р’В»Р В Р’ВµР В Р вЂ¦Р В РЎвЂР В РІвЂћвЂ“ Р РЋР вЂљР В Р’В°Р В Р’В±Р В РЎвЂўР РЋРІР‚С™Р В Р’В°Р В Р’ВµР РЋРІР‚С™.")
                .setStyle(new Notification.BigTextStyle()
                        .bigText("Р В РІР‚СћР РЋР С“Р В Р’В»Р В РЎвЂ Р РЋР РЉР РЋРІР‚С™Р В РЎвЂў Р В Р вЂ Р В РЎвЂР В РўвЂР В Р вЂ¦Р В РЎвЂў Р В РЎвЂ Р РЋР С“Р В Р’В»Р РЋРІР‚в„–Р РЋРІвЂљВ¬Р В Р вЂ¦Р В РЎвЂў, Р В РЎвЂќР В Р’В°Р В Р вЂ¦Р В Р’В°Р В Р’В» Р РЋРЎвЂњР В Р вЂ Р В Р’ВµР В РўвЂР В РЎвЂўР В РЎВР В Р’В»Р В Р’ВµР В Р вЂ¦Р В РЎвЂР В РІвЂћвЂ“ Р РЋР вЂљР В Р’В°Р В Р’В±Р В РЎвЂўР РЋРІР‚С™Р В Р’В°Р В Р’ВµР РЋРІР‚С™."))
                .setContentIntent(createOpenAppIntent(context))
                .setAutoCancel(true)
                .setWhen(System.currentTimeMillis())
                .setShowWhen(true)
                .setOnlyAlertOnce(false)
                .setCategory(Notification.CATEGORY_REMINDER)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setDefaults(Notification.DEFAULT_SOUND
                        | Notification.DEFAULT_VIBRATE
                        | Notification.DEFAULT_LIGHTS)
                .setPriority(Notification.PRIORITY_MAX);

        notificationManager.notify((int) System.currentTimeMillis(), builder.build());
        return true;
    }

    private static PendingIntent createOpenAppIntent(Context context) {
        Intent openAppIntent = new Intent(context, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        return PendingIntent.getActivity(
                context,
                0,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }
}
