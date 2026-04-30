package com.regstar.obsidiannotification.ui.feedback;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Builds safe mailto links for feedback intents.
 */
public final class FeedbackEmailComposer {
    private FeedbackEmailComposer() {
    }

    public static String buildMailToUri(String email, String subject, String body) {
        String safeEmail = email == null ? "" : email.trim();
        String safeSubject = subject == null ? "" : subject;
        String safeBody = body == null ? "" : body;
        return "mailto:" + urlEncode(safeEmail)
                + "?subject=" + urlEncode(safeSubject)
                + "&body=" + urlEncode(safeBody);
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
