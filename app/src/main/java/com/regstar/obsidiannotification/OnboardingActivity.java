package com.regstar.obsidiannotification;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class OnboardingActivity extends Activity {
    private static final int REQUEST_SOURCE_MANAGEMENT = 5101;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemePreferences.apply(this);
        super.onCreate(savedInstanceState);
        buildUi();
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_SOURCE_MANAGEMENT && resultCode == RESULT_OK) {
            finishOnboarding();
        }
    }

    private void buildUi() {
        ScrollView scrollView = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(22), dp(20), dp(20));
        root.setBackgroundColor(getColor(R.color.background));
        scrollView.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_launcher_original);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(76), dp(76));
        iconParams.setMargins(0, 0, 0, dp(14));
        root.addView(icon, iconParams);

        root.addView(createText("ObsidianNotification", 24, R.color.text_primary, true), fullWidth());
        TextView subtitle = createText(
                "Локальные напоминания из markdown-заметок Obsidian.",
                15,
                R.color.text_secondary,
                false
        );
        subtitle.setPadding(0, dp(6), 0, dp(18));
        root.addView(subtitle, fullWidth());

        root.addView(createInfoCard(
                "1. Выберите источник",
                "Можно выбрать одну заметку, несколько markdown-файлов или папку vault. Для Syncthing выбирайте локальную папку, которая уже синхронизируется на телефоне."
        ), fullWidthWithBottomMargin());
        root.addView(createInfoCard(
                "2. Пишите задачи в markdown",
                "- [ ] Купить лекарство @due(2026-04-20 19:00) @repeatUntilDone(15m)\nPlain reminder @due(19:10) @group(home)"
        ), fullWidthWithBottomMargin());
        root.addView(createInfoCard(
                "3. Разрешите уведомления",
                "Android 13+ спросит разрешение на уведомления. Для максимально точных напоминаний Android может попросить отдельное разрешение на exact alarms."
        ), fullWidthWithBottomMargin());

        Button sourceButton = createPrimaryButton("Выбрать источник");
        sourceButton.setOnClickListener(view -> openSourceManagement());
        root.addView(sourceButton, buttonParams());

        Button continueButton = createSecondaryButton("Продолжить без выбора");
        continueButton.setOnClickListener(view -> finishOnboarding());
        root.addView(continueButton, buttonParams());

        setContentView(scrollView);
    }

    @SuppressWarnings("deprecation")
    private void openSourceManagement() {
        startActivityForResult(new Intent(this, SourceManagementActivity.class), REQUEST_SOURCE_MANAGEMENT);
    }

    private void finishOnboarding() {
        OnboardingPreferences.markCompleted(this);
        setResult(RESULT_OK);
        finish();
    }

    private LinearLayout createInfoCard(String title, String body) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(createRoundedBackground(
                getColor(R.color.card_background),
                getColor(R.color.card_stroke),
                8
        ));
        card.addView(createText(title, 16, R.color.text_primary, true), fullWidth());
        TextView bodyView = createText(body, 14, R.color.text_secondary, false);
        bodyView.setPadding(0, dp(6), 0, 0);
        if (body.contains("@due(")) {
            bodyView.setTypeface(Typeface.MONOSPACE);
        }
        card.addView(bodyView, fullWidth());
        return card;
    }

    private Button createPrimaryButton(String text) {
        Button button = createBaseButton(text);
        button.setTextColor(getColor(R.color.primary_button_text));
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setBackground(createRoundedBackground(
                getColor(R.color.primary_button_background),
                0,
                8
        ));
        return button;
    }

    private Button createSecondaryButton(String text) {
        Button button = createBaseButton(text);
        button.setTextColor(getColor(R.color.secondary_button_text));
        button.setBackground(createRoundedBackground(
                getColor(R.color.secondary_button_background),
                getColor(R.color.card_stroke),
                8
        ));
        return button;
    }

    private Button createBaseButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setMinHeight(0);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
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

    private LinearLayout.LayoutParams fullWidthWithBottomMargin() {
        LinearLayout.LayoutParams params = fullWidth();
        params.setMargins(0, 0, 0, dp(12));
        return params;
    }

    private LinearLayout.LayoutParams buttonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(48)
        );
        params.setMargins(0, dp(6), 0, dp(8));
        return params;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
