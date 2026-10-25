package dev.coala.core.planning.search;

import dev.coala.core.ActionRegistry;
import dev.coala.core.AgentContext;

import java.util.List;

/** Use-case-specific simulator for search. It must not execute real external side effects during expansion/rollout. */
public interface SearchDomain<S> {
    S initialState(AgentContext context);
    List<SearchTransition<S>> expand(S state, AgentContext context, ActionRegistry allowedActions);
    double evaluate(S state, AgentContext context);
    boolean terminal(S state, AgentContext context);
}
