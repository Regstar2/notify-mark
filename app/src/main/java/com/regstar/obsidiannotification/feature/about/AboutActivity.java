package com.regstar.obsidiannotification.feature.about;

import com.regstar.obsidiannotification.R;

import com.regstar.obsidiannotification.core.debug.*;
import com.regstar.obsidiannotification.core.markdown.*;
import com.regstar.obsidiannotification.core.model.*;
import com.regstar.obsidiannotification.core.notifications.*;
import com.regstar.obsidiannotification.core.preferences.*;
import com.regstar.obsidiannotification.core.source.*;
import com.regstar.obsidiannotification.core.storage.*;
import com.regstar.obsidiannotification.feature.about.*;
import com.regstar.obsidiannotification.feature.editor.*;
import com.regstar.obsidiannotification.feature.main.*;
import com.regstar.obsidiannotification.feature.onboarding.*;
import com.regstar.obsidiannotification.feature.settings.*;
import com.regstar.obsidiannotification.feature.sources.*;
import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class AboutActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemePreferences.apply(this);
        super.onCreate(savedInstanceState);
        buildUi();
    }

    private void buildUi() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(getColor(R.color.background));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(18), dp(20), dp(24));
        scrollView.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        root.addView(createTopBar(), fullWidthWithBottomMargin(dp(12)));
        root.addView(createHeroCard(), fullWidthWithBottomMargin(dp(14)));
        root.addView(createWhatIsSection(), fullWidthWithBottomMargin(dp(12)));
        root.addView(createFeaturesSection(), fullWidthWithBottomMargin(dp(12)));
        root.addView(createPrivacySection(), fullWidthWithBottomMargin(dp(12)));
        root.addView(createTechnicalSection(), fullWidthWithBottomMargin(dp(12)));
        root.addView(createFooter(), fullWidth());

        setContentView(scrollView);
    }

    private LinearLayout createTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);

        ImageButton back = createPlainIconButton(R.drawable.ic_arrow_back, "\u041d\u0430\u0437\u0430\u0434");
        back.setOnClickListener(view -> finish());
        bar.addView(back, new LinearLayout.LayoutParams(dp(40), dp(40)));

        TextView title = createText("\u041e \u043f\u0440\u0438\u043b\u043e\u0436\u0435\u043d\u0438\u0438", 22, R.color.text_primary, true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        );
        titleParams.setMargins(dp(10), 0, 0, 0);
        bar.addView(title, titleParams);
        return bar;
    }

    private LinearLayout createHeroCard() {
        LinearLayout card = createCardContainer();
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_drawer_transparent);
        card.addView(icon, new LinearLayout.LayoutParams(dp(72), dp(72)));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);

        TextView title = createText("ObsidianNotification", 24, R.color.text_primary, true);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(title, fullWidth());

        TextView subtitle = createText(
                "\u041b\u043e\u043a\u0430\u043b\u044c\u043d\u044b\u0435 markdown-\u043d\u0430\u043f\u043e\u043c\u0438\u043d\u0430\u043d\u0438\u044f \u0434\u043b\u044f Obsidian",
                14,
                R.color.text_secondary,
                false
        );
        subtitle.setMaxLines(2);
        subtitle.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(subtitle, fullWidthWithTopMargin(dp(4)));

        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        );
        textParams.setMargins(dp(14), 0, 0, 0);
        card.addView(texts, textParams);
        return card;
    }

    private LinearLayout createWhatIsSection() {
        LinearLayout card = createSectionCard("\u0427\u0442\u043e \u044d\u0442\u043e");
        TextView body = createParagraph(
                "ObsidianNotification \u2014 Android-\u043f\u0440\u0438\u043b\u043e\u0436\u0435\u043d\u0438\u0435 \u0434\u043b\u044f \u043b\u043e\u043a\u0430\u043b\u044c\u043d\u044b\u0445 \u043d\u0430\u043f\u043e\u043c\u0438\u043d\u0430\u043d\u0438\u0439 \u0438\u0437 markdown-\u0437\u0430\u043c\u0435\u0442\u043e\u043a Obsidian. \u041e\u043d\u043e \u0447\u0438\u0442\u0430\u0435\u0442 \u0437\u0430\u0434\u0430\u0447\u0438 \u0438\u0437 \u0432\u044b\u0431\u0440\u0430\u043d\u043d\u044b\u0445 \u0444\u0430\u0439\u043b\u043e\u0432 \u0438 \u043f\u0430\u043f\u043e\u043a, \u043f\u043e\u043a\u0430\u0437\u044b\u0432\u0430\u0435\u0442 \u0443\u0432\u0435\u0434\u043e\u043c\u043b\u0435\u043d\u0438\u044f \u0432 \u043d\u0443\u0436\u043d\u043e\u0435 \u0432\u0440\u0435\u043c\u044f \u0438 \u043f\u043e\u043c\u043e\u0433\u0430\u0435\u0442 \u0440\u0430\u0431\u043e\u0442\u0430\u0442\u044c \u0441 \u043d\u0438\u043c\u0438 \u043f\u0440\u044f\u043c\u043e \u043d\u0430 \u0442\u0435\u043b\u0435\u0444\u043e\u043d\u0435."
        );
        card.addView(body, fullWidth());
        return card;
    }

    private LinearLayout createFeaturesSection() {
        LinearLayout card = createSectionCard("\u0427\u0442\u043e \u0443\u043c\u0435\u0435\u0442");
        card.addView(createFeatureRow("\u0427\u0438\u0442\u0430\u0435\u0442 \u0437\u0430\u0434\u0430\u0447\u0438 \u0438\u0437 markdown-\u0444\u0430\u0439\u043b\u043e\u0432"), fullWidthWithBottomMargin(dp(8)));
        card.addView(createFeatureRow("\u0420\u0430\u0431\u043e\u0442\u0430\u0435\u0442 \u0441 \u043e\u0434\u043d\u0438\u043c \u0444\u0430\u0439\u043b\u043e\u043c, \u043d\u0435\u0441\u043a\u043e\u043b\u044c\u043a\u0438\u043c\u0438 \u0444\u0430\u0439\u043b\u0430\u043c\u0438 \u0438 \u043f\u0430\u043f\u043a\u0430\u043c\u0438"), fullWidthWithBottomMargin(dp(8)));
        card.addView(createFeatureRow("\u041f\u043e\u043a\u0430\u0437\u044b\u0432\u0430\u0435\u0442 \u043b\u043e\u043a\u0430\u043b\u044c\u043d\u044b\u0435 \u0443\u0432\u0435\u0434\u043e\u043c\u043b\u0435\u043d\u0438\u044f Android"), fullWidthWithBottomMargin(dp(8)));
        card.addView(createFeatureRow("\u041f\u043e\u0434\u0434\u0435\u0440\u0436\u0438\u0432\u0430\u0435\u0442 \u043f\u043e\u0432\u0442\u043e\u0440 \u0438 \u043f\u043e\u0432\u0442\u043e\u0440 \u0434\u043e \u0432\u044b\u043f\u043e\u043b\u043d\u0435\u043d\u0438\u044f"), fullWidthWithBottomMargin(dp(8)));
        card.addView(createFeatureRow("\u041f\u043e\u0437\u0432\u043e\u043b\u044f\u0435\u0442 \u0440\u0435\u0434\u0430\u043a\u0442\u0438\u0440\u043e\u0432\u0430\u0442\u044c \u0437\u0430\u0434\u0430\u0447\u0438 \u0438 \u0443\u043f\u0440\u0430\u0432\u043b\u044f\u0442\u044c \u043f\u043e\u0434\u0437\u0430\u0434\u0430\u0447\u0430\u043c\u0438"), fullWidth());
        return card;
    }

    private LinearLayout createPrivacySection() {
        LinearLayout card = createSectionCard("\u0414\u0430\u043d\u043d\u044b\u0435 \u0438 \u043f\u0440\u0438\u0432\u0430\u0442\u043d\u043e\u0441\u0442\u044c");
        TextView body = createParagraph(
                "\u041f\u0440\u0438\u043b\u043e\u0436\u0435\u043d\u0438\u0435 \u0440\u0430\u0431\u043e\u0442\u0430\u0435\u0442 \u0441 \u0432\u044b\u0431\u0440\u0430\u043d\u043d\u044b\u043c\u0438 \u043f\u043e\u043b\u044c\u0437\u043e\u0432\u0430\u0442\u0435\u043b\u0435\u043c markdown-\u0444\u0430\u0439\u043b\u0430\u043c\u0438 \u0438 \u0438\u0441\u043f\u043e\u043b\u044c\u0437\u0443\u0435\u0442 \u043b\u043e\u043a\u0430\u043b\u044c\u043d\u044b\u0435 \u0443\u0432\u0435\u0434\u043e\u043c\u043b\u0435\u043d\u0438\u044f Android. \u0414\u0430\u043d\u043d\u044b\u0435 \u0437\u0430\u0434\u0430\u0447 \u043e\u0441\u0442\u0430\u044e\u0442\u0441\u044f \u0432 \u0437\u0430\u043c\u0435\u0442\u043a\u0430\u0445 \u043f\u043e\u043b\u044c\u0437\u043e\u0432\u0430\u0442\u0435\u043b\u044f. \u041e\u0442\u0434\u0435\u043b\u044c\u043d\u044b\u0439 \u043e\u0431\u043b\u0430\u0447\u043d\u044b\u0439 \u0441\u0435\u0440\u0432\u0438\u0441 \u043f\u0440\u0438\u043b\u043e\u0436\u0435\u043d\u0438\u044f \u043d\u0435 \u0442\u0440\u0435\u0431\u0443\u0435\u0442\u0441\u044f."
        );
        card.addView(body, fullWidth());
        return card;
    }

    private LinearLayout createTechnicalSection() {
        LinearLayout card = createSectionCard("\u0422\u0435\u0445\u043d\u0438\u0447\u0435\u0441\u043a\u0430\u044f \u0438\u043d\u0444\u043e\u0440\u043c\u0430\u0446\u0438\u044f");
        card.addView(createInfoRow("\u0412\u0435\u0440\u0441\u0438\u044f \u043f\u0440\u0438\u043b\u043e\u0436\u0435\u043d\u0438\u044f", appVersionName()), fullWidthWithBottomMargin(dp(10)));
        card.addView(createInfoRow("\u0422\u0435\u043c\u0430", themeLabel()), fullWidthWithBottomMargin(dp(10)));
        card.addView(createInfoRow("\u0420\u0435\u0434\u0430\u043a\u0442\u0438\u0440\u043e\u0432\u0430\u043d\u0438\u0435", editModeLabel()), fullWidthWithBottomMargin(dp(10)));
        card.addView(createInfoRow("\u0424\u043e\u0440\u043c\u0430\u0442 \u0434\u0430\u0442\u044b", UserPreferences.getDateFormatLabel(this)), fullWidthWithBottomMargin(dp(10)));
        card.addView(createInfoRow("\u0424\u043e\u0440\u043c\u0430\u0442 \u0437\u0430\u0434\u0430\u0447", ""), fullWidthWithBottomMargin(dp(6)));

        TextView format = createText(TaskFormatSettings.load(this).formatForStatus(), 13, R.color.text_primary, false);
        format.setTypeface(Typeface.MONOSPACE);
        format.setBackground(createRoundedBackground(getColor(R.color.background), getColor(R.color.card_stroke), 8));
        format.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.addView(format, fullWidth());
        return card;
    }

    private TextView createFooter() {
        TextView footer = createText("ObsidianNotification v" + appVersionName(), 12, R.color.text_secondary, false);
        footer.setGravity(Gravity.CENTER_HORIZONTAL);
        footer.setPadding(0, dp(4), 0, 0);
        return footer;
    }

    private LinearLayout createSectionCard(String title) {
        LinearLayout card = createCardContainer();
        TextView titleView = createText(title, 16, R.color.text_primary, true);
        titleView.setPadding(0, 0, 0, dp(10));
        card.addView(titleView, fullWidth());
        return card;
    }

    private LinearLayout createFeatureRow(String text) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.TOP | Gravity.CENTER_VERTICAL);

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_check);
        icon.setColorFilter(getColor(R.color.chip_selected_text));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(18), dp(18));
        iconParams.setMargins(0, dp(2), 0, 0);
        row.addView(icon, iconParams);

        TextView textView = createText(text, 14, R.color.text_primary, false);
        textView.setMaxLines(3);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        );
        textParams.setMargins(dp(10), 0, 0, 0);
        row.addView(textView, textParams);
        return row;
    }

    private LinearLayout createInfoRow(String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);

        TextView labelView = createText(label, 12, R.color.text_secondary, false);
        row.addView(labelView, fullWidth());

        if (value != null && !value.trim().isEmpty()) {
            TextView valueView = createText(value, 14, R.color.text_primary, true);
            valueView.setPadding(0, dp(2), 0, 0);
            row.addView(valueView, fullWidth());
        }
        return row;
    }

    private TextView createParagraph(String text) {
        TextView body = createText(text, 14, R.color.text_primary, false);
        body.setLineSpacing(0f, 1.12f);
        return body;
    }

    private LinearLayout createCardContainer() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(createRoundedBackground(
                getColor(R.color.card_background),
                getColor(R.color.card_stroke),
                12
        ));
        return card;
    }

    private ImageButton createPlainIconButton(int iconRes, String description) {
        ImageButton button = new ImageButton(this);
        button.setImageResource(iconRes);
        button.setContentDescription(description);
        button.setColorFilter(getColor(R.color.text_secondary));
        button.setPadding(dp(9), dp(9), dp(9), dp(9));
        button.setBackground(createRoundedBackground(Color.TRANSPARENT, 0, 18));
        return button;
    }

    private TextView createText(String text, int sizeSp, int colorRes, boolean bold) {
        TextView textView = new TextView(this);
        textView.setText(text);
        textView.setTextSize(sizeSp);
        textView.setTextColor(getColor(colorRes));
        textView.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        return textView;
    }

    private GradientDrawable createRoundedBackground(int color, int strokeColor, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
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

    private LinearLayout.LayoutParams fullWidthWithBottomMargin(int bottomMargin) {
        LinearLayout.LayoutParams params = fullWidth();
        params.setMargins(0, 0, 0, bottomMargin);
        return params;
    }

    private LinearLayout.LayoutParams fullWidthWithTopMargin(int topMargin) {
        LinearLayout.LayoutParams params = fullWidth();
        params.setMargins(0, topMargin, 0, 0);
        return params;
    }

    private String themeLabel() {
        String mode = ThemePreferences.getThemeMode(this);
        if (ThemePreferences.MODE_LIGHT.equals(mode)) {
            return "\u0421\u0432\u0435\u0442\u043b\u0430\u044f";
        }
        if (ThemePreferences.MODE_DARK.equals(mode)) {
            return "\u0422\u0435\u043c\u043d\u0430\u044f";
        }
        return "\u0421\u0438\u0441\u0442\u0435\u043c\u043d\u0430\u044f";
    }

    private String editModeLabel() {
        return EditPreferences.MODE_MARKDOWN.equals(EditPreferences.getEditMode(this))
                ? "Markdown-\u0444\u0430\u0439\u043b"
                : "UI-\u0440\u0435\u0434\u0430\u043a\u0442\u043e\u0440";
    }

    private String appVersionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception exception) {
            return "1.2.0";
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}


