package com.regstar.obsidiannotification.core.source;

import com.regstar.obsidiannotification.R;
import com.regstar.obsidiannotification.core.reminders.*;
import com.regstar.obsidiannotification.core.tasks.*;
import com.regstar.obsidiannotification.prefs.*;
import com.regstar.obsidiannotification.support.ErrorLog;
import com.regstar.obsidiannotification.support.IoExecutor;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class NoteSyncReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !NoteChangeMonitor.ACTION_SYNC_NOTE.equals(intent.getAction())) {
            return;
        }
        final PendingResult pendingResult = goAsync();
        final Context appContext = context == null ? null : context.getApplicationContext();
        IoExecutor.io().execute(() -> {
            try {
                if (appContext == null) {
                    return;
                }
                NoteChangeMonitor.syncNow(appContext);
                NoteChangeMonitor.ensureScheduled(appContext);
            } catch (Exception exception) {
                if (appContext != null) {
                    ErrorLog.record(
                            appContext,
                            appContext.getString(R.string.runtime_note_sync_receiver_error),
                            exception
                    );
                }
            } finally {
                pendingResult.finish();
            }
        });
    }
}
