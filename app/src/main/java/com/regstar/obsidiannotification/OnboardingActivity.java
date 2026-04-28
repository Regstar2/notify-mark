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
import android.widget.Toast;

import java.io.IOException;

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
            if (TaskSourceManager.hasExternalSources(this)) {
                TaskSourceManager.useExternalStorage(this);
                finishOnboarding();
            }
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
                "Один markdown-движок для быстрых встроенных задач и для внешней папки Obsidian.",
                15,
                R.color.text_secondary,
                false
        );
        subtitle.setPadding(0, dp(6), 0, dp(18));
        root.addView(subtitle, fullWidth());

        root.addView(createInfoCard(
                "Встроенное хранилище",
                "Локальные markdown-файлы внутри приложения. Подходит для быстрого старта без выбора внешней папки."
        ), fullWidthWithBottomMargin());

        Button internalButton = createPrimaryButton("Начать быстро");
        internalButton.setOnClickListener(view -> useInternalStorage());
        root.addView(internalButton, buttonParams());

        root.addView(createInfoCard(
                "Внешние markdown-файлы",
                "Выбранные файлы и папки через Android picker. Подходит для Obsidian, Syncthing и обычных markdown-файлов."
        ), fullWidthWithBottomMargin());

        Button externalButton = createSecondaryButton("Подключить папку");
        externalButton.setOnClickListener(view -> openSourceManagement());
        root.addView(externalButton, buttonParams());

        TextView note = createText(
                "Сменить режим хранения можно позже в настройках. Переключение пока не переносит задачи автоматически.",
                13,
                R.color.text_secondary,
                false
        );
        note.setPadding(0, dp(10), 0, 0);
        root.addView(note, fullWidth());

        setContentView(scrollView);
    }

    @SuppressWarnings("deprecation")
    private void openSourceManagement() {
        startActivityForResult(new Intent(this, SourceManagementActivity.class), REQUEST_SOURCE_MANAGEMENT);
    }

    private void useInternalStorage() {
        try {
            TaskSourceManager.useInternalStorage(this);
            finishOnboarding();
        } catch (IOException exception) {
            ErrorLog.record(this, "Не удалось подготовить встроенное хранилище", exception);
            Toast.makeText(
                    this,
                    "Не удалось подготовить встроенное хранилище: " + safeMessage(exception),
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void finishOnboarding() {
        OnboardingPreferences.markCompleted(this);
        setResult(RESULT_OK);
        finish();
    }

    private String safeMessage(Exception exception) {
        String message = exception == null ? null : exception.getMessage();
        return message == null || message.trim().isEmpty()
                ? "проверьте доступ к памяти приложения"
                : message;
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
