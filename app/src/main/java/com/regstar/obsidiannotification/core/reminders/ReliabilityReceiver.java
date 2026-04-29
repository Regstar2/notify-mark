package com.regstar.obsidiannotification.core.reminders;

import com.regstar.obsidiannotification.R;
import com.regstar.obsidiannotification.core.source.NoteChangeMonitor;
import com.regstar.obsidiannotification.support.ErrorLog;
import com.regstar.obsidiannotification.support.IoExecutor;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class ReliabilityReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        final PendingResult pendingResult = goAsync();
        final Context appContext = context == null ? null : context.getApplicationContext();
        final String action = intent == null ? "unknown" : intent.getAction();
        IoExecutor.io().execute(() -> {
            try {
                if (appContext == null) {
                    return;
                }
                ReminderScheduler.ensureNotificationChannel(appContext);
                NoteChangeMonitor.NoteSyncResult result = NoteChangeMonitor.syncNow(appContext, true);
                if (!result.isSuccess() && !result.isRestoredFromCache()) {
                    NoteChangeMonitor.restoreFromCache(appContext, result.getErrorMessage());
                }
                NoteChangeMonitor.ensureScheduled(appContext);
            } catch (Exception exception) {
                ErrorLog.record(appContext, appContext.getString(R.string.runtime_system_event_error, action), exception);
                NoteChangeMonitor.restoreFromCache(appContext, exception.getMessage());
                NoteChangeMonitor.ensureScheduled(appContext);
            } finally {
                pendingResult.finish();
            }
        });
    }
}
