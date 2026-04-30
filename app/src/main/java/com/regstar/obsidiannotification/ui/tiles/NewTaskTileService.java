package com.regstar.obsidiannotification.ui.tiles;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.TileService;

import com.regstar.obsidiannotification.ui.AppLaunchIntents;

/**
 * Quick Settings tile that opens task creation flow.
 */
public final class NewTaskTileService extends TileService {
    @Override
    public void onClick() {
        super.onClick();
        Intent launchIntent = AppLaunchIntents.createNewTaskIntent(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    this,
                    2,
                    launchIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            startActivityAndCollapse(pendingIntent);
        } else {
            startActivityAndCollapse(launchIntent);
        }
    }
}
