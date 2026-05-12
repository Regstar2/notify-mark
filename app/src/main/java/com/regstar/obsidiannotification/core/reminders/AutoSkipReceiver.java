package com.regstar.obsidiannotification.core.reminders;

import com.regstar.obsidiannotification.R;
import com.regstar.obsidiannotification.core.source.NoteChangeMonitor;
import com.regstar.obsidiannotification.support.ErrorLog;
import com.regstar.obsidiannotification.support.IoExecutor;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Fires at the planned auto-skip instant; verifies occurrence identity then skips via
 * {@link AutoSkipExecutor} / {@link com.regstar.obsidiannotification.core.source.NoteStore#markTaskSkipped}.
 */
public final class AutoSkipReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        final PendingResult pendingResult = goAsync();
        final Context appContext = context == null ? null : context.getApplicationContext();
        IoExecutor.io().execute(() -> {
            try {
                if (appContext == null || intent == null) {
                    return;
                }
                if (!AutoSkipScheduler.ACTION_AUTO_SKIP.equals(intent.getAction())) {
                    return;
                }
                String taskKey = intent.getStringExtra(AutoSkipScheduler.EXTRA_TASK_KEY);
                long expected = intent.getLongExtra(AutoSkipScheduler.EXTRA_EXPECTED_REMINDER_AT_MILLIS, Long.MIN_VALUE);
                if (taskKey == null || taskKey.trim().isEmpty() || expected == Long.MIN_VALUE) {
                    return;
                }
                if (AutoSkipExecutor.trySkipForOccurrence(appContext, taskKey, expected)) {
                    NoteChangeMonitor.syncNow(appContext, true);
                }
            } catch (Exception exception) {
                Context logContext = appContext != null ? appContext : context;
                if (logContext != null) {
                    ErrorLog.record(
                            logContext,
                            logContext.getString(R.string.runtime_auto_skip_error),
                            exception
                    );
                }
            } finally {
                pendingResult.finish();
            }
        });
    }
}
