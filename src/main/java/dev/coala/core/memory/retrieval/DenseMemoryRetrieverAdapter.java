package dev.coala.core.memory.retrieval;

import dev.coala.core.memory.LongTermMemory;
import dev.coala.core.memory.MemoryQuery;
import dev.coala.core.memory.ScoredMemory;

import java.util.Comparator;
import java.util.List;

/** Dense retrieval composition over independently configured embedding and vector-index ports. */
public final class DenseMemoryRetrieverAdapter implements DenseMemoryRetriever {
    private final EmbeddingProvider embeddings;
    private final VectorMemoryIndex index;
    public DenseMemoryRetrieverAdapter(EmbeddingProvider embeddings, VectorMemoryIndex index) {
        this.embeddings = java.util.Objects.requireNonNull(embeddings); this.index = java.util.Objects.requireNonNull(index);
    }
    @Override public List<ScoredMemory> retrieve(MemoryQuery query, LongTermMemory memory) {
        if (query.text().isBlank()) return new LexicalMemoryRetriever().retrieve(query, memory);
        float[] vector = embeddings.embed(query.text());
        if (vector == null || vector.length == 0 || vector.length > 65_536) throw new IllegalStateException("Embedding provider returned invalid vector length");
        for (float value : vector) if (!Float.isFinite(value)) throw new IllegalStateException("Embedding contains a non-finite value");
        List<VectorMemoryIndex.VectorMatch> matches = index.search(vector, query);
        if (matches == null) return List.of();
        return matches.stream().filter(match -> query.types().contains(match.record().type()))
                .filter(match -> query.namespace() == null || query.namespace().sameAgentSession(match.record().namespace()))
                .filter(match -> query.createdAfter() == null || !match.record().createdAt().isBefore(query.createdAfter()))
                .filter(match -> query.createdBefore() == null || !match.record().createdAt().isAfter(query.createdBefore()))
                .sorted(Comparator.comparingDouble(VectorMemoryIndex.VectorMatch::similarity).reversed())
                .limit(query.limit()).map(match -> new ScoredMemory(match.record(), match.similarity(), match.reason())).toList();
    }
}
