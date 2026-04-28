package com.regstar.obsidiannotification;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

public final class TaskOccurrenceRecord {
    private final String seriesId;
    private final LocalDateTime occurrenceDueAt;
    private final OccurrenceStatus occurrenceStatus;
    private final LocalDateTime resolvedAt;
    private final String sourceName;
    private final String taskTitleSnapshot;
    private final String groupSnapshot;
    private final List<String> tagsSnapshot;
    private final TaskPriority prioritySnapshot;
    private final int lineNumberSnapshot;

    public TaskOccurrenceRecord(
            String seriesId,
            LocalDateTime occurrenceDueAt,
            OccurrenceStatus occurrenceStatus,
            LocalDateTime resolvedAt,
            String sourceName,
            String taskTitleSnapshot,
            String groupSnapshot,
            List<String> tagsSnapshot,
            TaskPriority prioritySnapshot,
            int lineNumberSnapshot
    ) {
        this.seriesId = seriesId == null ? "" : seriesId;
        this.occurrenceDueAt = occurrenceDueAt;
        this.occurrenceStatus = occurrenceStatus == null ? OccurrenceStatus.COMPLETED : occurrenceStatus;
        this.resolvedAt = resolvedAt;
        this.sourceName = sourceName == null ? "" : sourceName;
        this.taskTitleSnapshot = taskTitleSnapshot == null ? "" : taskTitleSnapshot;
        this.groupSnapshot = groupSnapshot == null ? ObsidianTask.DEFAULT_GROUP : groupSnapshot;
        this.tagsSnapshot = Collections.unmodifiableList(new ArrayList<>(tagsSnapshot == null
                ? Collections.emptyList()
                : tagsSnapshot));
        this.prioritySnapshot = prioritySnapshot == null ? TaskPriority.NONE : prioritySnapshot;
        this.lineNumberSnapshot = Math.max(0, lineNumberSnapshot);
    }

    public String getSeriesId() {
        return seriesId;
    }

    public LocalDateTime getOccurrenceDueAt() {
        return occurrenceDueAt;
    }

    public OccurrenceStatus getOccurrenceStatus() {
        return occurrenceStatus;
    }

    public LocalDateTime getResolvedAt() {
        return resolvedAt;
    }

    public String getSourceName() {
        return sourceName;
    }

    public String getTaskTitleSnapshot() {
        return taskTitleSnapshot;
    }

    public String getGroupSnapshot() {
        return groupSnapshot;
    }

    public List<String> getTagsSnapshot() {
        return tagsSnapshot;
    }

    public TaskPriority getPrioritySnapshot() {
        return prioritySnapshot;
    }

    public int getLineNumberSnapshot() {
        return lineNumberSnapshot;
    }

    public String encode() {
        return encodeValue(seriesId)
                + "|"
                + encodeValue(occurrenceDueAt == null ? "" : occurrenceDueAt.toString())
                + "|"
                + occurrenceStatus.name()
                + "|"
                + encodeValue(resolvedAt == null ? "" : resolvedAt.toString())
                + "|"
                + encodeValue(sourceName)
                + "|"
                + encodeValue(taskTitleSnapshot)
                + "|"
                + encodeValue(groupSnapshot)
                + "|"
                + encodeValue(join(tagsSnapshot))
                + "|"
                + prioritySnapshot.name()
                + "|"
                + lineNumberSnapshot;
    }

    public static TaskOccurrenceRecord decode(String encoded) {
        String[] parts = encoded.split("\\|", -1);
        if (parts.length != 10) {
            return null;
        }
        try {
            return new TaskOccurrenceRecord(
                    decodeValue(parts[0]),
                    parseDateTime(parts[1]),
                    OccurrenceStatus.fromName(parts[2]),
                    parseDateTime(parts[3]),
                    decodeValue(parts[4]),
                    decodeValue(parts[5]),
                    decodeValue(parts[6]),
                    splitTags(decodeValue(parts[7])),
                    TaskPriority.fromName(parts[8]),
                    Integer.parseInt(parts[9])
            );
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static LocalDateTime parseDateTime(String encoded) {
        String value = decodeValue(encoded);
        return value.isEmpty() ? null : LocalDateTime.parse(value);
    }

    private static String join(List<String> values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(value);
        }
        return builder.toString();
    }

    private static List<String> splitTags(String rawValue) {
        List<String> tags = new ArrayList<>();
        if (rawValue == null || rawValue.trim().isEmpty()) {
            return tags;
        }
        for (String tag : rawValue.split(",")) {
            if (!tag.trim().isEmpty()) {
                tags.add(tag.trim());
            }
        }
        return tags;
    }

    private static String encodeValue(String value) {
        String safeValue = value == null ? "" : value;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                safeValue.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static String decodeValue(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }
}
