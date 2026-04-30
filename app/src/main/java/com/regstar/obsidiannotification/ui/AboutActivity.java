package com.regstar.obsidiannotification.ui;

import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.regstar.obsidiannotification.R;
import com.regstar.obsidiannotification.core.source.TaskSourceManager;
import com.regstar.obsidiannotification.prefs.ThemePreferences;

/**
 * Full about screen with product overview and technical information.
 */
public final class AboutActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(getColor(R.color.background));
        root.setPadding(dp(16), dp(14), dp(16), dp(20));

        root.addView(createTopBar(), fullWidth());
        root.addView(createTopDivider(), fullWidthWithTopMargin(dp(6)));
        root.addView(createHeroCard(), fullWidthWithTopMargin(dp(10)));
        root.addView(createSectionCard(
                getString(R.string.about_section_what_title),
                getString(R.string.about_section_what_body)
        ), fullWidthWithTopMargin(dp(10)));
        root.addView(createFeaturesCard(), fullWidthWithTopMargin(dp(10)));
        root.addView(createSectionCard(
                getString(R.string.about_section_privacy_title),
                getString(R.string.about_section_privacy_body)
        ), fullWidthWithTopMargin(dp(10)));
        root.addView(createTechnicalCard(), fullWidthWithTopMargin(dp(10)));

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
        back.setContentDescription(getString(R.string.about_back));
        back.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        back.setOnClickListener(view -> finish());
        bar.addView(back, new LinearLayout.LayoutParams(dp(40), dp(40)));

        TextView title = createText(getString(R.string.main_nav_about), 21, R.color.text_primary, true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        );
        titleParams.setMargins(dp(8), 0, 0, 0);
        bar.addView(title, titleParams);
        return bar;
    }

    private LinearLayout createHeroCard() {
        LinearLayout card = createCard();

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_drawer_transparent);
        row.addView(icon, new LinearLayout.LayoutParams(dp(56), dp(56)));

        LinearLayout textColumn = new LinearLayout(this);
        textColumn.setOrientation(LinearLayout.VERTICAL);
        TextView title = createText(getString(R.string.app_name), 20, R.color.text_primary, true);
        textColumn.addView(title, fullWidth());
        TextView subtitle = createText(
                getString(R.string.about_hero_subtitle),
                13,
                R.color.text_secondary,
                false
        );
        subtitle.setPadding(0, dp(4), 0, 0);
        textColumn.addView(subtitle, fullWidth());

        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        );
        textParams.setMargins(dp(12), 0, 0, 0);
        row.addView(textColumn, textParams);
        card.addView(row, fullWidth());
        return card;
    }

    private LinearLayout createFeaturesCard() {
        LinearLayout card = createCard();
        card.addView(createText(getString(R.string.about_section_features_title), 16, R.color.text_primary, true), fullWidth());
        addFeatureRow(card, getString(R.string.about_feature_read_markdown));
        addFeatureRow(card, getString(R.string.about_feature_sources));
        addFeatureRow(card, getString(R.string.about_feature_notifications));
        addFeatureRow(card, getString(R.string.about_feature_repeat));
        addFeatureRow(card, getString(R.string.about_feature_editing));
        return card;
    }

    private void addFeatureRow(LinearLayout card, String text) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);

        ImageView bullet = new ImageView(this);
        bullet.setImageResource(R.drawable.ic_check);
        bullet.setColorFilter(getColor(R.color.accent));
        row.addView(bullet, new LinearLayout.LayoutParams(dp(16), dp(16)));

        TextView item = createText(text, 13, R.color.text_secondary, false);
        LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        );
        itemParams.setMargins(dp(8), 0, 0, 0);
        row.addView(item, itemParams);
        card.addView(row, fullWidthWithTopMargin(dp(8)));
    }

    private LinearLayout createTechnicalCard() {
        LinearLayout card = createCard();
        card.addView(createText(getString(R.string.about_section_tech_title), 16, R.color.text_primary, true), fullWidth());
        card.addView(createInfoRow(
                getString(R.string.about_info_version),
                appVersionName()
        ), fullWidthWithTopMargin(dp(10)));
        card.addView(createInfoRow(
                getString(R.string.about_info_storage_mode),
                TaskSourceManager.storageModeLabel(this)
        ), fullWidthWithTopMargin(dp(8)));
        card.addView(createInfoRow(
                getString(R.string.about_info_theme),
                themeModeLabel()
        ), fullWidthWithTopMargin(dp(8)));

        TextView syntaxTitle = createText(getString(R.string.about_info_syntax), 12, R.color.text_primary, true);
        syntaxTitle.setPadding(0, dp(10), 0, 0);
        card.addView(syntaxTitle, fullWidth());

        TextView syntax = createText(getString(R.string.about_info_syntax_value), 12, R.color.text_primary, false);
        syntax.setTypeface(Typeface.MONOSPACE);
        syntax.setBackground(createRoundedBackground(getColor(R.color.background), getColor(R.color.card_stroke), 10));
        syntax.setPadding(dp(10), dp(10), dp(10), dp(10));
        card.addView(syntax, fullWidthWithTopMargin(dp(6)));
        return card;
    }

    private LinearLayout createInfoRow(String key, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.addView(createText(key, 13, R.color.text_secondary, false), new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        ));
        TextView valueView = createText(value, 13, R.color.text_primary, true);
        valueView.setMaxLines(2);
        row.addView(valueView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        return row;
    }

    private LinearLayout createSectionCard(String title, String body) {
        LinearLayout card = createCard();
        card.addView(createText(title, 16, R.color.text_primary, true), fullWidth());
        TextView bodyView = createText(body, 13, R.color.text_secondary, false);
        bodyView.setPadding(0, dp(8), 0, 0);
        card.addView(bodyView, fullWidth());
        return card;
    }

    private LinearLayout createTopDivider() {
        LinearLayout divider = new LinearLayout(this);
        divider.setOrientation(LinearLayout.VERTICAL);
        divider.setMinimumHeight(dp(1));
        divider.setBackground(createRoundedBackground(getColor(R.color.card_stroke), 0, 1));
        return divider;
    }

    private LinearLayout createCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(createRoundedBackground(getColor(R.color.card_background), getColor(R.color.card_stroke), 16));
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        return card;
    }

    private String appVersionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception exception) {
            return "unknown";
        }
    }

    private String themeModeLabel() {
        String mode = ThemePreferences.getThemeMode(this);
        if (ThemePreferences.MODE_DARK.equals(mode)) {
            return getString(R.string.about_theme_dark);
        }
        if (ThemePreferences.MODE_LIGHT.equals(mode)) {
            return getString(R.string.about_theme_light);
        }
        return getString(R.string.about_theme_system);
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
