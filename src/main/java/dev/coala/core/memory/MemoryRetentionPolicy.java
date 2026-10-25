package dev.coala.core.memory;

import java.time.Instant;
import java.util.List;

/** Opt-in record retention/forgetting policy; memory stores call this before/after writes. */
public interface MemoryRetentionPolicy {
    boolean retain(MemoryRecord record, Instant now);
    default List<MemoryRecord> prune(List<MemoryRecord> records, Instant now) { return List.copyOf(records); }
    static MemoryRetentionPolicy keepAll() { return (record, now) -> true; }
}
