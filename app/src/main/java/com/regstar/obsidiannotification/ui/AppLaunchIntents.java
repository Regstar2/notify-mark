package com.regstar.obsidiannotification.ui;

import android.content.Context;
import android.content.Intent;

/**
 * Shared app entry intents for external launchers and tiles.
 */
public final class AppLaunchIntents {
    public static final String ACTION_OPEN_TASKS =
            "com.regstar.obsidiannotification.action.OPEN_TASKS";
    public static final String ACTION_NEW_TASK =
            "com.regstar.obsidiannotification.action.NEW_TASK";

    private AppLaunchIntents() {
    }

    public static Intent createOpenTasksIntent(Context context) {
        return new Intent(context, MainActivity.class)
                .setAction(ACTION_OPEN_TASKS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    }

    public static Intent createNewTaskIntent(Context context) {
        return new Intent(context, MainActivity.class)
                .setAction(ACTION_NEW_TASK)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    }
}
