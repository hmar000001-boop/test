package dev.coala.core.action;

import dev.coala.core.AgentContext;
import dev.coala.core.memory.MemoryRecord;

import java.util.List;

/**
 * Internal planning operation: retrieve relevant long-term memories into active working memory.
 * This is distinct from a selected grounding/learning action in the outer decision cycle.
 */
public interface RetrievalAction {
    List<MemoryRecord> retrieve(String query, AgentContext context, int limitPerType);
}
