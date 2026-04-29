package com.regstar.obsidiannotification.support;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Shared single-thread executor for IO-heavy background work started from
 * BroadcastReceivers and other non-UI entry points.
 *
 * <p>This keeps receivers responsive without pulling in WorkManager/DI.</p>
 */
public final class IoExecutor {
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();

    private IoExecutor() {
    }

    public static ExecutorService io() {
        return IO;
    }
}

