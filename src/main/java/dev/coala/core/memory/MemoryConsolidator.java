package dev.coala.core.memory;

import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Conservative exact-text consolidation; it does not infer equivalence between semantically different facts. */
public final class MemoryConsolidator {
    public record Result(int recordsExamined, int duplicateRecordsRemoved, int recordsStored) { }
    public Result consolidate(LongTermMemory memory, MemoryNamespace namespace, int maxRecords) {
        if (memory == null || namespace == null) throw new IllegalArgumentException("memory and namespace are required");
        if (maxRecords < 1) throw new IllegalArgumentException("maxRecords must be positive");
        List<MemoryRecord> records = memory.list(namespace, Math.min(100_000, maxRecords));
        Map<String, List<MemoryRecord>> groups = new HashMap<>();
        for (MemoryRecord record : records) {
            String key = normalize(record.content());
            groups.computeIfAbsent(record.type() + ":" + key, ignored -> new ArrayList<>()).add(record);
        }
        int removed = 0, stored = 0;
        for (List<MemoryRecord> group : groups.values()) {
            if (group.size() < 2) continue;
            MemoryRecord representative = group.stream().max(Comparator.comparingDouble(MemoryRecord::importance)
                    .thenComparing(MemoryRecord::createdAt)).orElseThrow();
            Map<String, String> metadata = new HashMap<>(representative.metadata());
            metadata.put("consolidatedCount", Integer.toString(group.size()));
            MemoryRecord consolidated = new MemoryRecord(UUID.randomUUID(), representative.type(), representative.content(), Instant.now(),
                    namespace, metadata, representative.importance());
            // Store first; if the storage backend fails, existing records remain intact.
            memory.store(consolidated);
            int deletedFromGroup = 0;
            for (MemoryRecord item : group) if (memory.delete(item.id(), namespace)) deletedFromGroup++;
            removed += deletedFromGroup;
            stored++;
        }
        return new Result(records.size(), removed, stored);
    }
    private static String normalize(String content) {
        return Normalizer.normalize(content, Normalizer.Form.NFKC).toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[\\p{Punct}\\s]+", " ").trim();
    }
}
