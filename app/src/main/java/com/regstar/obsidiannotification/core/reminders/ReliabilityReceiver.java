package com.regstar.obsidiannotification.core.reminders;

import com.regstar.obsidiannotification.core.source.*;
import com.regstar.obsidiannotification.core.tasks.*;
import com.regstar.obsidiannotification.prefs.*;
import com.regstar.obsidiannotification.support.ErrorLog;

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
            ErrorLog.record(context, "РћС€РёР±РєР° РѕР±СЂР°Р±РѕС‚РєРё СЃРёСЃС‚РµРјРЅРѕРіРѕ СЃРѕР±С‹С‚РёСЏ " + action, exception);
            NoteChangeMonitor.restoreFromCache(context, exception.getMessage());
            NoteChangeMonitor.ensureScheduled(context);
        }
    }
}
