package com.regstar.obsidiannotification.ui;

import com.regstar.obsidiannotification.R;

import com.regstar.obsidiannotification.core.reminders.*;
import com.regstar.obsidiannotification.core.source.*;
import com.regstar.obsidiannotification.core.tasks.*;
import com.regstar.obsidiannotification.debug.*;
import com.regstar.obsidiannotification.prefs.*;
import com.regstar.obsidiannotification.support.ErrorLog;

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
import android.view.WindowInsets;
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

/**
 * Management screen for task storage mode and external markdown sources.
 *
 * <p>The screen is intentionally about selection and labeling only. SAF
 * persistence and document IO stay in the source/store layer.</p>
 */
public final class SourceManagementActivity extends Activity {
    private static final int REQUEST_REPLACE_NOTES = 4101;
    private static final int REQUEST_REPLACE_FOLDER = 4102;
    private static final int REQUEST_ADD_NOTES = 4103;
    private static final int REQUEST_ADD_FOLDER = 4104;
    private static final int REQUEST_CREATE_NOTE = 4105;
    private static final String NEW_NOTE_TEMPLATE = "## Р В РІР‚вЂќР В Р’В°Р В РўвЂР В Р’В°Р РЋРІР‚РЋР В РЎвЂ\n\n";

    private LinearLayout sourcesList;
    private TextView modeHeadlineText;
    private TextView modeMetaText;
    private TextView modeTechnicalText;
    private TextView externalSourcesHintText;
    private LinearLayout internalModeCard;
    private LinearLayout externalModeCard;
    private TextView internalModeMarker;
    private TextView externalModeMarker;

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
            Toast.makeText(this, "Р В РІР‚в„ўР В Р вЂ¦Р В Р’ВµР РЋРІвЂљВ¬Р В Р вЂ¦Р В РЎвЂР В РІвЂћвЂ“ Р В РЎвЂР РЋР С“Р РЋРІР‚С™Р В РЎвЂўР РЋРІР‚РЋР В Р вЂ¦Р В РЎвЂР В РЎвЂќ Р В РЎвЂўР В Р’В±Р В Р вЂ¦Р В РЎвЂўР В Р вЂ Р В Р’В»Р РЋРІР‚ВР В Р вЂ¦", Toast.LENGTH_SHORT).show();
        } catch (IOException exception) {
            ErrorLog.record(this, "Р В РЎСљР В Р’Вµ Р РЋРЎвЂњР В РўвЂР В Р’В°Р В Р’В»Р В РЎвЂўР РЋР С“Р РЋР Р‰ Р В Р’В°Р В РЎвЂќР РЋРІР‚С™Р В РЎвЂР В Р вЂ Р В РЎвЂР РЋР вЂљР В РЎвЂўР В Р вЂ Р В Р’В°Р РЋРІР‚С™Р РЋР Р‰ Р В Р вЂ Р В Р вЂ¦Р В Р’ВµР РЋРІвЂљВ¬Р В Р вЂ¦Р В РЎвЂР В РІвЂћвЂ“ markdown-Р В РЎвЂР РЋР С“Р РЋРІР‚С™Р В РЎвЂўР РЋРІР‚РЋР В Р вЂ¦Р В РЎвЂР В РЎвЂќ", exception);
            Toast.makeText(
                    this,
                    "Р В РЎСљР В Р’Вµ Р РЋРЎвЂњР В РўвЂР В Р’В°Р В Р’В»Р В РЎвЂўР РЋР С“Р РЋР Р‰ Р В РЎвЂўР В Р’В±Р В Р вЂ¦Р В РЎвЂўР В Р вЂ Р В РЎвЂР РЋРІР‚С™Р РЋР Р‰ Р В РЎвЂР РЋР С“Р РЋРІР‚С™Р В РЎвЂўР РЋРІР‚РЋР В Р вЂ¦Р В РЎвЂР В РЎвЂќ: " + safeMessage(exception),
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void buildUi() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setClipToPadding(false);
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
        summaryCard.addView(createText("Р В РЎвЂ™Р В РЎвЂќР РЋРІР‚С™Р В РЎвЂР В Р вЂ Р В Р вЂ¦Р В РЎвЂўР В Р’Вµ Р РЋРІР‚В¦Р РЋР вЂљР В Р’В°Р В Р вЂ¦Р В РЎвЂР В Р’В»Р В РЎвЂР РЋРІР‚В°Р В Р’Вµ", 15, R.color.text_primary, true), fullWidth());
        modeHeadlineText = createText("", 16, R.color.text_primary, true);
        modeHeadlineText.setPadding(0, dp(8), 0, 0);
        summaryCard.addView(modeHeadlineText, fullWidth());
        modeMetaText = createText("", 13, R.color.text_secondary, false);
        modeMetaText.setPadding(0, dp(6), 0, 0);
        summaryCard.addView(modeMetaText, fullWidth());
        modeTechnicalText = createText("", 12, R.color.text_secondary, false);
        modeTechnicalText.setPadding(0, dp(6), 0, 0);
        modeTechnicalText.setSingleLine(true);
        modeTechnicalText.setEllipsize(TextUtils.TruncateAt.END);
        summaryCard.addView(modeTechnicalText, fullWidth());
        root.addView(summaryCard, fullWidthWithBottomMargin());

        root.addView(createSectionTitle("Р В Р’В Р В Р’ВµР В Р’В¶Р В РЎвЂР В РЎВ Р РЋРІР‚В¦Р РЋР вЂљР В Р’В°Р В Р вЂ¦Р В Р’ВµР В Р вЂ¦Р В РЎвЂР РЋР РЏ"), fullWidth());
        ChoiceCardViews internalChoice = createChoiceCard(
                "Р В РІР‚в„ўР РЋР С“Р РЋРІР‚С™Р РЋР вЂљР В РЎвЂўР В Р’ВµР В Р вЂ¦Р В Р вЂ¦Р В РЎвЂўР В Р’Вµ Р РЋРІР‚В¦Р РЋР вЂљР В Р’В°Р В Р вЂ¦Р В РЎвЂР В Р’В»Р В РЎвЂР РЋРІР‚В°Р В Р’Вµ",
                "Р В РІР‚С”Р В РЎвЂўР В РЎвЂќР В Р’В°Р В Р’В»Р РЋР Р‰Р В Р вЂ¦Р РЋРІР‚в„–Р В Р’Вµ markdown-Р РЋРІР‚С›Р В Р’В°Р В РІвЂћвЂ“Р В Р’В»Р РЋРІР‚в„– Р В Р вЂ Р В Р вЂ¦Р РЋРЎвЂњР РЋРІР‚С™Р РЋР вЂљР В РЎвЂ Р В РЎвЂ”Р РЋР вЂљР В РЎвЂР В Р’В»Р В РЎвЂўР В Р’В¶Р В Р’ВµР В Р вЂ¦Р В РЎвЂР РЋР РЏ. Р В РЎСљР В Р’Вµ Р РЋРІР‚С™Р РЋР вЂљР В Р’ВµР В Р’В±Р РЋРЎвЂњР В Р’ВµР РЋРІР‚С™ Р В Р вЂ Р РЋРІР‚в„–Р В Р’В±Р В РЎвЂўР РЋР вЂљР В Р’В° Р В Р вЂ Р В Р вЂ¦Р В Р’ВµР РЋРІвЂљВ¬Р В Р вЂ¦Р В Р’ВµР В РІвЂћвЂ“ Р В РЎвЂ”Р В Р’В°Р В РЎвЂ”Р В РЎвЂќР В РЎвЂ.",
                this::confirmSwitchToInternal
        );
        internalModeCard = internalChoice.card;
        internalModeMarker = internalChoice.marker;
        root.addView(internalChoice.card, fullWidthWithBottomMargin());

        ChoiceCardViews externalChoice = createChoiceCard(
                "Р В РІР‚в„ўР В Р вЂ¦Р В Р’ВµР РЋРІвЂљВ¬Р В Р вЂ¦Р В РЎвЂР В Р’Вµ markdown-Р РЋРІР‚С›Р В Р’В°Р В РІвЂћвЂ“Р В Р’В»Р РЋРІР‚в„–",
                "Р В Р’В¤Р В Р’В°Р В РІвЂћвЂ“Р В Р’В»Р РЋРІР‚в„– Р В РЎвЂ Р В РЎвЂ”Р В Р’В°Р В РЎвЂ”Р В РЎвЂќР В РЎвЂ Р РЋРІР‚РЋР В Р’ВµР РЋР вЂљР В Р’ВµР В Р’В· Android picker. Р В РЎСџР В РЎвЂўР В РўвЂР РЋРІР‚В¦Р В РЎвЂўР В РўвЂР В РЎвЂР РЋРІР‚С™ Р В РўвЂР В Р’В»Р РЋР РЏ Obsidian, Syncthing Р В РЎвЂ Р В РЎвЂўР В Р’В±Р РЋРІР‚в„–Р РЋРІР‚РЋР В Р вЂ¦Р РЋРІР‚в„–Р РЋРІР‚В¦ markdown-Р РЋРІР‚С›Р В Р’В°Р В РІвЂћвЂ“Р В Р’В»Р В РЎвЂўР В Р вЂ .",
                this::activateExternalMode
        );
        externalModeCard = externalChoice.card;
        externalModeMarker = externalChoice.marker;
        root.addView(externalChoice.card, fullWidthWithBottomMargin());

        TextView migrationHint = createText(
                "Р В Р Р‹Р В РЎВР В Р’ВµР В Р вЂ¦Р В Р’В° Р РЋР вЂљР В Р’ВµР В Р’В¶Р В РЎвЂР В РЎВР В Р’В° Р В Р вЂ¦Р В Р’Вµ Р В РЎвЂ”Р В Р’ВµР РЋР вЂљР В Р’ВµР В Р вЂ¦Р В РЎвЂўР РЋР С“Р В РЎвЂР РЋРІР‚С™ Р В Р’В·Р В Р’В°Р В РўвЂР В Р’В°Р РЋРІР‚РЋР В РЎвЂ Р В Р’В°Р В Р вЂ Р РЋРІР‚С™Р В РЎвЂўР В РЎВР В Р’В°Р РЋРІР‚С™Р В РЎвЂР РЋРІР‚РЋР В Р’ВµР РЋР С“Р В РЎвЂќР В РЎвЂ. Р В РІР‚СњР В Р’В»Р РЋР РЏ Р В РЎВР В РЎвЂР В РЎвЂ“Р РЋР вЂљР В Р’В°Р РЋРІР‚В Р В РЎвЂР В РЎвЂ markdown-Р РЋРІР‚С›Р В Р’В°Р В РІвЂћвЂ“Р В Р’В»Р РЋРІР‚в„– Р В РЎвЂ”Р В РЎвЂўР В РЎвЂќР В Р’В° Р В Р вЂ¦Р РЋРЎвЂњР В Р’В¶Р В Р вЂ¦Р В РЎвЂў Р В РЎвЂќР В РЎвЂўР В РЎвЂ”Р В РЎвЂР РЋР вЂљР В РЎвЂўР В Р вЂ Р В Р’В°Р РЋРІР‚С™Р РЋР Р‰ Р В Р вЂ Р РЋР вЂљР РЋРЎвЂњР РЋРІР‚РЋР В Р вЂ¦Р РЋРЎвЂњР РЋР вЂ№.",
                13,
                R.color.text_secondary,
                false
        );
        migrationHint.setPadding(0, 0, 0, dp(8));
        root.addView(migrationHint, fullWidthWithBottomMargin());

        root.addView(createSectionTitle("Р В РІР‚в„ўР В Р вЂ¦Р В Р’ВµР РЋРІвЂљВ¬Р В Р вЂ¦Р В РЎвЂР В Р’Вµ markdown-Р В РЎвЂР РЋР С“Р РЋРІР‚С™Р В РЎвЂўР РЋРІР‚РЋР В Р вЂ¦Р В РЎвЂР В РЎвЂќР В РЎвЂ"), fullWidth());
        externalSourcesHintText = createSectionHint("");
        root.addView(externalSourcesHintText, fullWidthWithBottomMargin());

        root.addView(createSubsectionTitle("Р В РІР‚вЂќР В Р’В°Р В РЎВР В Р’ВµР В Р вЂ¦Р В РЎвЂР РЋРІР‚С™Р РЋР Р‰ Р РЋРІР‚С™Р В Р’ВµР В РЎвЂќР РЋРЎвЂњР РЋРІР‚В°Р В РЎвЂР В РІвЂћвЂ“ Р В РЎвЂР РЋР С“Р РЋРІР‚С™Р В РЎвЂўР РЋРІР‚РЋР В Р вЂ¦Р В РЎвЂР В РЎвЂќ"), fullWidth());
        root.addView(createActionCard(
                "Р В РІР‚в„ўР РЋРІР‚в„–Р В Р’В±Р РЋР вЂљР В Р’В°Р РЋРІР‚С™Р РЋР Р‰ Р В Р’В·Р В Р’В°Р В РЎВР В Р’ВµР РЋРІР‚С™Р В РЎвЂќР В РЎвЂ",
                "Р В Р’ВР РЋР С“Р В РЎвЂ”Р В РЎвЂўР В Р’В»Р РЋР Р‰Р В Р’В·Р В РЎвЂўР В Р вЂ Р В Р’В°Р РЋРІР‚С™Р РЋР Р‰ Р В РЎвЂўР В РўвЂР В РЎвЂР В Р вЂ¦ Р В РЎвЂР В Р’В»Р В РЎвЂ Р В Р вЂ¦Р В Р’ВµР РЋР С“Р В РЎвЂќР В РЎвЂўР В Р’В»Р РЋР Р‰Р В РЎвЂќР В РЎвЂў markdown-Р РЋРІР‚С›Р В Р’В°Р В РІвЂћвЂ“Р В Р’В»Р В РЎвЂўР В Р вЂ  Р В РЎвЂќР В Р’В°Р В РЎвЂќ Р В Р вЂ¦Р В РЎвЂўР В Р вЂ Р РЋРІР‚в„–Р В РІвЂћвЂ“ Р В РЎвЂР РЋР С“Р РЋРІР‚С™Р В РЎвЂўР РЋРІР‚РЋР В Р вЂ¦Р В РЎвЂР В РЎвЂќ.",
                () -> openNotePicker(REQUEST_REPLACE_NOTES)
        ), fullWidthWithBottomMargin());
        root.addView(createActionCard(
                "Р В РІР‚в„ўР РЋРІР‚в„–Р В Р’В±Р РЋР вЂљР В Р’В°Р РЋРІР‚С™Р РЋР Р‰ Р В РЎвЂ”Р В Р’В°Р В РЎвЂ”Р В РЎвЂќР РЋРЎвЂњ",
                "Р В Р’ВР РЋР С“Р В РЎвЂ”Р В РЎвЂўР В Р’В»Р РЋР Р‰Р В Р’В·Р В РЎвЂўР В Р вЂ Р В Р’В°Р РЋРІР‚С™Р РЋР Р‰ Р В РЎвЂ”Р В Р’В°Р В РЎвЂ”Р В РЎвЂќР РЋРЎвЂњ Р РЋР С“ markdown-Р РЋРІР‚С›Р В Р’В°Р В РІвЂћвЂ“Р В Р’В»Р В Р’В°Р В РЎВР В РЎвЂ Р В РЎвЂќР В Р’В°Р В РЎвЂќ Р В Р вЂ¦Р В РЎвЂўР В Р вЂ Р РЋРІР‚в„–Р В РІвЂћвЂ“ Р В РЎвЂР РЋР С“Р РЋРІР‚С™Р В РЎвЂўР РЋРІР‚РЋР В Р вЂ¦Р В РЎвЂР В РЎвЂќ.",
                () -> openFolderPicker(REQUEST_REPLACE_FOLDER)
        ), fullWidthWithBottomMargin());

        root.addView(createSubsectionTitle("Р В РІР‚СњР В РЎвЂўР В Р’В±Р В Р’В°Р В Р вЂ Р В РЎвЂР РЋРІР‚С™Р РЋР Р‰ Р В РЎвЂќ Р РЋРІР‚С™Р В Р’ВµР В РЎвЂќР РЋРЎвЂњР РЋРІР‚В°Р В РЎвЂР В РЎВ"), fullWidth());
        root.addView(createActionCard(
                "Р В РІР‚СњР В РЎвЂўР В Р’В±Р В Р’В°Р В Р вЂ Р В РЎвЂР РЋРІР‚С™Р РЋР Р‰ Р В Р’В·Р В Р’В°Р В РЎВР В Р’ВµР РЋРІР‚С™Р В РЎвЂќР РЋРЎвЂњ",
                "Р В РЎСџР В РЎвЂўР В РўвЂР В РЎвЂќР В Р’В»Р РЋР вЂ№Р РЋРІР‚РЋР В РЎвЂР РЋРІР‚С™Р РЋР Р‰ Р В Р’ВµР РЋРІР‚В°Р РЋРІР‚В Р В РЎвЂўР В РўвЂР В РЎвЂР В Р вЂ¦ markdown-Р РЋРІР‚С›Р В Р’В°Р В РІвЂћвЂ“Р В Р’В».",
                () -> openNotePicker(REQUEST_ADD_NOTES)
        ), fullWidthWithBottomMargin());
        root.addView(createActionCard(
                "Р В РІР‚СњР В РЎвЂўР В Р’В±Р В Р’В°Р В Р вЂ Р В РЎвЂР РЋРІР‚С™Р РЋР Р‰ Р В РЎвЂ”Р В Р’В°Р В РЎвЂ”Р В РЎвЂќР РЋРЎвЂњ",
                "Р В РЎСџР В РЎвЂўР В РўвЂР В РЎвЂќР В Р’В»Р РЋР вЂ№Р РЋРІР‚РЋР В РЎвЂР РЋРІР‚С™Р РЋР Р‰ Р В РўвЂР В РЎвЂўР В РЎвЂ”Р В РЎвЂўР В Р’В»Р В Р вЂ¦Р В РЎвЂР РЋРІР‚С™Р В Р’ВµР В Р’В»Р РЋР Р‰Р В Р вЂ¦Р РЋРЎвЂњР РЋР вЂ№ Р В РЎвЂ”Р В Р’В°Р В РЎвЂ”Р В РЎвЂќР РЋРЎвЂњ Р РЋР С“ markdown-Р РЋРІР‚С›Р В Р’В°Р В РІвЂћвЂ“Р В Р’В»Р В Р’В°Р В РЎВР В РЎвЂ.",
                () -> openFolderPicker(REQUEST_ADD_FOLDER)
        ), fullWidthWithBottomMargin());

        root.addView(createSubsectionTitle("Р В Р Р‹Р В РЎвЂўР В Р’В·Р В РўвЂР В Р’В°Р РЋРІР‚С™Р РЋР Р‰"), fullWidth());
        root.addView(createActionCard(
                "Р В Р Р‹Р В РЎвЂўР В Р’В·Р В РўвЂР В Р’В°Р РЋРІР‚С™Р РЋР Р‰ Р В Р вЂ Р В Р вЂ¦Р В Р’ВµР РЋРІвЂљВ¬Р В Р вЂ¦Р В РЎвЂР В РІвЂћвЂ“ Р РЋРІР‚С›Р В Р’В°Р В РІвЂћвЂ“Р В Р’В»",
                "Р В Р Р‹Р В РЎвЂўР В Р’В·Р В РўвЂР В Р’В°Р РЋРІР‚С™Р РЋР Р‰ Р В Р вЂ¦Р В РЎвЂўР В Р вЂ Р РЋРІР‚в„–Р В РІвЂћвЂ“ markdown-Р РЋРІР‚С›Р В Р’В°Р В РІвЂћвЂ“Р В Р’В» Р РЋРІР‚РЋР В Р’ВµР РЋР вЂљР В Р’ВµР В Р’В· Android picker Р В РЎвЂ Р В РЎвЂ”Р В РЎвЂўР В РўвЂР В РЎвЂќР В Р’В»Р РЋР вЂ№Р РЋРІР‚РЋР В РЎвЂР РЋРІР‚С™Р РЋР Р‰ Р В Р’ВµР В РЎвЂ“Р В РЎвЂў.",
                this::openNoteCreator
        ), fullWidthWithBottomMargin());

        root.addView(createSectionTitle("Р В РЎвЂєР В РЎвЂ”Р В Р’В°Р РЋР С“Р В Р вЂ¦Р В Р’В°Р РЋР РЏ Р В Р’В·Р В РЎвЂўР В Р вЂ¦Р В Р’В°"), fullWidth());
        root.addView(createDestructiveActionCard(
                "Р В РЎвЂєР РЋРІР‚РЋР В РЎвЂР РЋР С“Р РЋРІР‚С™Р В РЎвЂР РЋРІР‚С™Р РЋР Р‰ Р В Р вЂ Р В Р вЂ¦Р В Р’ВµР РЋРІвЂљВ¬Р В Р вЂ¦Р В РЎвЂР В Р’Вµ Р В РЎвЂ”Р В РЎвЂўР В РўвЂР В РЎвЂќР В Р’В»Р РЋР вЂ№Р РЋРІР‚РЋР В Р’ВµР В Р вЂ¦Р В РЎвЂР РЋР РЏ",
                "Р В РЎСџР РЋР вЂљР В РЎвЂР В Р’В»Р В РЎвЂўР В Р’В¶Р В Р’ВµР В Р вЂ¦Р В РЎвЂР В Р’Вµ Р В Р’В·Р В Р’В°Р В Р’В±Р РЋРЎвЂњР В РўвЂР В Р’ВµР РЋРІР‚С™ Р В Р вЂ Р РЋРІР‚в„–Р В Р’В±Р РЋР вЂљР В Р’В°Р В Р вЂ¦Р В Р вЂ¦Р РЋРІР‚в„–Р В Р’Вµ Р В Р вЂ Р В Р вЂ¦Р В Р’ВµР РЋРІвЂљВ¬Р В Р вЂ¦Р В РЎвЂР В Р’Вµ Р РЋРІР‚С›Р В Р’В°Р В РІвЂћвЂ“Р В Р’В»Р РЋРІР‚в„– Р В РЎвЂ Р В РЎвЂ”Р В Р’В°Р В РЎвЂ”Р В РЎвЂќР В РЎвЂ, Р В Р вЂ¦Р В РЎвЂў Р РЋР С“Р В Р’В°Р В РЎВР В РЎвЂ markdown-Р РЋРІР‚С›Р В Р’В°Р В РІвЂћвЂ“Р В Р’В»Р РЋРІР‚в„– Р В Р вЂ¦Р В Р’В° Р В РўвЂР В РЎвЂР РЋР С“Р В РЎвЂќР В Р’Вµ Р РЋРЎвЂњР В РўвЂР В Р’В°Р В Р’В»Р В Р’ВµР В Р вЂ¦Р РЋРІР‚в„– Р В Р вЂ¦Р В Р’Вµ Р В Р’В±Р РЋРЎвЂњР В РўвЂР РЋРЎвЂњР РЋРІР‚С™.",
                this::confirmClearSources
        ), fullWidthWithBottomMargin());

        root.addView(createSectionTitle("Р В Р Р‹Р В РЎвЂўР РЋРІР‚В¦Р РЋР вЂљР В Р’В°Р В Р вЂ¦Р РЋРІР‚ВР В Р вЂ¦Р В Р вЂ¦Р РЋРІР‚в„–Р В Р’Вµ Р В Р вЂ Р В Р вЂ¦Р В Р’ВµР РЋРІвЂљВ¬Р В Р вЂ¦Р В РЎвЂР В Р’Вµ Р В РЎвЂР РЋР С“Р РЋРІР‚С™Р В РЎвЂўР РЋРІР‚РЋР В Р вЂ¦Р В РЎвЂР В РЎвЂќР В РЎвЂ"), fullWidth());
        sourcesList = new LinearLayout(this);
        sourcesList.setOrientation(LinearLayout.VERTICAL);
        root.addView(sourcesList, fullWidth());

        setContentView(scrollView);
        applyWindowInsets(root);
    }

    private LinearLayout createTopBar() {
        LinearLayout appBar = new LinearLayout(this);
        appBar.setOrientation(LinearLayout.HORIZONTAL);
        appBar.setGravity(Gravity.CENTER_VERTICAL);

        ImageButton back = createIconButton(R.drawable.ic_arrow_back, "Р В РЎСљР В Р’В°Р В Р’В·Р В Р’В°Р В РўвЂ");
        back.setOnClickListener(view -> finish());
        appBar.addView(back, new LinearLayout.LayoutParams(dp(40), dp(40)));

        TextView title = createText("Р В Р’ВР РЋР С“Р РЋРІР‚С™Р В РЎвЂўР РЋРІР‚РЋР В Р вЂ¦Р В РЎвЂР В РЎвЂќР В РЎвЂ Р В Р’В·Р В Р’В°Р В РўвЂР В Р’В°Р РЋРІР‚РЋ", 21, R.color.text_primary, true);
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
        renderModeSummary();
        renderModeSelection();
        renderExternalHint();
        renderSavedSources();
    }

    private void renderModeSummary() {
        TaskStorageMode mode = TaskSourceManager.getStorageMode(this);
        if (mode == TaskStorageMode.INTERNAL_MARKDOWN_STORAGE) {
            modeHeadlineText.setText("Р В РІР‚в„ўР РЋР С“Р РЋРІР‚С™Р РЋР вЂљР В РЎвЂўР В Р’ВµР В Р вЂ¦Р В Р вЂ¦Р В РЎвЂўР В Р’Вµ Р РЋРІР‚В¦Р РЋР вЂљР В Р’В°Р В Р вЂ¦Р В РЎвЂР В Р’В»Р В РЎвЂР РЋРІР‚В°Р В Р’Вµ");
            modeMetaText.setText("Р В РІР‚С”Р В РЎвЂўР В РЎвЂќР В Р’В°Р В Р’В»Р РЋР Р‰Р В Р вЂ¦Р РЋРІР‚в„–Р В Р’Вµ markdown-Р РЋРІР‚С›Р В Р’В°Р В РІвЂћвЂ“Р В Р’В»Р РЋРІР‚в„– Р В Р вЂ Р В Р вЂ¦Р РЋРЎвЂњР РЋРІР‚С™Р РЋР вЂљР В РЎвЂ Р В РЎвЂ”Р РЋР вЂљР В РЎвЂР В Р’В»Р В РЎвЂўР В Р’В¶Р В Р’ВµР В Р вЂ¦Р В РЎвЂР РЋР РЏ");
            modeTechnicalText.setVisibility(TextView.GONE);
            return;
        }

        SourceDisplayNameResolver.ExternalSummaryModel summary =
                SourceDisplayNameResolver.summarizeExternalSources(this, NoteStore.getSavedSources(this));
        modeHeadlineText.setText(summary.getHeadline());
        modeMetaText.setText(summary.getSubtitle());
        modeTechnicalText.setVisibility(TextView.GONE);
    }

    private void renderModeSelection() {
        TaskStorageMode mode = TaskSourceManager.getStorageMode(this);
        boolean internalSelected = mode == TaskStorageMode.INTERNAL_MARKDOWN_STORAGE;
        bindChoiceCardState(internalModeCard, internalModeMarker, internalSelected);
        bindChoiceCardState(externalModeCard, externalModeMarker, !internalSelected);
    }

    private void renderExternalHint() {
        if (externalSourcesHintText == null) {
            return;
        }
        if (TaskSourceManager.getStorageMode(this) == TaskStorageMode.INTERNAL_MARKDOWN_STORAGE) {
            externalSourcesHintText.setText(
                    "Р В Р’В¤Р В Р’В°Р В РІвЂћвЂ“Р В Р’В»Р РЋРІР‚в„– Р В РЎвЂ Р В РЎвЂ”Р В Р’В°Р В РЎвЂ”Р В РЎвЂќР В РЎвЂ, Р В РЎвЂќР В РЎвЂўР РЋРІР‚С™Р В РЎвЂўР РЋР вЂљР РЋРІР‚в„–Р В Р’Вµ Р В РЎвЂ”Р РЋР вЂљР В РЎвЂР В Р’В»Р В РЎвЂўР В Р’В¶Р В Р’ВµР В Р вЂ¦Р В РЎвЂР В Р’Вµ Р В РЎВР В РЎвЂўР В Р’В¶Р В Р’ВµР РЋРІР‚С™ Р В РЎвЂР РЋР С“Р В РЎвЂ”Р В РЎвЂўР В Р’В»Р РЋР Р‰Р В Р’В·Р В РЎвЂўР В Р вЂ Р В Р’В°Р РЋРІР‚С™Р РЋР Р‰ Р В Р вЂ Р В РЎвЂў Р В Р вЂ Р В Р вЂ¦Р В Р’ВµР РЋРІвЂљВ¬Р В Р вЂ¦Р В Р’ВµР В РЎВ Р РЋР вЂљР В Р’ВµР В Р’В¶Р В РЎвЂР В РЎВР В Р’Вµ. Р В Р’ВР РЋРІР‚В¦ Р В РЎВР В РЎвЂўР В Р’В¶Р В Р вЂ¦Р В РЎвЂў Р В Р вЂ Р РЋРІР‚в„–Р В Р’В±Р РЋР вЂљР В Р’В°Р РЋРІР‚С™Р РЋР Р‰ Р В Р’В·Р В Р’В°Р РЋР вЂљР В Р’В°Р В Р вЂ¦Р В Р’ВµР В Р’Вµ, Р В РўвЂР В Р’В°Р В Р’В¶Р В Р’Вµ Р В Р’ВµР РЋР С“Р В Р’В»Р В РЎвЂ Р РЋР С“Р В Р’ВµР В РІвЂћвЂ“Р РЋРІР‚РЋР В Р’В°Р РЋР С“ Р В Р’В°Р В РЎвЂќР РЋРІР‚С™Р В РЎвЂР В Р вЂ Р В Р вЂ¦Р В РЎвЂў Р В Р вЂ Р РЋР С“Р РЋРІР‚С™Р РЋР вЂљР В РЎвЂўР В Р’ВµР В Р вЂ¦Р В Р вЂ¦Р В РЎвЂўР В Р’Вµ Р РЋРІР‚В¦Р РЋР вЂљР В Р’В°Р В Р вЂ¦Р В РЎвЂР В Р’В»Р В РЎвЂР РЋРІР‚В°Р В Р’Вµ."
            );
        } else {
            externalSourcesHintText.setText(
                    "Р В Р’В¤Р В Р’В°Р В РІвЂћвЂ“Р В Р’В»Р РЋРІР‚в„– Р В РЎвЂ Р В РЎвЂ”Р В Р’В°Р В РЎвЂ”Р В РЎвЂќР В РЎвЂ, Р В РЎвЂќР В РЎвЂўР РЋРІР‚С™Р В РЎвЂўР РЋР вЂљР РЋРІР‚в„–Р В Р’Вµ Р РЋР С“Р В Р’ВµР В РІвЂћвЂ“Р РЋРІР‚РЋР В Р’В°Р РЋР С“ Р В РЎвЂР РЋР С“Р В РЎвЂ”Р В РЎвЂўР В Р’В»Р РЋР Р‰Р В Р’В·Р РЋРЎвЂњР РЋР вЂ№Р РЋРІР‚С™Р РЋР С“Р РЋР РЏ Р В РЎвЂР В Р’В»Р В РЎвЂ Р В РЎВР В РЎвЂўР В РЎвЂ“Р РЋРЎвЂњР РЋРІР‚С™ Р В РЎвЂР РЋР С“Р В РЎвЂ”Р В РЎвЂўР В Р’В»Р РЋР Р‰Р В Р’В·Р В РЎвЂўР В Р вЂ Р В Р’В°Р РЋРІР‚С™Р РЋР Р‰Р РЋР С“Р РЋР РЏ Р В РЎвЂќР В Р’В°Р В РЎвЂќ Р В Р вЂ Р В Р вЂ¦Р В Р’ВµР РЋРІвЂљВ¬Р В Р вЂ¦Р В РЎвЂР В РІвЂћвЂ“ markdown-Р В РЎвЂР РЋР С“Р РЋРІР‚С™Р В РЎвЂўР РЋРІР‚РЋР В Р вЂ¦Р В РЎвЂР В РЎвЂќ."
            );
        }
    }

    private void renderSavedSources() {
        if (sourcesList == null) {
            return;
        }
        sourcesList.removeAllViews();

        List<NoteStore.NoteSource> sources = NoteStore.getSavedSources(this);
        if (sources.isEmpty()) {
            LinearLayout emptyCard = createCardContainer();
            emptyCard.addView(createText(
                    "Р В Р Р‹Р В РЎвЂўР РЋРІР‚В¦Р РЋР вЂљР В Р’В°Р В Р вЂ¦Р РЋРІР‚ВР В Р вЂ¦Р В Р вЂ¦Р РЋРІР‚в„–Р РЋРІР‚В¦ Р В Р вЂ Р В Р вЂ¦Р В Р’ВµР РЋРІвЂљВ¬Р В Р вЂ¦Р В РЎвЂР РЋРІР‚В¦ Р В РЎвЂР РЋР С“Р РЋРІР‚С™Р В РЎвЂўР РЋРІР‚РЋР В Р вЂ¦Р В РЎвЂР В РЎвЂќР В РЎвЂўР В Р вЂ  Р В РЎвЂ”Р В РЎвЂўР В РЎвЂќР В Р’В° Р В Р вЂ¦Р В Р’ВµР РЋРІР‚С™.",
                    15,
                    R.color.text_primary,
                    true
            ), fullWidth());
            TextView subtitle = createText(
                    "Р В РІР‚в„ўР РЋРІР‚в„–Р В Р’В±Р В Р’ВµР РЋР вЂљР В РЎвЂР РЋРІР‚С™Р В Р’Вµ Р РЋРІР‚С›Р В Р’В°Р В РІвЂћвЂ“Р В Р’В» Р В РЎвЂР В Р’В»Р В РЎвЂ Р В РЎвЂ”Р В Р’В°Р В РЎвЂ”Р В РЎвЂќР РЋРЎвЂњ, Р РЋРІР‚РЋР РЋРІР‚С™Р В РЎвЂўР В Р’В±Р РЋРІР‚в„– Р В РЎвЂ”Р В РЎвЂўР В РўвЂР В РЎвЂќР В Р’В»Р РЋР вЂ№Р РЋРІР‚РЋР В РЎвЂР РЋРІР‚С™Р РЋР Р‰ markdown-Р В Р’В·Р В Р’В°Р В РўвЂР В Р’В°Р РЋРІР‚РЋР В РЎвЂ.",
                    13,
                    R.color.text_secondary,
                    false
            );
            subtitle.setPadding(0, dp(4), 0, 0);
            emptyCard.addView(subtitle, fullWidth());
            sourcesList.addView(emptyCard, fullWidthWithBottomMargin());
            return;
        }

        for (NoteStore.NoteSource source : sources) {
            sourcesList.addView(createSourceItem(source), fullWidthWithBottomMargin());
        }
    }

    private LinearLayout createSourceItem(NoteStore.NoteSource source) {
        SourceDisplayNameResolver.SourceItemModel model =
                SourceDisplayNameResolver.describeExternalSource(this, source);

        LinearLayout card = createCardContainer();
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        ImageView icon = new ImageView(this);
        icon.setImageResource(model.isFolder() ? R.drawable.ic_folder : R.drawable.ic_file);
        icon.setColorFilter(getColor(model.getAccessState() == SourceDisplayNameResolver.AccessState.LOST
                ? R.color.error_text
                : R.color.text_secondary));
        row.addView(icon, new LinearLayout.LayoutParams(dp(24), dp(24)));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(createText(model.getTitle(), 15, R.color.text_primary, true), fullWidth());

        TextView subtitle = createText(
                model.getSubtitle(),
                12,
                model.getAccessState() == SourceDisplayNameResolver.AccessState.LOST
                        ? R.color.error_text
                        : R.color.text_secondary,
                false
        );
        subtitle.setPadding(0, dp(3), 0, 0);
        texts.addView(subtitle, fullWidth());

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
                .setTitle("Р В РЎСџР В Р’ВµР РЋР вЂљР В Р’ВµР В РЎвЂќР В Р’В»Р РЋР вЂ№Р РЋРІР‚РЋР В РЎвЂР РЋРІР‚С™Р РЋР Р‰ Р В РЎвЂР РЋР С“Р РЋРІР‚С™Р В РЎвЂўР РЋРІР‚РЋР В Р вЂ¦Р В РЎвЂР В РЎвЂќ")
                .setMessage(TaskSourceManager.switchWithoutMigrationWarning(TaskStorageMode.INTERNAL_MARKDOWN_STORAGE))
                .setNegativeButton("Р В РЎвЂєР РЋРІР‚С™Р В РЎВР В Р’ВµР В Р вЂ¦Р В Р’В°", null)
                .setPositiveButton("Р В РЎСџР В Р’ВµР РЋР вЂљР В Р’ВµР В РЎвЂќР В Р’В»Р РЋР вЂ№Р РЋРІР‚РЋР В РЎвЂР РЋРІР‚С™Р РЋР Р‰", (dialog, which) -> switchToInternal())
                .show();
    }

    private void switchToInternal() {
        try {
            TaskSourceManager.useInternalStorage(this);
            OnboardingPreferences.markCompleted(this);
            resyncActiveSource();
            setResult(RESULT_OK);
            renderSources();
            Toast.makeText(this, "Р В РЎСџР РЋР вЂљР В РЎвЂР В Р’В»Р В РЎвЂўР В Р’В¶Р В Р’ВµР В Р вЂ¦Р В РЎвЂР В Р’Вµ Р В РЎвЂ”Р В Р’ВµР РЋР вЂљР В Р’ВµР В РЎвЂќР В Р’В»Р РЋР вЂ№Р РЋРІР‚РЋР В Р’ВµР В Р вЂ¦Р В РЎвЂў Р В Р вЂ¦Р В Р’В° Р В Р вЂ Р РЋР С“Р РЋРІР‚С™Р РЋР вЂљР В РЎвЂўР В Р’ВµР В Р вЂ¦Р В Р вЂ¦Р В РЎвЂўР В Р’Вµ Р РЋРІР‚В¦Р РЋР вЂљР В Р’В°Р В Р вЂ¦Р В РЎвЂР В Р’В»Р В РЎвЂР РЋРІР‚В°Р В Р’Вµ", Toast.LENGTH_SHORT).show();
        } catch (IOException exception) {
            ErrorLog.record(this, "Р В РЎСљР В Р’Вµ Р РЋРЎвЂњР В РўвЂР В Р’В°Р В Р’В»Р В РЎвЂўР РЋР С“Р РЋР Р‰ Р В Р вЂ Р В РЎвЂќР В Р’В»Р РЋР вЂ№Р РЋРІР‚РЋР В РЎвЂР РЋРІР‚С™Р РЋР Р‰ Р В Р вЂ Р РЋР С“Р РЋРІР‚С™Р РЋР вЂљР В РЎвЂўР В Р’ВµР В Р вЂ¦Р В Р вЂ¦Р В РЎвЂўР В Р’Вµ Р РЋРІР‚В¦Р РЋР вЂљР В Р’В°Р В Р вЂ¦Р В РЎвЂР В Р’В»Р В РЎвЂР РЋРІР‚В°Р В Р’Вµ", exception);
            Toast.makeText(
                    this,
                    "Р В РЎСљР В Р’Вµ Р РЋРЎвЂњР В РўвЂР В Р’В°Р В Р’В»Р В РЎвЂўР РЋР С“Р РЋР Р‰ Р В Р вЂ Р В РЎвЂќР В Р’В»Р РЋР вЂ№Р РЋРІР‚РЋР В РЎвЂР РЋРІР‚С™Р РЋР Р‰ Р В Р вЂ Р РЋР С“Р РЋРІР‚С™Р РЋР вЂљР В РЎвЂўР В Р’ВµР В Р вЂ¦Р В Р вЂ¦Р В РЎвЂўР В Р’Вµ Р РЋРІР‚В¦Р РЋР вЂљР В Р’В°Р В Р вЂ¦Р В РЎвЂР В Р’В»Р В РЎвЂР РЋРІР‚В°Р В Р’Вµ: " + safeMessage(exception),
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
        Toast.makeText(this, "Р В РІР‚в„ўР В Р вЂ¦Р В Р’ВµР РЋРІвЂљВ¬Р В Р вЂ¦Р В РЎвЂР В РІвЂћвЂ“ Р В РЎвЂР РЋР С“Р РЋРІР‚С™Р В РЎвЂўР РЋРІР‚РЋР В Р вЂ¦Р В РЎвЂР В РЎвЂќ Р В Р’В°Р В РЎвЂќР РЋРІР‚С™Р В РЎвЂР В Р вЂ Р В РЎвЂР РЋР вЂљР В РЎвЂўР В Р вЂ Р В Р’В°Р В Р вЂ¦", Toast.LENGTH_SHORT).show();
    }

    private void confirmClearSources() {
        new AlertDialog.Builder(this)
                .setTitle("Р В РЎвЂєР РЋРІР‚РЋР В РЎвЂР РЋР С“Р РЋРІР‚С™Р В РЎвЂР РЋРІР‚С™Р РЋР Р‰ Р В Р вЂ Р В Р вЂ¦Р В Р’ВµР РЋРІвЂљВ¬Р В Р вЂ¦Р В РЎвЂР В Р’Вµ Р В РЎвЂ”Р В РЎвЂўР В РўвЂР В РЎвЂќР В Р’В»Р РЋР вЂ№Р РЋРІР‚РЋР В Р’ВµР В Р вЂ¦Р В РЎвЂР РЋР РЏ?")
                .setMessage("Р В РЎСџР РЋР вЂљР В РЎвЂР В Р’В»Р В РЎвЂўР В Р’В¶Р В Р’ВµР В Р вЂ¦Р В РЎвЂР В Р’Вµ Р В Р’В·Р В Р’В°Р В Р’В±Р РЋРЎвЂњР В РўвЂР В Р’ВµР РЋРІР‚С™ Р В Р вЂ Р РЋРІР‚в„–Р В Р’В±Р РЋР вЂљР В Р’В°Р В Р вЂ¦Р В Р вЂ¦Р РЋРІР‚в„–Р В Р’Вµ Р В Р вЂ Р В Р вЂ¦Р В Р’ВµР РЋРІвЂљВ¬Р В Р вЂ¦Р В РЎвЂР В Р’Вµ Р РЋРІР‚С›Р В Р’В°Р В РІвЂћвЂ“Р В Р’В»Р РЋРІР‚в„– Р В РЎвЂ Р В РЎвЂ”Р В Р’В°Р В РЎвЂ”Р В РЎвЂќР В РЎвЂ. "
                        + "Р В Р Р‹Р В Р’В°Р В РЎВР В РЎвЂ markdown-Р РЋРІР‚С›Р В Р’В°Р В РІвЂћвЂ“Р В Р’В»Р РЋРІР‚в„– Р В Р вЂ¦Р В Р’В° Р В РўвЂР В РЎвЂР РЋР С“Р В РЎвЂќР В Р’Вµ Р РЋРЎвЂњР В РўвЂР В Р’В°Р В Р’В»Р В Р’ВµР В Р вЂ¦Р РЋРІР‚в„– Р В Р вЂ¦Р В Р’Вµ Р В Р’В±Р РЋРЎвЂњР В РўвЂР РЋРЎвЂњР РЋРІР‚С™.\n\n"
                        + "Р В РЎСџР В РЎвЂўР РЋР С“Р В Р’В»Р В Р’Вµ Р В РЎвЂўР РЋРІР‚РЋР В РЎвЂР РЋР С“Р РЋРІР‚С™Р В РЎвЂќР В РЎвЂ Р В РЎвЂ”Р РЋР вЂљР В РЎвЂР В Р’В»Р В РЎвЂўР В Р’В¶Р В Р’ВµР В Р вЂ¦Р В РЎвЂР В Р’Вµ Р В РЎвЂ”Р В Р’ВµР РЋР вЂљР В Р’ВµР В РЎвЂќР В Р’В»Р РЋР вЂ№Р РЋРІР‚РЋР В РЎвЂР РЋРІР‚С™Р РЋР С“Р РЋР РЏ Р В Р вЂ¦Р В Р’В° Р В Р вЂ Р РЋР С“Р РЋРІР‚С™Р РЋР вЂљР В РЎвЂўР В Р’ВµР В Р вЂ¦Р В Р вЂ¦Р В РЎвЂўР В Р’Вµ Р РЋРІР‚В¦Р РЋР вЂљР В Р’В°Р В Р вЂ¦Р В РЎвЂР В Р’В»Р В РЎвЂР РЋРІР‚В°Р В Р’Вµ.")
                .setNegativeButton("Р В РЎвЂєР РЋРІР‚С™Р В РЎВР В Р’ВµР В Р вЂ¦Р В Р’В°", null)
                .setPositiveButton("Р В РЎвЂєР РЋРІР‚РЋР В РЎвЂР РЋР С“Р РЋРІР‚С™Р В РЎвЂР РЋРІР‚С™Р РЋР Р‰", (dialog, which) -> clearSources())
                .show();
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
        TextView subtitleView = createText(subtitle, 13, R.color.text_secondary, false);
        subtitleView.setPadding(0, dp(3), 0, 0);
        texts.addView(subtitleView, fullWidth());
        row.addView(texts, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        ));

        TextView chevron = createText("Р Р†Р вЂљРЎвЂќ", 24, R.color.text_secondary, false);
        chevron.setGravity(Gravity.CENTER);
        row.addView(chevron, new LinearLayout.LayoutParams(dp(32), dp(42)));
        card.addView(row, fullWidth());
        return card;
    }

    private LinearLayout createDestructiveActionCard(String title, String subtitle, Runnable action) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(createRoundedBackground(
                getColor(R.color.card_background),
                getColor(R.color.error_text),
                8
        ));
        card.setOnClickListener(view -> action.run());

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_delete);
        icon.setColorFilter(getColor(R.color.error_text));
        card.addView(icon, new LinearLayout.LayoutParams(dp(24), dp(24)));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView titleView = createText(title, 15, R.color.error_text, true);
        texts.addView(titleView, fullWidth());
        TextView subtitleView = createText(subtitle, 13, R.color.text_secondary, false);
        subtitleView.setPadding(0, dp(3), 0, 0);
        texts.addView(subtitleView, fullWidth());
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        );
        textParams.setMargins(dp(12), 0, 0, 0);
        card.addView(texts, textParams);
        return card;
    }

    private ChoiceCardViews createChoiceCard(
            String title,
            String subtitle,
            Runnable action
    ) {
        LinearLayout card = createCardContainer();
        card.setOnClickListener(view -> action.run());

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(createText(title, 15, R.color.text_primary, true), fullWidth());
        TextView subtitleView = createText(subtitle, 13, R.color.text_secondary, false);
        subtitleView.setPadding(0, dp(3), 0, 0);
        texts.addView(subtitleView, fullWidth());
        row.addView(texts, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        ));

        TextView marker = createText("", 12, R.color.chip_selected_text, true);
        row.addView(marker, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        card.addView(row, fullWidth());
        return new ChoiceCardViews(card, marker);
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
        Toast.makeText(this, "Р В Р’В¤Р В Р’В°Р В РІвЂћвЂ“Р В Р’В» Р РЋР С“Р В РЎвЂўР В Р’В·Р В РўвЂР В Р’В°Р В Р вЂ¦ Р В РЎвЂ Р В РЎвЂ”Р В РЎвЂўР В РўвЂР В РЎвЂќР В Р’В»Р РЋР вЂ№Р РЋРІР‚РЋР РЋРІР‚ВР В Р вЂ¦ Р В РЎвЂќР В Р’В°Р В РЎвЂќ Р В Р вЂ Р В Р вЂ¦Р В Р’ВµР РЋРІвЂљВ¬Р В Р вЂ¦Р В РЎвЂР В РІвЂћвЂ“ Р В РЎвЂР РЋР С“Р РЋРІР‚С™Р В РЎвЂўР РЋРІР‚РЋР В Р вЂ¦Р В РЎвЂР В РЎвЂќ", Toast.LENGTH_SHORT).show();
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
        NoteStore.clearSources(this);
        try {
            TaskSourceManager.useInternalStorage(this);
            OnboardingPreferences.markCompleted(this);
            resyncActiveSource();
            setResult(RESULT_OK);
            renderSources();
            Toast.makeText(
                    this,
                    "Р В РІР‚в„ўР В Р вЂ¦Р В Р’ВµР РЋРІвЂљВ¬Р В Р вЂ¦Р В РЎвЂР В Р’Вµ Р В РЎвЂ”Р В РЎвЂўР В РўвЂР В РЎвЂќР В Р’В»Р РЋР вЂ№Р РЋРІР‚РЋР В Р’ВµР В Р вЂ¦Р В РЎвЂР РЋР РЏ Р В РЎвЂўР РЋРІР‚РЋР В РЎвЂР РЋРІР‚В°Р В Р’ВµР В Р вЂ¦Р РЋРІР‚в„–, Р В Р вЂ Р РЋР С“Р РЋРІР‚С™Р РЋР вЂљР В РЎвЂўР В Р’ВµР В Р вЂ¦Р В Р вЂ¦Р В РЎвЂўР В Р’Вµ Р РЋРІР‚В¦Р РЋР вЂљР В Р’В°Р В Р вЂ¦Р В РЎвЂР В Р’В»Р В РЎвЂР РЋРІР‚В°Р В Р’Вµ Р РЋР С“Р В Р вЂ¦Р В РЎвЂўР В Р вЂ Р В Р’В° Р В Р’В°Р В РЎвЂќР РЋРІР‚С™Р В РЎвЂР В Р вЂ Р В Р вЂ¦Р В РЎвЂў",
                    Toast.LENGTH_SHORT
            ).show();
        } catch (IOException exception) {
            ErrorLog.record(this, "Р В РЎСљР В Р’Вµ Р РЋРЎвЂњР В РўвЂР В Р’В°Р В Р’В»Р В РЎвЂўР РЋР С“Р РЋР Р‰ Р В РЎвЂ”Р В РЎвЂўР В РўвЂР В РЎвЂ“Р В РЎвЂўР РЋРІР‚С™Р В РЎвЂўР В Р вЂ Р В РЎвЂР РЋРІР‚С™Р РЋР Р‰ Р В Р вЂ Р РЋР С“Р РЋРІР‚С™Р РЋР вЂљР В РЎвЂўР В Р’ВµР В Р вЂ¦Р В Р вЂ¦Р В РЎвЂўР В Р’Вµ Р РЋРІР‚В¦Р РЋР вЂљР В Р’В°Р В Р вЂ¦Р В РЎвЂР В Р’В»Р В РЎвЂР РЋРІР‚В°Р В Р’Вµ Р В РЎвЂ”Р В РЎвЂўР РЋР С“Р В Р’В»Р В Р’Вµ Р В РЎвЂўР РЋРІР‚РЋР В РЎвЂР РЋР С“Р РЋРІР‚С™Р В РЎвЂќР В РЎвЂ Р В РЎвЂР РЋР С“Р РЋРІР‚С™Р В РЎвЂўР РЋРІР‚РЋР В Р вЂ¦Р В РЎвЂР В РЎвЂќР В РЎвЂўР В Р вЂ ", exception);
            Toast.makeText(
                    this,
                    "Р В РІР‚в„ўР В Р вЂ¦Р В Р’ВµР РЋРІвЂљВ¬Р В Р вЂ¦Р В РЎвЂР В Р’Вµ Р В РЎвЂ”Р В РЎвЂўР В РўвЂР В РЎвЂќР В Р’В»Р РЋР вЂ№Р РЋРІР‚РЋР В Р’ВµР В Р вЂ¦Р В РЎвЂР РЋР РЏ Р В РЎвЂўР РЋРІР‚РЋР В РЎвЂР РЋРІР‚В°Р В Р’ВµР В Р вЂ¦Р РЋРІР‚в„–, Р В Р вЂ¦Р В РЎвЂў Р В Р вЂ Р РЋР С“Р РЋРІР‚С™Р РЋР вЂљР В РЎвЂўР В Р’ВµР В Р вЂ¦Р В Р вЂ¦Р В РЎвЂўР В Р’Вµ Р РЋРІР‚В¦Р РЋР вЂљР В Р’В°Р В Р вЂ¦Р В РЎвЂР В Р’В»Р В РЎвЂР РЋРІР‚В°Р В Р’Вµ Р В Р вЂ¦Р В Р’Вµ Р РЋРЎвЂњР В РўвЂР В Р’В°Р В Р’В»Р В РЎвЂўР РЋР С“Р РЋР Р‰ Р В РЎвЂ”Р В РЎвЂўР В РўвЂР В РЎвЂ“Р В РЎвЂўР РЋРІР‚С™Р В РЎвЂўР В Р вЂ Р В РЎвЂР РЋРІР‚С™Р РЋР Р‰: "
                            + safeMessage(exception),
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void resyncActiveSource() {
        NoteChangeMonitor.ensureScheduled(this);
        NoteChangeMonitor.syncNow(this, true);
    }

    private String safeMessage(Exception exception) {
        String message = exception == null ? null : exception.getMessage();
        return message == null || message.trim().isEmpty()
                ? "Р В РЎвЂ”Р РЋР вЂљР В РЎвЂўР В Р вЂ Р В Р’ВµР РЋР вЂљР РЋР Р‰Р РЋРІР‚С™Р В Р’Вµ Р В РўвЂР В РЎвЂўР РЋР С“Р РЋРІР‚С™Р РЋРЎвЂњР В РЎвЂ” Р В РЎвЂќ Р В Р вЂ Р РЋРІР‚в„–Р В Р’В±Р РЋР вЂљР В Р’В°Р В Р вЂ¦Р В Р вЂ¦Р В РЎвЂўР В РЎВР РЋРЎвЂњ Р В РЎвЂР РЋР С“Р РЋРІР‚С™Р В РЎвЂўР РЋРІР‚РЋР В Р вЂ¦Р В РЎвЂР В РЎвЂќР РЋРЎвЂњ"
                : message;
    }

    private TextView createSectionTitle(String text) {
        TextView title = createText(text, 17, R.color.text_primary, true);
        title.setPadding(0, dp(14), 0, dp(8));
        return title;
    }

    private TextView createSubsectionTitle(String text) {
        TextView title = createText(text, 13, R.color.text_secondary, true);
        title.setPadding(0, dp(4), 0, dp(8));
        return title;
    }

    private TextView createSectionHint(String text) {
        TextView hint = createText(text, 13, R.color.text_secondary, false);
        hint.setPadding(0, 0, 0, dp(2));
        return hint;
    }

    private void bindChoiceCardState(LinearLayout card, TextView marker, boolean selected) {
        if (card == null || marker == null) {
            return;
        }
        card.setBackground(createRoundedBackground(
                getColor(R.color.card_background),
                getColor(selected ? R.color.chip_selected_stroke : R.color.card_stroke),
                8
        ));
        marker.setText(selected ? "Р В РЎвЂ™Р В РЎвЂќР РЋРІР‚С™Р В РЎвЂР В Р вЂ Р В Р вЂ¦Р В РЎвЂў" : "");
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

    private void applyWindowInsets(LinearLayout root) {
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int topInset = insets == null ? 0 : insets.getSystemWindowInsetTop();
            int bottomInset = insets == null ? 0 : insets.getSystemWindowInsetBottom();
            view.setPadding(
                    dp(16),
                    dp(14) + topInset,
                    dp(16),
                    dp(20) + bottomInset
            );
            return insets;
        });
        root.requestApplyInsets();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static final class ChoiceCardViews {
        private final LinearLayout card;
        private final TextView marker;

        private ChoiceCardViews(LinearLayout card, TextView marker) {
            this.card = card;
            this.marker = marker;
        }
    }
}
