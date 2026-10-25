package dev.coala.core.action;

import dev.coala.core.AgentContext;
import dev.coala.core.memory.MemoryContextAssembler;
import dev.coala.core.memory.MemoryQuery;
import dev.coala.core.memory.MemoryRecord;
import dev.coala.core.memory.MemoryType;
import dev.coala.core.memory.ScoredMemory;
import dev.coala.core.memory.retrieval.LexicalMemoryRetriever;
import dev.coala.core.memory.retrieval.MemoryRetriever;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Internal retrieval action: brings scored, provenance-preserving records into working memory. */
public final class MemoryRetrievalAction implements RetrievalAction {
    private final MemoryRetriever retriever;
    private final MemoryContextAssembler assembler;

    public MemoryRetrievalAction() { this(new LexicalMemoryRetriever(), new MemoryContextAssembler()); }
    public MemoryRetrievalAction(MemoryRetriever retriever, MemoryContextAssembler assembler) {
        this.retriever = java.util.Objects.requireNonNull(retriever, "retriever");
        this.assembler = java.util.Objects.requireNonNull(assembler, "assembler");
    }

    @Override public List<MemoryRecord> retrieve(String query, AgentContext context, int limitPerType) {
        if (limitPerType < 1) return List.of();
        List<ScoredMemory> found = new ArrayList<>();
        for (MemoryType type : List.of(MemoryType.EPISODIC, MemoryType.SEMANTIC)) {
            MemoryQuery request = new MemoryQuery(query, Set.of(type), Math.min(limitPerType, 100), context.namespace(), null, null);
            found.addAll(retriever.retrieve(request, context.longTermMemory()));
        }
        found.sort(java.util.Comparator.comparingDouble(ScoredMemory::score).reversed()
                .thenComparing(item -> item.record().createdAt(), java.util.Comparator.reverseOrder()));
        List<java.util.Map<String, Object>> assembled = assembler.assemble(found, context);
        java.util.Set<String> includedIds = assembled.stream().map(item -> String.valueOf(item.get("id")))
                .collect(java.util.stream.Collectors.toSet());
        return found.stream().filter(item -> includedIds.contains(item.record().id().toString()))
                .map(ScoredMemory::record).toList();
    }
}
