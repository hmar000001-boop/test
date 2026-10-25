package dev.coala.core.memory;

/** Retrieved memory plus transparent ranking metadata; score is not a calibrated probability. */
public record ScoredMemory(MemoryRecord record, double score, String reason) {
    public ScoredMemory {
        if (record == null) throw new IllegalArgumentException("record is required");
        if (!Double.isFinite(score)) throw new IllegalArgumentException("score must be finite");
        reason = reason == null ? "" : reason;
    }
}
