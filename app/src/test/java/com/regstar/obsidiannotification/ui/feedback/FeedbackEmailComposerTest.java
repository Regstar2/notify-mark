package com.regstar.obsidiannotification.ui.feedback;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class FeedbackEmailComposerTest {
    @Test
    public void buildMailToUri_encodesSubjectAndBody() {
        String uri = FeedbackEmailComposer.buildMailToUri(
                "avvv6940@gmail.com",
                "ObsidianNotification: обратная связь",
                "Здравствуйте.\n\n## Тип обращения"
        );

        assertTrue(uri.startsWith("mailto:"));
        assertTrue(uri.contains("subject="));
        assertTrue(uri.contains("body="));
        assertTrue(uri.contains("%D0%BE%D0%B1%D1%80%D0%B0%D1%82%D0%BD%D0%B0%D1%8F"));
    }

    @Test
    public void buildMailToUri_handlesNullsWithoutCrashing() {
        String uri = FeedbackEmailComposer.buildMailToUri(null, null, null);
        assertTrue(uri.startsWith("mailto:"));
        assertTrue(uri.contains("subject="));
        assertTrue(uri.contains("body="));
    }
}
