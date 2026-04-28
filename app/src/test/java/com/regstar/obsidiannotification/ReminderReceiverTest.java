package com.regstar.obsidiannotification;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class ReminderReceiverTest {
    @Test
    public void hasNagLoop_trueForExplicitUntilDoneMode() {
        assertTrue(ReminderReceiver.hasNagLoop(0L, RepeatMode.UNTIL_DONE));
    }

    @Test
    public void hasNagLoop_trueForRepeatSeriesWithResolvedNagInterval() {
        assertTrue(ReminderReceiver.hasNagLoop(300_000L, RepeatMode.ALWAYS));
    }

    @Test
    public void hasNagLoop_falseForOneShotWithoutNagInterval() {
        assertFalse(ReminderReceiver.hasNagLoop(0L, RepeatMode.NONE));
    }

    @Test
    public void shouldRepostNotification_trueForNagUpdates() {
        assertTrue(ReminderReceiver.shouldRepostNotification(300_000L, RepeatMode.ALWAYS));
    }
}
