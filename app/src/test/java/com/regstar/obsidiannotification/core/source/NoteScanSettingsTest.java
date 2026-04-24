package com.regstar.obsidiannotification.core.source;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class NoteScanSettingsTest {
    @Test
    public void shouldReadFile_matchesIncludeAndExcludePatterns() {
        NoteScanSettings settings = NoteScanSettings.fromValues(
                "*.md, tasks/*.markdown",
                "archive/**, **/.obsidian/**",
                100
        );

        assertTrue(settings.shouldReadFile("inbox.md", "inbox.md"));
        assertTrue(settings.shouldReadFile("tasks/today.markdown", "today.markdown"));
        assertFalse(settings.shouldReadFile("archive/old.md", "old.md"));
        assertFalse(settings.shouldReadFile("vault/.obsidian/config.md", "config.md"));
        assertFalse(settings.shouldReadFile("image.png", "image.png"));
    }

    @Test
    public void shouldSkipDirectory_matchesNestedServiceFolders() {
        NoteScanSettings settings = NoteScanSettings.defaults();

        assertTrue(settings.shouldSkipDirectory(".obsidian"));
        assertTrue(settings.shouldSkipDirectory("projects/.obsidian"));
        assertTrue(settings.shouldSkipDirectory("archive"));
        assertTrue(settings.shouldSkipDirectory("projects/archive"));
        assertFalse(settings.shouldSkipDirectory("projects/tasks"));
    }
}


