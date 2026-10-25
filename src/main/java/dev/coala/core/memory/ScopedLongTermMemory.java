package dev.coala.core.memory;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Enforces a namespace at the memory boundary instead of relying on callers to filter after retrieval. */
public final class ScopedLongTermMemory implements LongTermMemory {
    private final LongTermMemory delegate;
    private final MemoryNamespace namespace;
    private final MemoryRetentionPolicy retentionPolicy;
    public ScopedLongTermMemory(LongTermMemory delegate, MemoryNamespace namespace, MemoryRetentionPolicy retentionPolicy) {
        this.delegate = Objects.requireNonNull(delegate, "delegate"); this.namespace = Objects.requireNonNull(namespace, "namespace");
        this.retentionPolicy = retentionPolicy == null ? MemoryRetentionPolicy.keepAll() : retentionPolicy;
    }
    @Override public List<MemoryRecord> retrieve(MemoryType type, String query, int limit) {
        return delegate.retrieve(new MemoryQuery(query, java.util.Set.of(type), Math.max(1, Math.min(limit, 500)), namespace, null, null));
    }
    @Override public List<MemoryRecord> retrieve(MemoryQuery query) {
        MemoryQuery scoped = new MemoryQuery(query.text(), query.types(), query.limit(), namespace, query.createdAfter(), query.createdBefore());
        return delegate.retrieve(scoped);
    }
    @Override public void store(MemoryRecord record) {
        Objects.requireNonNull(record, "record");
        MemoryRecord scoped = record.withNamespace(namespace);
        Instant now = Instant.now();
        if (!retentionPolicy.retain(scoped, now)) return;
        delegate.store(scoped);
        // Prune the full scoped set together. Calling prune once per record would never
        // enforce policies such as a maximum record count.
        List<MemoryRecord> current = delegate.list(namespace, 100_000);
        java.util.Set<UUID> retainedIds = retentionPolicy.prune(current, now).stream()
                .map(MemoryRecord::id).collect(java.util.stream.Collectors.toSet());
        for (MemoryRecord existing : current) {
            if (!retainedIds.contains(existing.id())) delegate.delete(existing.id(), namespace);
        }
    }
    @Override public boolean delete(UUID id, MemoryNamespace requested) {
        if (requested == null || !namespace.sameAgentSession(requested)) return false;
        return delegate.delete(id, namespace);
    }
    public MemoryNamespace namespace() { return namespace; }
}
