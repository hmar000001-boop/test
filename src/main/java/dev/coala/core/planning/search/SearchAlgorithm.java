package dev.coala.core.planning.search;

import dev.coala.core.ActionRegistry;
import dev.coala.core.AgentContext;

@FunctionalInterface
public interface SearchAlgorithm<S> {
    SearchResult<S> search(S initialState, SearchDomain<S> domain, AgentContext context,
                           ActionRegistry actions, SearchBudget budget);
    default String id() { return getClass().getSimpleName(); }
}
