package dev.coala.core.memory;

import dev.coala.core.AgentContext;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Projects retrieved records to a bounded structured working-memory value without losing provenance. */
public final class MemoryContextAssembler {
    public record Limits(int maxRecords, int maxCharacters) {
        public Limits { if (maxRecords < 1 || maxRecords > 100) throw new IllegalArgumentException("maxRecords must be 1..100"); if (maxCharacters < 256 || maxCharacters > 64_000) throw new IllegalArgumentException("maxCharacters must be 256..64000"); }
        public static Limits defaults() { return new Limits(12, 8_000); }
    }
    private final Limits limits;
    public MemoryContextAssembler() { this(Limits.defaults()); }
    public MemoryContextAssembler(Limits limits) { this.limits = java.util.Objects.requireNonNull(limits); }

    public List<Map<String, Object>> assemble(List<ScoredMemory> memories, AgentContext context) {
        List<Map<String, Object>> selected = new java.util.ArrayList<>();
        int chars = 0;
        if (memories != null) for (ScoredMemory item : memories) {
            if (selected.size() >= limits.maxRecords()) break;
            MemoryRecord r = item.record();
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("id", r.id().toString()); view.put("type", r.type().name());
            view.put("createdAt", r.createdAt().toString()); view.put("content", r.content());
            view.put("score", item.score()); view.put("retrievalReason", item.reason());
            view.put("importance", r.importance()); view.put("metadata", r.metadata());
            int size = view.toString().length();
            if (chars + size > limits.maxCharacters()) continue;
            selected.add(Map.copyOf(view)); chars += size;
        }
        context.workingMemory().put("retrieved_memories", selected);
        context.workingMemory().put("retrieved_memory_count", selected.size());
        return List.copyOf(selected);
    }
}
