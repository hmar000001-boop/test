package dev.coala.core.memory.retrieval;

import dev.coala.core.memory.LongTermMemory;
import dev.coala.core.memory.MemoryQuery;
import dev.coala.core.memory.ScoredMemory;

import java.util.List;

/** Port for an embedding/vector-backed retriever; no embedding or vector-store product is assumed. */
@FunctionalInterface
public interface DenseMemoryRetriever extends MemoryRetriever {
    @Override List<ScoredMemory> retrieve(MemoryQuery query, LongTermMemory memory);
}
