package com.regstar.obsidiannotification;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SourceManagementActivity extends Activity {
    private static final int REQUEST_REPLACE_NOTES = 4101;
    private static final int REQUEST_REPLACE_FOLDER = 4102;
    private static final int REQUEST_ADD_NOTES = 4103;
    private static final int REQUEST_ADD_FOLDER = 4104;
    private static final int REQUEST_CREATE_NOTE = 4105;
    private static final String NEW_NOTE_TEMPLATE = "## Задачи\n\n";

    private LinearLayout sourcesList;
    private TextView modeSummaryText;
    private TextView sourceSummaryText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemePreferences.apply(this);
        super.onCreate(savedInstanceState);
        buildUi();
        renderSources();
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) {
            return;
        }

        List<Uri> selectedUris = selectedUris(data);
        if (selectedUris.isEmpty()) {
            return;
        }
        for (Uri uri : selectedUris) {
            persistReadPermission(data, uri);
        }

        try {
            if (requestCode == REQUEST_CREATE_NOTE) {
                handleCreatedNote(selectedUris.get(0));
                return;
            }

            if (requestCode == REQUEST_REPLACE_NOTES) {
                NoteStore.saveNoteUris(this, selectedUris);
            } else if (requestCode == REQUEST_REPLACE_FOLDER) {
                NoteStore.saveFolderUri(this, selectedUris.get(0));
            } else if (requestCode == REQUEST_ADD_NOTES) {
                NoteStore.addNoteUris(this, selectedUris);
            } else if (requestCode == REQUEST_ADD_FOLDER) {
                NoteStore.addFolderUri(this, selectedUris.get(0));
            } else {
                return;
            }

            TaskSourceManager.useExternalStorage(this);
            OnboardingPreferences.markCompleted(this);
            resyncActiveSource();
            setResult(RESULT_OK);
            renderSources();
            Toast.makeText(this, "Внешний источник обновлён", Toast.LENGTH_SHORT).show();
        } catch (IOException exception) {
            ErrorLog.record(this, "Не удалось активировать внешний markdown-источник", exception);
            Toast.makeText(
                    this,
                    "Не удалось обновить источник: " + safeMessage(exception),
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void buildUi() {
        ScrollView scrollView = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(14), dp(16), dp(20));
        root.setBackgroundColor(getColor(R.color.background));
        scrollView.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        root.addView(createTopBar(), fullWidthWithBottomMargin());

        LinearLayout summaryCard = createCardContainer();
        summaryCard.addView(createText("Текущий режим", 15, R.color.text_primary, true), fullWidth());
        modeSummaryText = createText("", 13, R.color.text_secondary, false);
        modeSummaryText.setPadding(0, dp(5), 0, 0);
        summaryCard.addView(modeSummaryText, fullWidth());
        sourceSummaryText = createText("", 13, R.color.text_secondary, false);
        sourceSummaryText.setPadding(0, dp(8), 0, 0);
        summaryCard.addView(sourceSummaryText, fullWidth());
        root.addView(summaryCard, fullWidthWithBottomMargin());

        root.addView(createSectionTitle("Режим хранения"), fullWidth());
        root.addView(createChoiceCard(
                "Встроенное хранилище",
                "Локальные markdown-файлы внутри приложения. Подходит для быстрого старта без внешней папки.",
                TaskSourceManager.getStorageMode(this) == TaskStorageMode.INTERNAL_MARKDOWN_STORAGE,
                this::confirmSwitchToInternal
        ), fullWidthWithBottomMargin());
        root.addView(createChoiceCard(
                "Внешняя папка / Obsidian",
                "Работа с уже существующими markdown-файлами и папками через Android picker.",
                TaskSourceManager.getStorageMode(this) == TaskStorageMode.EXTERNAL_MARKDOWN_STORAGE,
                this::activateExternalMode
        ), fullWidthWithBottomMargin());

        TextView migrationHint = createText(
                "Смена режима пока не переносит задачи автоматически. Для миграции markdown-файлы нужно копировать вручную.",
                13,
                R.color.text_secondary,
                false
        );
        migrationHint.setPadding(0, 0, 0, dp(8));
        root.addView(migrationHint, fullWidthWithBottomMargin());

        root.addView(createSectionTitle("Внешние markdown-источники"), fullWidth());
        root.addView(createActionCard(
                "Заменить заметками",
                "Выбрать один или несколько markdown-файлов как текущий внешний источник.",
                () -> openNotePicker(REQUEST_REPLACE_NOTES)
        ), fullWidthWithBottomMargin());
        root.addView(createActionCard(
                "Заменить папкой",
                "Выбрать папку Obsidian vault или Syncthing-папку с markdown-файлами.",
                () -> openFolderPicker(REQUEST_REPLACE_FOLDER)
        ), fullWidthWithBottomMargin());
        root.addView(createActionCard(
                "Добавить заметку",
                "Подключить ещё один markdown-файл к уже сохранённым внешним источникам.",
                () -> openNotePicker(REQUEST_ADD_NOTES)
        ), fullWidthWithBottomMargin());
        root.addView(createActionCard(
                "Создать внешний файл",
                "Создать новый markdown-файл через Android picker и сразу подключить его.",
                this::openNoteCreator
        ), fullWidthWithBottomMargin());
        root.addView(createActionCard(
                "Добавить папку",
                "Подключить дополнительную папку с markdown-файлами.",
                () -> openFolderPicker(REQUEST_ADD_FOLDER)
        ), fullWidthWithBottomMargin());

        Button clearButton = createSecondaryButton("Очистить внешние подключения");
        clearButton.setOnClickListener(view -> clearSources());
        root.addView(clearButton, fullWidthWithBottomMargin());

        root.addView(createSectionTitle("Сохранённые внешние источники"), fullWidth());
        sourcesList = new LinearLayout(this);
        sourcesList.setOrientation(LinearLayout.VERTICAL);
        root.addView(sourcesList, fullWidth());

        setContentView(scrollView);
    }

    private LinearLayout createTopBar() {
        LinearLayout appBar = new LinearLayout(this);
        appBar.setOrientation(LinearLayout.HORIZONTAL);
        appBar.setGravity(Gravity.CENTER_VERTICAL);

        ImageButton back = createIconButton(R.drawable.ic_arrow_back, "Назад");
        back.setOnClickListener(view -> finish());
        appBar.addView(back, new LinearLayout.LayoutParams(dp(40), dp(40)));

        TextView title = createText("Источник задач", 21, R.color.text_primary, true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        );
        titleParams.setMargins(dp(10), 0, 0, 0);
        appBar.addView(title, titleParams);
        return appBar;
    }

    private void renderSources() {
        TaskStorageMode mode = TaskSourceManager.getStorageMode(this);
        if (modeSummaryText != null) {
            modeSummaryText.setText(
                    TaskSourceManager.storageModeLabel(this)
                            + " · файлов/источников: "
                            + TaskSourceManager.activeSourceCount(this)
                            + " · запись: "
                            + (TaskSourceManager.canWriteActiveSource(this) ? "доступна" : "недоступна")
            );
        }
        if (sourceSummaryText != null) {
            if (mode == TaskStorageMode.INTERNAL_MARKDOWN_STORAGE) {
                sourceSummaryText.setText(
                        "Источник: " + TaskSourceManager.activeSourceLabel(this)
                                + "\nПапка: " + TaskSourceManager.internalFolderSummary(this)
                );
            } else {
                sourceSummaryText.setText("Источник: " + TaskSourceManager.activeSourceLabel(this));
            }
        }

        if (sourcesList == null) {
            return;
        }
        sourcesList.removeAllViews();
        List<NoteStore.NoteSource> sources = NoteStore.getSavedSources(this);
        if (sources.isEmpty()) {
            TextView empty = createText(
                    "Внешние markdown-источники ещё не подключены.",
                    14,
                    R.color.text_secondary,
                    false
            );
            empty.setPadding(0, dp(6), 0, 0);
            sourcesList.addView(empty, fullWidthWithBottomMargin());
            return;
        }

        for (NoteStore.NoteSource source : sources) {
            sourcesList.addView(createSourceItem(source), fullWidthWithBottomMargin());
        }
    }

    private LinearLayout createSourceItem(NoteStore.NoteSource source) {
        LinearLayout card = createCardContainer();
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_file);
        icon.setColorFilter(getColor(R.color.text_secondary));
        row.addView(icon, new LinearLayout.LayoutParams(dp(24), dp(24)));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(createText(compactName(NoteStore.sourceDisplayName(this, source.getUri())), 15, R.color.text_primary, true), fullWidth());
        String type = NoteStore.SOURCE_FOLDER.equals(source.getType()) ? "Папка" : "Файл";
        TextView meta = createText(type + " · " + source.getUri(), 12, R.color.text_secondary, false);
        meta.setSingleLine(true);
        meta.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(meta, fullWidthWithTopMargin(dp(3)));
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        );
        textParams.setMargins(dp(12), 0, 0, 0);
        row.addView(texts, textParams);
        card.addView(row, fullWidth());
        return card;
    }

    private void confirmSwitchToInternal() {
        if (TaskSourceManager.getStorageMode(this) == TaskStorageMode.INTERNAL_MARKDOWN_STORAGE) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Переключить источник")
                .setMessage(TaskSourceManager.switchWithoutMigrationWarning(TaskStorageMode.INTERNAL_MARKDOWN_STORAGE))
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Переключить", (dialog, which) -> switchToInternal())
                .show();
    }

    private void switchToInternal() {
        try {
            TaskSourceManager.useInternalStorage(this);
            OnboardingPreferences.markCompleted(this);
            resyncActiveSource();
            setResult(RESULT_OK);
            renderSources();
            Toast.makeText(this, "Приложение переключено на встроенное хранилище", Toast.LENGTH_SHORT).show();
        } catch (IOException exception) {
            ErrorLog.record(this, "Не удалось включить встроенное хранилище", exception);
            Toast.makeText(
                    this,
                    "Не удалось включить встроенное хранилище: " + safeMessage(exception),
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void activateExternalMode() {
        if (!TaskSourceManager.hasExternalSources(this)) {
            openFolderPicker(REQUEST_REPLACE_FOLDER);
            return;
        }
        TaskSourceManager.useExternalStorage(this);
        OnboardingPreferences.markCompleted(this);
        resyncActiveSource();
        setResult(RESULT_OK);
        renderSources();
        Toast.makeText(this, "Внешний источник активирован", Toast.LENGTH_SHORT).show();
    }

    private LinearLayout createActionCard(String title, String subtitle, Runnable action) {
        LinearLayout card = createCardContainer();
        card.setOnClickListener(view -> action.run());

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(createText(title, 15, R.color.text_primary, true), fullWidth());
        texts.addView(createText(subtitle, 13, R.color.text_secondary, false), fullWidthWithTopMargin(dp(3)));
        row.addView(texts, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        ));

        TextView chevron = createText("›", 24, R.color.text_secondary, false);
        chevron.setGravity(Gravity.CENTER);
        row.addView(chevron, new LinearLayout.LayoutParams(dp(32), dp(42)));
        card.addView(row, fullWidth());
        return card;
    }

    private LinearLayout createChoiceCard(
            String title,
            String subtitle,
            boolean selected,
            Runnable action
    ) {
        LinearLayout card = createCardContainer();
        card.setBackground(createRoundedBackground(
                getColor(selected ? R.color.chip_selected_background : R.color.card_background),
                getColor(selected ? R.color.chip_selected_stroke : R.color.card_stroke),
                8
        ));
        card.setOnClickListener(view -> action.run());

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(createText(title, 15, R.color.text_primary, true), fullWidth());
        texts.addView(createText(subtitle, 13, R.color.text_secondary, false), fullWidthWithTopMargin(dp(3)));
        row.addView(texts, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        ));

        TextView marker = createText(selected ? "Активно" : "", 12, R.color.text_secondary, false);
        row.addView(marker, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        card.addView(row, fullWidth());
        return card;
    }

    @SuppressWarnings("deprecation")
    private void openNotePicker(int requestCode) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "text/markdown",
                "text/plain",
                "application/octet-stream"
        });
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(intent, requestCode);
    }

    @SuppressWarnings("deprecation")
    private void openNoteCreator() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/markdown");
        intent.putExtra(Intent.EXTRA_TITLE, "tasks.md");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_CREATE_NOTE);
    }

    private void handleCreatedNote(Uri uri) throws IOException {
        NoteStore.writeMarkdown(this, uri, NEW_NOTE_TEMPLATE);
        NoteStore.addNoteUris(this, Collections.singletonList(uri));
        TaskSourceManager.useExternalStorage(this);
        OnboardingPreferences.markCompleted(this);
        resyncActiveSource();
        setResult(RESULT_OK);
        renderSources();
        Toast.makeText(this, "Файл создан и подключён как внешний источник", Toast.LENGTH_SHORT).show();
    }

    @SuppressWarnings("deprecation")
    private void openFolderPicker(int requestCode) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        startActivityForResult(intent, requestCode);
    }

    private void persistReadPermission(Intent data, Uri selectedUri) {
        try {
            int persistableFlags = data.getFlags()
                    & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            if (persistableFlags == 0) {
                persistableFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
            }
            getContentResolver().takePersistableUriPermission(selectedUri, persistableFlags);
        } catch (SecurityException ignored) {
            // Some providers grant only temporary access.
        }
    }

    private List<Uri> selectedUris(Intent data) {
        List<Uri> uris = new ArrayList<>();
        ClipData clipData = data.getClipData();
        if (clipData != null) {
            for (int index = 0; index < clipData.getItemCount(); index++) {
                Uri uri = clipData.getItemAt(index).getUri();
                if (uri != null) {
                    uris.add(uri);
                }
            }
        }

        Uri dataUri = data.getData();
        if (dataUri != null && !uris.contains(dataUri)) {
            uris.add(dataUri);
        }
        return uris;
    }

    private void clearSources() {
        new AlertDialog.Builder(this)
                .setTitle("Очистить внешние источники")
                .setMessage("Внешние подключения будут удалены. Приложение переключится на встроенное хранилище.")
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Очистить", (dialog, which) -> {
                    NoteStore.clearSources(this);
                    try {
                        TaskSourceManager.useInternalStorage(this);
                        OnboardingPreferences.markCompleted(this);
                        resyncActiveSource();
                        setResult(RESULT_OK);
                        renderSources();
                        Toast.makeText(
                                this,
                                "Внешние подключения очищены, встроенное хранилище снова активно",
                                Toast.LENGTH_SHORT
                        ).show();
                    } catch (IOException exception) {
                        ErrorLog.record(this, "Не удалось подготовить встроенное хранилище после очистки источников", exception);
                        Toast.makeText(
                                this,
                                "Внешние подключения очищены, но встроенное хранилище не удалось подготовить: "
                                        + safeMessage(exception),
                                Toast.LENGTH_LONG
                        ).show();
                    }
                })
                .show();
    }

    private void resyncActiveSource() {
        NoteChangeMonitor.ensureScheduled(this);
        NoteChangeMonitor.syncNow(this, true);
    }

    private String safeMessage(Exception exception) {
        String message = exception == null ? null : exception.getMessage();
        return message == null || message.trim().isEmpty()
                ? "проверьте доступ к выбранному источнику"
                : message;
    }

    private TextView createSectionTitle(String text) {
        TextView title = createText(text, 17, R.color.text_primary, true);
        title.setPadding(0, dp(14), 0, dp(8));
        return title;
    }

    private LinearLayout createCardContainer() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(createRoundedBackground(
                getColor(R.color.card_background),
                getColor(R.color.card_stroke),
                8
        ));
        return card;
    }

    private ImageButton createIconButton(int iconRes, String description) {
        ImageButton button = new ImageButton(this);
        button.setImageResource(iconRes);
        button.setContentDescription(description);
        button.setColorFilter(getColor(R.color.text_primary));
        button.setPadding(dp(10), dp(10), dp(10), dp(10));
        button.setBackground(createRoundedBackground(
                getColor(R.color.icon_button_background),
                0,
                18
        ));
        return button;
    }

    private Button createSecondaryButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextColor(getColor(R.color.secondary_button_text));
        button.setBackground(createRoundedBackground(
                getColor(R.color.secondary_button_background),
                getColor(R.color.card_stroke),
                8
        ));
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

    private String compactName(String rawName) {
        if (rawName == null || rawName.trim().isEmpty()) {
            return "не выбран";
        }
        String value = rawName.trim();
        int queryIndex = value.indexOf('?');
        if (queryIndex >= 0) {
            value = value.substring(0, queryIndex);
        }
        int encodedSlash = Math.max(value.lastIndexOf("%2F"), value.lastIndexOf("%2f"));
        if (encodedSlash >= 0 && encodedSlash + 3 < value.length()) {
            value = value.substring(encodedSlash + 3);
        }
        int slash = Math.max(value.lastIndexOf('/'), value.lastIndexOf('\\'));
        if (slash >= 0 && slash + 1 < value.length()) {
            value = value.substring(slash + 1);
        }
        int colon = value.lastIndexOf(':');
        if (colon >= 0 && colon + 1 < value.length()) {
            value = value.substring(colon + 1);
        }
        return value.isEmpty() ? rawName : value;
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

    private LinearLayout.LayoutParams fullWidthWithTopMargin(int topMargin) {
        LinearLayout.LayoutParams params = fullWidth();
        params.setMargins(0, topMargin, 0, 0);
        return params;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
