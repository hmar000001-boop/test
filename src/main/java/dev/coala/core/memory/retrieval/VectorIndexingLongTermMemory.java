package dev.coala.core.memory.retrieval;

import dev.coala.core.memory.LongTermMemory;
import dev.coala.core.memory.MemoryNamespace;
import dev.coala.core.memory.MemoryQuery;
import dev.coala.core.memory.MemoryRecord;
import dev.coala.core.memory.MemoryType;

import java.util.List;
import java.util.UUID;

/** Decorates a durable/in-memory store to maintain an optional vector index when memories are written/deleted. */
public final class VectorIndexingLongTermMemory implements LongTermMemory {
    private final LongTermMemory delegate;
    private final EmbeddingProvider embeddings;
    private final VectorMemoryIndex index;
    public VectorIndexingLongTermMemory(LongTermMemory delegate, EmbeddingProvider embeddings, VectorMemoryIndex index) {
        this.delegate = java.util.Objects.requireNonNull(delegate); this.embeddings = java.util.Objects.requireNonNull(embeddings); this.index = java.util.Objects.requireNonNull(index);
    }
    @Override public List<MemoryRecord> retrieve(MemoryType type, String query, int limit) { return delegate.retrieve(type, query, limit); }
    @Override public List<MemoryRecord> retrieve(MemoryQuery query) { return delegate.retrieve(query); }
    @Override public synchronized void store(MemoryRecord record) {
        float[] vector = embeddings.embed(record.content());
        if (vector == null || vector.length == 0 || vector.length > 65_536) throw new IllegalStateException("Embedding provider returned invalid vector length");
        for (float value : vector) if (!Float.isFinite(value)) throw new IllegalStateException("Embedding contains non-finite value");
        delegate.store(record); index.upsert(record, vector);
    }
    @Override public List<MemoryRecord> list(MemoryNamespace namespace, int limit) { return delegate.list(namespace, limit); }
    @Override public synchronized boolean delete(UUID id, MemoryNamespace namespace) {
        boolean fromStore = delegate.delete(id, namespace); boolean fromIndex = index.delete(id, namespace); return fromStore || fromIndex;
    }
}
