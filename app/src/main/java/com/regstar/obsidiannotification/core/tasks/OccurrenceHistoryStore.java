package com.regstar.obsidiannotification.core.tasks;

import com.regstar.obsidiannotification.core.reminders.*;
import com.regstar.obsidiannotification.core.source.*;
import com.regstar.obsidiannotification.prefs.*;
import com.regstar.obsidiannotification.support.ErrorLog;

import android.content.Context;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Local occurrence history for repeat series. Markdown remains the single source
 * for the current head occurrence only; past occurrences live here.
 */
public final class OccurrenceHistoryStore {
    private static final String PREFS_NAME = "obsidian_notification_occurrence_history";
    private static final String KEY_HISTORY = "history";
    private static final String KEY_PENDING = "pending_external";
    private static final Duration DEFAULT_STABILIZATION_WINDOW = Duration.ofSeconds(45);
    private static final Object HISTORY_LOCK = new Object();

    private OccurrenceHistoryStore() {
    }

    public static void append(Context context, TaskOccurrenceRecord record) {
        if (context == null || record == null || record.getSeriesId().trim().isEmpty()) {
            return;
        }
        synchronized (HISTORY_LOCK) {
            List<TaskOccurrenceRecord> records = loadAll(context);
            records.add(record);
            saveAll(context, records);
        }
    }

    public static List<TaskOccurrenceRecord> getSeriesHistory(Context context, String seriesId) {
        if (seriesId == null || seriesId.trim().isEmpty()) {
            return Collections.emptyList();
        }
        synchronized (HISTORY_LOCK) {
            List<TaskOccurrenceRecord> result = new ArrayList<>();
            for (TaskOccurrenceRecord record : loadAll(context)) {
                if (seriesId.equals(record.getSeriesId())) {
                    result.add(record);
                }
            }
            return result;
        }
    }

    public static PendingExternalCompletion getPendingExternalCompletion(
            Context context,
            String seriesId
    ) {
        if (seriesId == null || seriesId.trim().isEmpty()) {
            return null;
        }
        synchronized (HISTORY_LOCK) {
            for (PendingExternalCompletion candidate : loadPending(context)) {
                if (seriesId.equals(candidate.getSeriesId())) {
                    return candidate;
                }
            }
            return null;
        }
    }

    public static void putPendingExternalCompletion(
            Context context,
            PendingExternalCompletion candidate
    ) {
        if (context == null || candidate == null || candidate.getSeriesId().trim().isEmpty()) {
            return;
        }
        synchronized (HISTORY_LOCK) {
            List<PendingExternalCompletion> candidates = loadPending(context);
            candidates.removeIf(value -> candidate.getSeriesId().equals(value.getSeriesId()));
            candidates.add(candidate);
            savePending(context, candidates);
        }
    }

    public static void removePendingExternalCompletion(Context context, String seriesId) {
        if (context == null || seriesId == null || seriesId.trim().isEmpty()) {
            return;
        }
        synchronized (HISTORY_LOCK) {
            List<PendingExternalCompletion> candidates = loadPending(context);
            if (candidates.removeIf(value -> seriesId.equals(value.getSeriesId()))) {
                savePending(context, candidates);
            }
        }
    }

    public static Duration stabilizationWindow() {
        return DEFAULT_STABILIZATION_WINDOW;
    }

    private static List<TaskOccurrenceRecord> loadAll(Context context) {
        String raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_HISTORY, "");
        List<TaskOccurrenceRecord> records = new ArrayList<>();
        if (raw == null || raw.trim().isEmpty()) {
            return records;
        }
        for (String line : raw.split("\\n")) {
            if (line.trim().isEmpty()) {
                continue;
            }
            TaskOccurrenceRecord record = TaskOccurrenceRecord.decode(line);
            if (record != null) {
                records.add(record);
            }
        }
        return records;
    }

    private static void saveAll(Context context, List<TaskOccurrenceRecord> records) {
        StringBuilder builder = new StringBuilder();
        for (TaskOccurrenceRecord record : records) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(record.encode());
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_HISTORY, builder.toString())
                .apply();
    }

    private static List<PendingExternalCompletion> loadPending(Context context) {
        String raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_PENDING, "");
        List<PendingExternalCompletion> candidates = new ArrayList<>();
        if (raw == null || raw.trim().isEmpty()) {
            return candidates;
        }
        for (String line : raw.split("\\n")) {
            if (line.trim().isEmpty()) {
                continue;
            }
            PendingExternalCompletion candidate = PendingExternalCompletion.decode(line);
            if (candidate != null) {
                candidates.add(candidate);
            }
        }
        return candidates;
    }

    private static void savePending(Context context, List<PendingExternalCompletion> candidates) {
        StringBuilder builder = new StringBuilder();
        for (PendingExternalCompletion candidate : candidates) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(candidate.encode());
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_PENDING, builder.toString())
                .apply();
    }

    public static final class PendingExternalCompletion {
        private final String seriesId;
        private final String taskKey;
        private final String rawLineSnapshot;
        private final OccurrenceStatus candidateStatus;
        private final LocalDateTime occurrenceDueAt;
        private final LocalDateTime detectedAt;

        public PendingExternalCompletion(
                String seriesId,
                String taskKey,
                String rawLineSnapshot,
                OccurrenceStatus candidateStatus,
                LocalDateTime occurrenceDueAt,
                LocalDateTime detectedAt
        ) {
            this.seriesId = seriesId == null ? "" : seriesId;
            this.taskKey = taskKey == null ? "" : taskKey;
            this.rawLineSnapshot = rawLineSnapshot == null ? "" : rawLineSnapshot;
            this.candidateStatus = candidateStatus == null ? OccurrenceStatus.COMPLETED : candidateStatus;
            this.occurrenceDueAt = occurrenceDueAt;
            this.detectedAt = detectedAt == null ? LocalDateTime.now() : detectedAt;
        }

        public String getSeriesId() {
            return seriesId;
        }

        public String getTaskKey() {
            return taskKey;
        }

        public String getRawLineSnapshot() {
            return rawLineSnapshot;
        }

        public OccurrenceStatus getCandidateStatus() {
            return candidateStatus;
        }

        public LocalDateTime getOccurrenceDueAt() {
            return occurrenceDueAt;
        }

        public LocalDateTime getDetectedAt() {
            return detectedAt;
        }

        public String encode() {
            return new TaskOccurrenceRecord(
                    seriesId,
                    occurrenceDueAt,
                    candidateStatus,
                    detectedAt,
                    taskKey,
                    rawLineSnapshot,
                    "",
                    Collections.emptyList(),
                    TaskPriority.NONE,
                    0
            ).encode();
        }

        public static PendingExternalCompletion decode(String encoded) {
            TaskOccurrenceRecord record = TaskOccurrenceRecord.decode(encoded);
            if (record == null) {
                return null;
            }
            return new PendingExternalCompletion(
                    record.getSeriesId(),
                    record.getSourceName(),
                    record.getTaskTitleSnapshot(),
                    record.getOccurrenceStatus(),
                    record.getOccurrenceDueAt(),
                    record.getResolvedAt()
            );
        }
    }
}
