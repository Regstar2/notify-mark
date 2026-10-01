package com.regstar.obsidiannotification.ui.feedback;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class FeedbackConfigTest {
    @Test
    public void config_doesNotPublishPersonalSupportEmail() {
        assertFalse(FeedbackConfig.EMAIL_ENABLED);
        assertTrue(FeedbackConfig.SUPPORT_EMAIL.isEmpty());
    }

    @Test
    public void config_usesPublicRepositoryIssues() {
        assertTrue(FeedbackConfig.GITHUB_ISSUES_ENABLED);
        assertTrue(FeedbackConfig.GITHUB_ISSUES_URL.equals(
                "https://github.com/Regstar2/notify-mark/issues"
        ));
    }

    @Test
    public void config_containsPreparedGithubUrls() {
        assertTrue(FeedbackConfig.GITHUB_BUG_REPORT_URL.contains("/Regstar2/notify-mark/issues/new"));
        assertTrue(FeedbackConfig.GITHUB_BUG_REPORT_URL.contains("title=Bug"));
        assertTrue(FeedbackConfig.GITHUB_FEATURE_REQUEST_URL.contains("title=Feature"));
    }
}
