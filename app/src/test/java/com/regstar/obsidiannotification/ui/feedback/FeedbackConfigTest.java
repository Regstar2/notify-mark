package com.regstar.obsidiannotification.ui.feedback;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class FeedbackConfigTest {
    @Test
    public void config_usesExpectedTemporaryEmail() {
        assertTrue(FeedbackConfig.SUPPORT_EMAIL.contains("@"));
        assertTrue(FeedbackConfig.SUPPORT_EMAIL.equals("avvv6940@gmail.com"));
    }

    @Test
    public void config_keepsGithubChannelsDisabledForPrivateRepository() {
        assertFalse(FeedbackConfig.GITHUB_ISSUES_ENABLED);
    }

    @Test
    public void config_containsPreparedGithubUrls() {
        assertTrue(FeedbackConfig.GITHUB_ISSUES_URL.contains("/issues"));
        assertTrue(FeedbackConfig.GITHUB_BUG_REPORT_URL.contains("title=Bug"));
        assertTrue(FeedbackConfig.GITHUB_FEATURE_REQUEST_URL.contains("title=Feature"));
    }
}
