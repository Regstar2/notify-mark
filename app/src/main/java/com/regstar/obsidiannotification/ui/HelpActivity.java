package com.regstar.obsidiannotification.ui;

import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.regstar.obsidiannotification.R;

/**
 * Structured help screen with compact expandable sections.
 */
public final class HelpActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(getColor(R.color.background));
        root.setPadding(dp(16), dp(14), dp(16), dp(20));

        root.addView(createTopBar(), fullWidth());
        root.addView(createIntroCard(), fullWidthWithTopMargin(dp(10)));
        root.addView(createHelpSectionCard(
                getString(R.string.help_section_tasks_title),
                getString(R.string.help_section_tasks_summary),
                getString(R.string.help_section_tasks_body)
        ), fullWidthWithTopMargin(dp(10)));
        root.addView(createMarkdownSyntaxCard(), fullWidthWithTopMargin(dp(10)));
        root.addView(createHelpSectionCard(
                getString(R.string.help_section_statuses_title),
                getString(R.string.help_section_statuses_summary),
                getString(R.string.help_section_statuses_body)
        ), fullWidthWithTopMargin(dp(10)));
        root.addView(createHelpSectionCard(
                getString(R.string.help_section_actions_title),
                getString(R.string.help_section_actions_summary),
                getString(R.string.help_section_actions_body)
        ), fullWidthWithTopMargin(dp(10)));
        root.addView(createHelpSectionCard(
                getString(R.string.help_section_subtasks_title),
                getString(R.string.help_section_subtasks_summary),
                getString(R.string.help_section_subtasks_body)
        ), fullWidthWithTopMargin(dp(10)));
        root.addView(createHelpSectionCard(
                getString(R.string.help_section_calendar_title),
                getString(R.string.help_section_calendar_summary),
                getString(R.string.help_section_calendar_body)
        ), fullWidthWithTopMargin(dp(10)));
        root.addView(createHelpSectionCard(
                getString(R.string.help_section_faq_title),
                getString(R.string.help_section_faq_summary),
                getString(R.string.help_section_faq_body)
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
        back.setContentDescription(getString(R.string.help_back));
        back.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        back.setOnClickListener(view -> finish());
        bar.addView(back, new LinearLayout.LayoutParams(dp(40), dp(40)));

        TextView title = createText(getString(R.string.help_title), 21, R.color.text_primary, true);
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
        TextView body = createText(getString(R.string.help_intro), 13, R.color.text_secondary, false);
        card.addView(body, fullWidth());
        return card;
    }

    private LinearLayout createMarkdownSyntaxCard() {
        LinearLayout card = createHelpSectionCard(
                getString(R.string.help_section_markdown_title),
                getString(R.string.help_section_markdown_summary),
                null
        );
        LinearLayout body = (LinearLayout) card.getChildAt(1);
        body.addView(createText(getString(R.string.help_markdown_explainer), 13, R.color.text_secondary, false), fullWidth());
        body.addView(createText(getString(R.string.help_markdown_tokens), 13, R.color.text_primary, false), fullWidthWithTopMargin(dp(8)));
        body.addView(createCodeBlock(getString(R.string.help_example_due)), fullWidthWithTopMargin(dp(8)));
        body.addView(createCodeBlock(getString(R.string.help_example_repeat)), fullWidthWithTopMargin(dp(8)));
        body.addView(createCodeBlock(getString(R.string.help_example_until_done)), fullWidthWithTopMargin(dp(8)));
        return card;
    }

    private LinearLayout createHelpSectionCard(String title, String summary, String bodyText) {
        LinearLayout card = createCard();

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(android.view.Gravity.CENTER_VERTICAL);
        header.setClickable(true);
        header.setFocusable(true);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(createText(title, 16, R.color.text_primary, true), fullWidth());
        if (!TextUtils.isEmpty(summary)) {
            TextView summaryView = createText(summary, 12, R.color.text_secondary, false);
            summaryView.setPadding(0, dp(4), 0, 0);
            texts.addView(summaryView, fullWidth());
        }
        header.addView(texts, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        ));

        ImageView chevron = new ImageView(this);
        chevron.setImageResource(R.drawable.ic_chevron_right);
        chevron.setColorFilter(getColor(R.color.text_secondary));
        header.addView(chevron, new LinearLayout.LayoutParams(dp(20), dp(20)));
        card.addView(header, fullWidth());

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setVisibility(View.GONE);
        if (!TextUtils.isEmpty(bodyText)) {
            TextView bodyView = createText(bodyText, 13, R.color.text_secondary, false);
            bodyView.setPadding(0, dp(10), 0, 0);
            body.addView(bodyView, fullWidth());
        }
        card.addView(body, fullWidth());

        header.setOnClickListener(view -> {
            boolean open = body.getVisibility() == View.VISIBLE;
            body.setVisibility(open ? View.GONE : View.VISIBLE);
            chevron.setRotation(open ? 0f : 90f);
        });
        return card;
    }

    private TextView createCodeBlock(String code) {
        TextView block = createText(code, 12, R.color.text_primary, false);
        block.setTypeface(Typeface.MONOSPACE);
        block.setBackground(createRoundedBackground(getColor(R.color.background), getColor(R.color.card_stroke), 10));
        block.setPadding(dp(10), dp(10), dp(10), dp(10));
        return block;
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
