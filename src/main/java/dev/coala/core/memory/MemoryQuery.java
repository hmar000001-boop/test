package dev.coala.core.memory;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

/** Retrieval request separated from storage implementation and scoring policy. */
public record MemoryQuery(String text, Set<MemoryType> types, int limit, MemoryNamespace namespace,
                          Instant createdAfter, Instant createdBefore) {
    public MemoryQuery {
        text = text == null ? "" : text.trim();
        types = types == null || types.isEmpty() ? Set.copyOf(EnumSet.allOf(MemoryType.class)) : Set.copyOf(types);
        if (limit < 1 || limit > 100_000) throw new IllegalArgumentException("limit must be between 1 and 100000");
        if (createdAfter != null && createdBefore != null && createdAfter.isAfter(createdBefore)) throw new IllegalArgumentException("createdAfter must not be after createdBefore");
    }
    public static MemoryQuery text(String query, int limit) { return new MemoryQuery(query, Set.of(), limit, null, null, null); }
    public MemoryQuery inNamespace(MemoryNamespace value) { return new MemoryQuery(text, types, limit, value, createdAfter, createdBefore); }
}
