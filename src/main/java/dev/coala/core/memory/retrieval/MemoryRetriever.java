package dev.coala.core.memory.retrieval;

import dev.coala.core.memory.LongTermMemory;
import dev.coala.core.memory.MemoryQuery;
import dev.coala.core.memory.ScoredMemory;

import java.util.List;

/** Retrieval policy independent from the store/backend. */
@FunctionalInterface
public interface MemoryRetriever {
    List<ScoredMemory> retrieve(MemoryQuery query, LongTermMemory memory);
}
