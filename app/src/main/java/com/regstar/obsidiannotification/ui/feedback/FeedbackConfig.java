package com.regstar.obsidiannotification.ui.feedback;

/**
 * Centralized feedback endpoints and feature switches.
 *
 * <p>Keep this class as a single place for support channel values so future
 * production rollout only requires updating constants.</p>
 */
public final class FeedbackConfig {
    public static final String SUPPORT_EMAIL = "avvv6940@gmail.com";
    public static final boolean GITHUB_ISSUES_ENABLED = false;

    public static final String GITHUB_ISSUES_URL =
            "https://github.com/Regstar2/ObsidianNotifications/issues";

    public static final String GITHUB_BUG_REPORT_URL =
            "https://github.com/Regstar2/ObsidianNotifications/issues/new?title=Bug%3A%20&body=##%20%D0%9E%D0%BF%D0%B8%D1%81%D0%B0%D0%BD%D0%B8%D0%B5%20%D0%BF%D1%80%D0%BE%D0%B1%D0%BB%D0%B5%D0%BC%D1%8B%0A%0A%0A##%20%D0%A8%D0%B0%D0%B3%D0%B8%20%D0%B4%D0%BB%D1%8F%20%D0%B2%D0%BE%D1%81%D0%BF%D1%80%D0%BE%D0%B8%D0%B7%D0%B2%D0%B5%D0%B4%D0%B5%D0%BD%D0%B8%D1%8F%0A1.%20%0A2.%20%0A3.%20%0A%0A##%20%D0%9E%D0%B6%D0%B8%D0%B4%D0%B0%D0%B5%D0%BC%D0%BE%D0%B5%20%D0%BF%D0%BE%D0%B2%D0%B5%D0%B4%D0%B5%D0%BD%D0%B8%D0%B5%0A%0A%0A##%20%D0%A4%D0%B0%D0%BA%D1%82%D0%B8%D1%87%D0%B5%D1%81%D0%BA%D0%BE%D0%B5%20%D0%BF%D0%BE%D0%B2%D0%B5%D0%B4%D0%B5%D0%BD%D0%B8%D0%B5%0A%0A%0A##%20%D0%98%D0%BD%D1%84%D0%BE%D1%80%D0%BC%D0%B0%D1%86%D0%B8%D1%8F%0A-%20%D0%92%D0%B5%D1%80%D1%81%D0%B8%D1%8F%20%D0%BF%D1%80%D0%B8%D0%BB%D0%BE%D0%B6%D0%B5%D0%BD%D0%B8%D1%8F%3A%20%0A-%20Android%3A%20%0A-%20%D0%A3%D1%81%D1%82%D1%80%D0%BE%D0%B9%D1%81%D1%82%D0%B2%D0%BE%3A%20";

    public static final String GITHUB_FEATURE_REQUEST_URL =
            "https://github.com/Regstar2/ObsidianNotifications/issues/new?title=Feature%3A%20&body=##%20%D0%98%D0%B4%D0%B5%D1%8F%0A%0A%0A##%20%D0%97%D0%B0%D1%87%D0%B5%D0%BC%20%D1%8D%D1%82%D0%BE%20%D0%BD%D1%83%D0%B6%D0%BD%D0%BE%0A%0A%0A##%20%D0%9A%D0%B0%D0%BA%20%D1%8D%D1%82%D0%BE%20%D0%BC%D0%BE%D0%B6%D0%B5%D1%82%20%D1%80%D0%B0%D0%B1%D0%BE%D1%82%D0%B0%D1%82%D1%8C%0A%0A";

    private FeedbackConfig() {
    }
}
