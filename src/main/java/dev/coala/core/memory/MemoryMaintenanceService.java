package dev.coala.core.memory;

import java.time.Instant;
import java.util.List;

/** Applies an explicitly configured deletion policy to a namespace. */
public final class MemoryMaintenanceService {
    public int deleteMatching(LongTermMemory memory, MemoryNamespace namespace, int scanLimit, MemoryDeletionPolicy policy) {
        if (memory == null || namespace == null || policy == null) throw new IllegalArgumentException("memory, namespace and policy are required");
        int deleted = 0;
        Instant now = Instant.now();
        List<MemoryRecord> records = memory.list(namespace, Math.min(100_000, Math.max(1, scanLimit)));
        for (MemoryRecord record : records) if (policy.shouldDelete(record, now) && memory.delete(record.id(), namespace)) deleted++;
        return deleted;
    }
}
