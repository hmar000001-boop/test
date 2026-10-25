package dev.coala.core.memory.retrieval;

import dev.coala.core.memory.LongTermMemory;
import dev.coala.core.memory.MemoryQuery;
import dev.coala.core.memory.ScoredMemory;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Combines lexical and dense scores with deduplication; dense provider is optional and pluggable. */
public final class HybridMemoryRetriever implements MemoryRetriever {
    private final MemoryRetriever lexical;
    private final MemoryRetriever dense;
    private final double lexicalWeight;
    private final double denseWeight;
    public HybridMemoryRetriever(MemoryRetriever lexical, MemoryRetriever dense, double lexicalWeight, double denseWeight) {
        if (lexical == null) throw new IllegalArgumentException("lexical retriever is required");
        if (lexicalWeight < 0 || denseWeight < 0 || lexicalWeight + denseWeight <= 0
                || !Double.isFinite(lexicalWeight + denseWeight)) throw new IllegalArgumentException("weights must be finite and non-negative");
        if (denseWeight > 0 && dense == null) throw new IllegalArgumentException("dense retriever is required when denseWeight > 0");
        this.lexical = lexical; this.dense = dense; this.lexicalWeight = lexicalWeight; this.denseWeight = denseWeight;
    }
    @Override public java.util.List<ScoredMemory> retrieve(MemoryQuery query, LongTermMemory memory) {
        Map<java.util.UUID, Aggregate> merged = new LinkedHashMap<>();
        lexical.retrieve(query, memory).forEach(item -> merged.computeIfAbsent(item.record().id(), id -> new Aggregate(item.record()))
                .add(item.score() * lexicalWeight, lexicalWeight, "lexical:" + item.reason()));
        if (dense != null && denseWeight > 0) dense.retrieve(query, memory).forEach(item -> merged.computeIfAbsent(item.record().id(), id -> new Aggregate(item.record()))
                .add(item.score() * denseWeight, denseWeight, "dense:" + item.reason()));
        return merged.values().stream().map(Aggregate::result).sorted(Comparator.comparingDouble(ScoredMemory::score).reversed()
                .thenComparing(s -> s.record().createdAt(), Comparator.reverseOrder())).limit(query.limit()).toList();
    }
    private static final class Aggregate {
        final dev.coala.core.memory.MemoryRecord record; double weightedScore; double totalWeight; final java.util.List<String> reasons = new java.util.ArrayList<>();
        Aggregate(dev.coala.core.memory.MemoryRecord record) { this.record = record; }
        void add(double weighted, double weight, String reason) { weightedScore += weighted; totalWeight += weight; reasons.add(reason); }
        ScoredMemory result() { return new ScoredMemory(record, totalWeight == 0 ? 0 : weightedScore / totalWeight, String.join("; ", reasons)); }
    }
}
