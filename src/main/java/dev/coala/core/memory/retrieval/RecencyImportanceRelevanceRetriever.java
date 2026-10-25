package dev.coala.core.memory.retrieval;

import dev.coala.core.memory.LongTermMemory;
import dev.coala.core.memory.MemoryQuery;
import dev.coala.core.memory.ScoredMemory;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/** Optional combined retrieval policy with independently configurable score factors. */
public final class RecencyImportanceRelevanceRetriever implements MemoryRetriever {
    private final double relevanceWeight, recencyWeight, importanceWeight;
    private final Duration recencyHalfLife;
    public RecencyImportanceRelevanceRetriever(double relevanceWeight, double recencyWeight,
                                               double importanceWeight, Duration recencyHalfLife) {
        if (relevanceWeight < 0 || recencyWeight < 0 || importanceWeight < 0
                || !Double.isFinite(relevanceWeight + recencyWeight + importanceWeight)
                || relevanceWeight + recencyWeight + importanceWeight <= 0)
            throw new IllegalArgumentException("Weights must be finite, non-negative, and not all zero");
        this.relevanceWeight = relevanceWeight; this.recencyWeight = recencyWeight; this.importanceWeight = importanceWeight;
        this.recencyHalfLife = recencyHalfLife == null ? Duration.ofDays(30) : recencyHalfLife;
        if (this.recencyHalfLife.isZero() || this.recencyHalfLife.isNegative()) throw new IllegalArgumentException("recency half-life must be positive");
    }
    @Override public List<ScoredMemory> retrieve(MemoryQuery query, LongTermMemory memory) {
        List<ScoredMemory> lexical = new LexicalMemoryRetriever().retrieve(query, memory);
        double weightSum = relevanceWeight + recencyWeight + importanceWeight;
        Instant now = Instant.now();
        return lexical.stream().map(item -> {
            double ageSeconds = Math.max(0.0, Duration.between(item.record().createdAt(), now).toSeconds());
            double halfLifeSeconds = Math.max(1.0, recencyHalfLife.toSeconds());
            double recency = Math.pow(0.5, ageSeconds / halfLifeSeconds);
            double score = (item.score() * relevanceWeight + recency * recencyWeight
                    + item.record().importance() * importanceWeight) / weightSum;
            return new ScoredMemory(item.record(), score, "weighted relevance=" + item.score()
                    + ", recency=" + recency + ", importance=" + item.record().importance());
        }).sorted(Comparator.comparingDouble(ScoredMemory::score).reversed()
                .thenComparing(s -> s.record().createdAt(), Comparator.reverseOrder())).limit(query.limit()).toList();
    }
}
