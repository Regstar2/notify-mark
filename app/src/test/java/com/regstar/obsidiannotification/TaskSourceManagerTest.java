package com.regstar.obsidiannotification;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class TaskSourceManagerTest {
    @Test
    public void resolveInitialModeDefaultsToInternalWhenNoExternalSourceExists() {
        assertEquals(
                TaskStorageMode.INTERNAL_MARKDOWN_STORAGE,
                TaskSourceManager.resolveInitialMode(null, false)
        );
    }

    @Test
    public void resolveInitialModeFallsBackToExternalForLegacyExternalSource() {
        assertEquals(
                TaskStorageMode.EXTERNAL_MARKDOWN_STORAGE,
                TaskSourceManager.resolveInitialMode(null, true)
        );
    }

    @Test
    public void resolveInitialModeKeepsPersistedChoice() {
        assertEquals(
                TaskStorageMode.INTERNAL_MARKDOWN_STORAGE,
                TaskSourceManager.resolveInitialMode(TaskStorageMode.INTERNAL_MARKDOWN_STORAGE, true)
        );
        assertEquals(
                TaskStorageMode.EXTERNAL_MARKDOWN_STORAGE,
                TaskSourceManager.resolveInitialMode(TaskStorageMode.EXTERNAL_MARKDOWN_STORAGE, false)
        );
    }
}
