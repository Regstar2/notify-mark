package com.regstar.obsidiannotification;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public final class SourceManagementActivity extends Activity {
    private static final int REQUEST_REPLACE_NOTES = 4101;
    private static final int REQUEST_REPLACE_FOLDER = 4102;
    private static final int REQUEST_ADD_NOTES = 4103;
    private static final int REQUEST_ADD_FOLDER = 4104;

    private LinearLayout sourcesList;
    private TextView summaryText;

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

        NoteChangeMonitor.ensureScheduled(this);
        NoteChangeMonitor.syncNow(this, true);
        setResult(RESULT_OK);
        renderSources();
        Toast.makeText(this, "Источники обновлены", Toast.LENGTH_SHORT).show();
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
        summaryCard.addView(createText("Текущий источник", 15, R.color.text_primary, true), fullWidth());
        summaryText = createText("", 13, R.color.text_secondary, false);
        summaryText.setPadding(0, dp(5), 0, 0);
        summaryCard.addView(summaryText, fullWidth());
        root.addView(summaryCard, fullWidthWithBottomMargin());

        root.addView(createActionCard(
                "Выбрать заметки",
                "Заменить текущий набор одной или несколькими markdown-заметками.",
                () -> openNotePicker(REQUEST_REPLACE_NOTES)
        ), fullWidthWithBottomMargin());
        root.addView(createActionCard(
                "Выбрать папку",
                "Заменить текущий источник папкой с markdown-файлами.",
                () -> openFolderPicker(REQUEST_REPLACE_FOLDER)
        ), fullWidthWithBottomMargin());
        root.addView(createActionCard(
                "Добавить заметку",
                "Добавить файл к текущим источникам.",
                () -> openNotePicker(REQUEST_ADD_NOTES)
        ), fullWidthWithBottomMargin());
        root.addView(createActionCard(
                "Добавить папку",
                "Добавить папку к текущим источникам.",
                () -> openFolderPicker(REQUEST_ADD_FOLDER)
        ), fullWidthWithBottomMargin());

        Button clearButton = createSecondaryButton("Очистить источники");
        clearButton.setOnClickListener(view -> clearSources());
        root.addView(clearButton, fullWidthWithBottomMargin());

        root.addView(createSectionTitle("Источники"), fullWidth());
        sourcesList = new LinearLayout(this);
        sourcesList.setOrientation(LinearLayout.VERTICAL);
        root.addView(sourcesList, fullWidth());

        setContentView(scrollView);
    }

    private LinearLayout createTopBar() {
        LinearLayout appBar = new LinearLayout(this);
        appBar.setOrientation(LinearLayout.HORIZONTAL);
        appBar.setGravity(android.view.Gravity.CENTER_VERTICAL);

        ImageButton back = createIconButton(R.drawable.ic_arrow_back, "Назад");
        back.setOnClickListener(view -> finish());
        appBar.addView(back, new LinearLayout.LayoutParams(dp(40), dp(40)));

        TextView title = createText("Источники", 21, R.color.text_primary, true);
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
        List<NoteStore.NoteSource> sources = NoteStore.getSavedSources(this);
        if (summaryText != null) {
            summaryText.setText(sources.isEmpty()
                    ? "Источник не выбран"
                    : sources.size() + " источн. · запись "
                    + (NoteStore.canWriteSavedSource(this) ? "доступна" : "недоступна"));
        }

        if (sourcesList == null) {
            return;
        }
        sourcesList.removeAllViews();
        if (sources.isEmpty()) {
            TextView empty = createText("Выберите заметку или папку с markdown-файлами.", 14, R.color.text_secondary, false);
            empty.setPadding(0, dp(6), 0, 0);
            sourcesList.addView(empty, fullWidthWithBottomMargin());
            return;
        }

        for (NoteStore.NoteSource source : sources) {
            LinearLayout card = createSourceItem(source);
            sourcesList.addView(card, fullWidthWithBottomMargin());
        }
    }

    private LinearLayout createSourceItem(NoteStore.NoteSource source) {
        LinearLayout card = createCardContainer();
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_file);
        icon.setColorFilter(getColor(R.color.text_secondary));
        row.addView(icon, new LinearLayout.LayoutParams(dp(24), dp(24)));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        String name = compactName(NoteStore.sourceDisplayName(this, source.getUri()));
        texts.addView(createText(name, 15, R.color.text_primary, true), fullWidth());
        String type = NoteStore.SOURCE_FOLDER.equals(source.getType()) ? "папка" : "заметка";
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

    private LinearLayout createActionCard(String title, String subtitle, Runnable action) {
        LinearLayout card = createCardContainer();
        card.setOnClickListener(view -> action.run());

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);

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
        chevron.setGravity(android.view.Gravity.CENTER);
        row.addView(chevron, new LinearLayout.LayoutParams(dp(32), dp(42)));
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
                    & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            if (persistableFlags == 0) {
                persistableFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
            }
            getContentResolver().takePersistableUriPermission(selectedUri, persistableFlags);
        } catch (SecurityException ignored) {
            // Some providers grant only temporary read access.
        }
    }

    private List<Uri> selectedUris(Intent data) {
        List<Uri> uris = new ArrayList<>();
        ClipData clipData = data.getClipData();
        if (clipData != null) {
            for (int i = 0; i < clipData.getItemCount(); i++) {
                Uri uri = clipData.getItemAt(i).getUri();
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
        NoteStore.clearSources(this);
        ReminderScheduler.cancelScheduled(this);
        NoteChangeMonitor.cancel(this);
        setResult(RESULT_OK);
        renderSources();
        Toast.makeText(this, "Источники очищены", Toast.LENGTH_SHORT).show();
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
