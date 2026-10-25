package dev.coala.core.memory.retrieval;

import dev.coala.core.memory.MemoryNamespace;
import dev.coala.core.memory.MemoryQuery;
import dev.coala.core.memory.MemoryRecord;

import java.util.List;
import java.util.UUID;

/** Vector-index port. Implementations must enforce namespace and memory-type filters inside the backend query. */
public interface VectorMemoryIndex {
    void upsert(MemoryRecord record, float[] embedding);
    List<VectorMatch> search(float[] queryEmbedding, MemoryQuery query);
    boolean delete(UUID memoryId, MemoryNamespace namespace);
    record VectorMatch(MemoryRecord record, double similarity, String reason) {
        public VectorMatch {
            if (record == null || !Double.isFinite(similarity)) throw new IllegalArgumentException("Invalid vector match");
            similarity = Math.max(0.0, Math.min(1.0, similarity));
            reason = reason == null ? "dense similarity" : reason;
        }
    }
}
