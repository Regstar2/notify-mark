package com.regstar.obsidiannotification;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class ReliabilityReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? "unknown" : intent.getAction();
        try {
            ReminderScheduler.ensureNotificationChannel(context);
            NoteChangeMonitor.NoteSyncResult result = NoteChangeMonitor.syncNow(context, true);
            if (!result.isSuccess() && !result.isRestoredFromCache()) {
                NoteChangeMonitor.restoreFromCache(context, result.getErrorMessage());
            }
            NoteChangeMonitor.ensureScheduled(context);
        } catch (RuntimeException exception) {
            ErrorLog.record(context, "Ошибка обработки системного события " + action, exception);
            NoteChangeMonitor.restoreFromCache(context, exception.getMessage());
            NoteChangeMonitor.ensureScheduled(context);
        }
    }
}
