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
    public void config_usesRepositoryIssues() {
        assertTrue(FeedbackConfig.GITHUB_ISSUES_ENABLED);
        assertTrue(FeedbackConfig.GITHUB_ISSUES_URL.equals(
                "https://github.com/Regstar2/notify-mark/issues"
        ));
    }

    @Test
    public void config_routesFeedbackToPreparedIssueForms() {
        assertTrue(FeedbackConfig.GITHUB_BUG_REPORT_URL.endsWith(
                "/issues/new?template=bug_report.yml"
        ));
        assertTrue(FeedbackConfig.GITHUB_FEATURE_REQUEST_URL.endsWith(
                "/issues/new?template=feature_request.yml"
        ));
    }
}
