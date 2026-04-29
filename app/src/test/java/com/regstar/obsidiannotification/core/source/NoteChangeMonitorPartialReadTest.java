package com.regstar.obsidiannotification.core.source;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class NoteChangeMonitorPartialReadTest {
    @Test
    public void suspiciousDelta_detectsDocumentDropWhenCacheExists() {
        assertTrue(NoteChangeMonitor.isSuspiciousSnapshotDelta(
                10,
                100,
                10000,
                6,
                90,
                9000,
                true
        ));
    }

    @Test
    public void suspiciousDelta_doesNotTriggerWithoutCache() {
        assertFalse(NoteChangeMonitor.isSuspiciousSnapshotDelta(
                10,
                100,
                10000,
                6,
                90,
                9000,
                false
        ));
    }

    @Test
    public void suspiciousDelta_detectsLargeTaskDrop() {
        assertTrue(NoteChangeMonitor.isSuspiciousSnapshotDelta(
                2,
                20,
                2000,
                2,
                5,
                1900,
                true
        ));
    }

    @Test
    public void suspiciousDelta_allowsNormalChanges() {
        assertFalse(NoteChangeMonitor.isSuspiciousSnapshotDelta(
                2,
                20,
                2000,
                2,
                18,
                1800,
                true
        ));
    }

    @Test
    public void sourceChange_bypassesSuspiciousProtection() {
        assertTrue(NoteChangeMonitor.shouldBypassPartialReadProtection(
                "external|folder:content://old",
                "external|folder:content://new"
        ));
        assertFalse(NoteChangeMonitor.shouldBypassPartialReadProtection(
                "external|folder:content://same",
                "external|folder:content://same"
        ));
    }

    @Test
    public void suspiciousReason_prefersDocumentDrop() {
        assertTrue(
                NoteChangeMonitor.suspiciousSnapshotReason(
                        10,
                        100,
                        10000,
                        6,
                        90,
                        9000
                ).contains("document count drop")
        );
    }
}

