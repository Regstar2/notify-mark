package com.regstar.obsidiannotification.ui.feedback;

/**
 * Centralized feedback endpoints and feature switches.
 *
 * <p>Public builds use GitHub Issues without embedding a client-side GitHub token
 * or personal support mailbox.</p>
 */
public final class FeedbackConfig {
    public static final boolean EMAIL_ENABLED = false;
    public static final String SUPPORT_EMAIL = "";
    public static final boolean GITHUB_ISSUES_ENABLED = true;

    public static final String GITHUB_ISSUES_URL =
            "https://github.com/Regstar2/notify-mark/issues";

    public static final String GITHUB_BUG_REPORT_URL =
            "https://github.com/Regstar2/notify-mark/issues/new?template=bug_report.yml";

    public static final String GITHUB_FEATURE_REQUEST_URL =
            "https://github.com/Regstar2/notify-mark/issues/new?template=feature_request.yml";

    private FeedbackConfig() {
    }
}
