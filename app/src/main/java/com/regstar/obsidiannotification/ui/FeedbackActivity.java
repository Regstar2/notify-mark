package com.regstar.obsidiannotification.ui;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.regstar.obsidiannotification.R;
import com.regstar.obsidiannotification.ui.feedback.FeedbackConfig;
import com.regstar.obsidiannotification.ui.feedback.FeedbackEmailComposer;

/**
 * Feedback screen with GitHub issue channels and an optional email channel.
 */
public final class FeedbackActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(getColor(R.color.background));
        root.setPadding(dp(16), dp(14), dp(16), dp(20));

        root.addView(createTopBar(), fullWidth());
        root.addView(createIntroCard(), fullWidthWithTopMargin(dp(10)));
        root.addView(createChannelCard(
                getString(R.string.feedback_email_title),
                getString(R.string.feedback_email_subtitle),
                FeedbackConfig.EMAIL_ENABLED
                        ? FeedbackConfig.SUPPORT_EMAIL
                        : getString(R.string.feedback_channel_unavailable),
                FeedbackConfig.EMAIL_ENABLED,
                this::openEmailFeedback
        ), fullWidthWithTopMargin(dp(10)));
        root.addView(createChannelCard(
                getString(R.string.feedback_github_issues_title),
                getString(R.string.feedback_github_issues_subtitle),
                getString(R.string.feedback_channel_unavailable),
                FeedbackConfig.GITHUB_ISSUES_ENABLED,
                () -> {
                    if (FeedbackConfig.GITHUB_ISSUES_ENABLED) {
                        openUrl(FeedbackConfig.GITHUB_ISSUES_URL);
                    } else {
                        showNotReadyMessage();
                    }
                }
        ), fullWidthWithTopMargin(dp(10)));
        root.addView(createChannelCard(
                getString(R.string.feedback_bug_title),
                getString(R.string.feedback_bug_subtitle),
                getString(R.string.feedback_channel_unavailable),
                FeedbackConfig.GITHUB_ISSUES_ENABLED,
                () -> {
                    if (FeedbackConfig.GITHUB_ISSUES_ENABLED) {
                        openUrl(FeedbackConfig.GITHUB_BUG_REPORT_URL);
                    } else {
                        showNotReadyMessage();
                    }
                }
        ), fullWidthWithTopMargin(dp(10)));
        root.addView(createChannelCard(
                getString(R.string.feedback_feature_title),
                getString(R.string.feedback_feature_subtitle),
                getString(R.string.feedback_channel_unavailable),
                FeedbackConfig.GITHUB_ISSUES_ENABLED,
                () -> {
                    if (FeedbackConfig.GITHUB_ISSUES_ENABLED) {
                        openUrl(FeedbackConfig.GITHUB_FEATURE_REQUEST_URL);
                    } else {
                        showNotReadyMessage();
                    }
                }
        ), fullWidthWithTopMargin(dp(10)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        setContentView(scroll);
    }

    private LinearLayout createTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(android.view.Gravity.CENTER_VERTICAL);

        ImageButton back = new ImageButton(this);
        back.setImageResource(R.drawable.ic_arrow_back);
        back.setContentDescription(getString(R.string.feedback_back));
        back.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        back.setOnClickListener(view -> finish());
        bar.addView(back, new LinearLayout.LayoutParams(dp(40), dp(40)));

        TextView title = createText(getString(R.string.feedback_title), 21, R.color.text_primary, true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        );
        titleParams.setMargins(dp(8), 0, 0, 0);
        bar.addView(title, titleParams);
        return bar;
    }

    private LinearLayout createIntroCard() {
        LinearLayout card = createCard();
        card.addView(createText(getString(R.string.feedback_intro), 13, R.color.text_secondary, false), fullWidth());
        return card;
    }

    private LinearLayout createChannelCard(
            String title,
            String subtitle,
            String meta,
            boolean enabled,
            Runnable action
    ) {
        LinearLayout card = createCard();
        card.setClickable(true);
        card.setEnabled(enabled);
        card.setOnClickListener(view -> action.run());
        card.setAlpha(enabled ? 1f : 0.72f);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);

        LinearLayout textColumn = new LinearLayout(this);
        textColumn.setOrientation(LinearLayout.VERTICAL);
        textColumn.addView(createText(title, 16, R.color.text_primary, true), fullWidth());

        TextView subtitleView = createText(subtitle, 12, R.color.text_secondary, false);
        subtitleView.setPadding(0, dp(4), 0, 0);
        textColumn.addView(subtitleView, fullWidth());

        if (!TextUtils.isEmpty(meta)) {
            TextView metaView = createText(meta, 12, R.color.text_primary, false);
            metaView.setPadding(0, dp(8), 0, 0);
            metaView.setTypeface(Typeface.MONOSPACE);
            textColumn.addView(metaView, fullWidth());
        }

        row.addView(textColumn, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        ));

        ImageView trailing = new ImageView(this);
        trailing.setImageResource(enabled ? R.drawable.ic_chevron_right : R.drawable.ic_info);
        trailing.setColorFilter(getColor(R.color.text_secondary));
        row.addView(trailing, new LinearLayout.LayoutParams(dp(20), dp(20)));
        card.addView(row, fullWidth());
        return card;
    }

    private void openEmailFeedback() {
        String mailToUri = FeedbackEmailComposer.buildMailToUri(
                FeedbackConfig.SUPPORT_EMAIL,
                getString(R.string.feedback_email_subject),
                getString(R.string.feedback_email_body_template)
        );
        Uri uri = Uri.parse(mailToUri);
        Intent intent = new Intent(Intent.ACTION_SENDTO, uri);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException exception) {
            Toast.makeText(this, getString(R.string.feedback_no_email_app), Toast.LENGTH_LONG).show();
        }
    }

    private void openUrl(String rawUrl) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(rawUrl)));
        } catch (RuntimeException exception) {
            Toast.makeText(this, getString(R.string.feedback_open_link_failed), Toast.LENGTH_LONG).show();
        }
    }

    private void showNotReadyMessage() {
        Toast.makeText(this, getString(R.string.feedback_github_not_ready), Toast.LENGTH_LONG).show();
    }

    private LinearLayout createCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(createRoundedBackground(getColor(R.color.card_background), getColor(R.color.card_stroke), 16));
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        return card;
    }

    private TextView createText(String text, int sizeSp, int colorRes, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(sizeSp);
        view.setTextColor(getColor(colorRes));
        view.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        return view;
    }

    private GradientDrawable createRoundedBackground(int fillColor, int strokeColor, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fillColor);
        drawable.setCornerRadius(dp(radiusDp));
        if (strokeColor != 0) {
            drawable.setStroke(dp(1), strokeColor);
        }
        return drawable;
    }

    private LinearLayout.LayoutParams fullWidth() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    private LinearLayout.LayoutParams fullWidthWithTopMargin(int topMargin) {
        LinearLayout.LayoutParams params = fullWidth();
        params.setMargins(0, topMargin, 0, 0);
        return params;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
