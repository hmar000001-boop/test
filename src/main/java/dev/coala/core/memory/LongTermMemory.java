package dev.coala.core.memory;

import java.util.List;

/** Storage/learning boundary for long-term episodic and semantic memory. */
public interface LongTermMemory {
    List<MemoryRecord> retrieve(MemoryType type, String query, int limit);
    void store(MemoryRecord record);

    /** Default compatibility implementation; robust adapters should enforce query filters in storage. */
    default List<MemoryRecord> retrieve(MemoryQuery query) {
        return query.types().stream().flatMap(type -> retrieve(type, query.text(), query.limit()).stream())
                .filter(record -> query.namespace() == null || query.namespace().sameAgentSession(record.namespace()))
                .filter(record -> query.createdAfter() == null || !record.createdAt().isBefore(query.createdAfter()))
                .filter(record -> query.createdBefore() == null || !record.createdAt().isAfter(query.createdBefore()))
                .sorted(java.util.Comparator.comparing(MemoryRecord::createdAt).reversed())
                .limit(query.limit()).toList();
    }

    default List<MemoryRecord> list(MemoryNamespace namespace, int limit) {
        if (limit < 1) return List.of();
        return retrieve(new MemoryQuery("", java.util.Set.of(), Math.min(100_000, limit), namespace, null, null));
    }
    default boolean delete(java.util.UUID id, MemoryNamespace namespace) { return false; }
}
