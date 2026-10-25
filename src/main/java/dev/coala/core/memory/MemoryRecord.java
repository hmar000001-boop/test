package dev.coala.core.memory;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** A record of episodic experience or semantic knowledge. Metadata is declarative, not executable. */
public record MemoryRecord(
        UUID id,
        MemoryType type,
        String content,
        Instant createdAt,
        MemoryNamespace namespace,
        Map<String, String> metadata,
        double importance) {
    public static final int MAX_CONTENT_CHARACTERS = 4_000;
    public static final int MAX_METADATA_ENTRIES = 32;
    public static final int MAX_METADATA_VALUE_CHARACTERS = 500;

    public MemoryRecord {
        id = id == null ? UUID.randomUUID() : id;
        if (type == null) throw new IllegalArgumentException("type is required");
        if (content == null || content.isBlank()) throw new IllegalArgumentException("content is required");
        if (content.length() > MAX_CONTENT_CHARACTERS) throw new IllegalArgumentException("memory content exceeds " + MAX_CONTENT_CHARACTERS + " characters");
        createdAt = createdAt == null ? Instant.now() : createdAt;
        namespace = namespace == null ? MemoryNamespace.DEFAULT : namespace;
        if (!Double.isFinite(importance) || importance < 0 || importance > 1) throw new IllegalArgumentException("importance must be in [0,1]");
        Map<String, String> safe = new LinkedHashMap<>();
        if (metadata != null) {
            if (metadata.size() > MAX_METADATA_ENTRIES) throw new IllegalArgumentException("Too many memory metadata entries");
            metadata.forEach((key, value) -> {
                if (key == null || key.isBlank() || key.length() > 100) throw new IllegalArgumentException("Invalid memory metadata key");
                if (value == null || value.length() > MAX_METADATA_VALUE_CHARACTERS) throw new IllegalArgumentException("Invalid memory metadata value");
                safe.put(key, value);
            });
        }
        metadata = Map.copyOf(safe);
    }

    /** Backwards-compatible constructor. */
    public MemoryRecord(UUID id, MemoryType type, String content, Instant createdAt) {
        this(id, type, content, createdAt, MemoryNamespace.DEFAULT, Map.of(), 0.5);
    }

    public static MemoryRecord of(MemoryType type, String content) {
        return new MemoryRecord(null, type, content, null, MemoryNamespace.DEFAULT, Map.of(), 0.5);
    }
    public static MemoryRecord of(MemoryType type, String content, MemoryNamespace namespace, Map<String, String> metadata, double importance) {
        return new MemoryRecord(null, type, content, null, namespace, metadata, importance);
    }
    public MemoryRecord withNamespace(MemoryNamespace value) { return new MemoryRecord(id, type, content, createdAt, value, metadata, importance); }
    public MemoryRecord withMetadata(Map<String, String> value) { return new MemoryRecord(id, type, content, createdAt, namespace, value, importance); }
}
