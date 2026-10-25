package dev.coala.core.memory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public final class MaxAgeMemoryRetentionPolicy implements MemoryRetentionPolicy {
    private final Duration maxAge;
    private final int maxRecords;
    public MaxAgeMemoryRetentionPolicy(Duration maxAge, int maxRecords) {
        if (maxAge == null || maxAge.isNegative() || maxAge.isZero()) throw new IllegalArgumentException("maxAge must be positive");
        if (maxRecords < 1 || maxRecords > 100_000) throw new IllegalArgumentException("maxRecords must be 1..100000");
        this.maxAge = maxAge; this.maxRecords = maxRecords;
    }
    @Override public boolean retain(MemoryRecord record, Instant now) { return !record.createdAt().isBefore(now.minus(maxAge)); }
    @Override public List<MemoryRecord> prune(List<MemoryRecord> records, Instant now) {
        return records.stream().filter(r -> retain(r, now)).sorted(java.util.Comparator.comparing(MemoryRecord::createdAt).reversed())
                .limit(maxRecords).toList();
    }
}
