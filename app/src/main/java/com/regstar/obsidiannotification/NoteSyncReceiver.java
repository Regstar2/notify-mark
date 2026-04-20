package com.regstar.obsidiannotification;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class NoteSyncReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!NoteChangeMonitor.ACTION_SYNC_NOTE.equals(intent.getAction())) {
            return;
        }

        NoteChangeMonitor.syncNow(context);
        NoteChangeMonitor.ensureScheduled(context);
    }
}
