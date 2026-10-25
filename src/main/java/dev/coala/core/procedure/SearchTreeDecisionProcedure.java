package dev.coala.core.procedure;

import dev.coala.core.ActionCall;
import dev.coala.core.ActionRegistry;
import dev.coala.core.AgentContext;
import dev.coala.core.DecisionEvent;
import dev.coala.core.DecisionStage;
import dev.coala.core.PlanDecision;
import dev.coala.core.PlanningExhaustedException;
import dev.coala.core.planning.search.SearchAlgorithm;
import dev.coala.core.planning.search.SearchBudget;
import dev.coala.core.planning.search.SearchDomain;
import dev.coala.core.planning.search.SearchResult;

import java.util.List;
import java.util.Map;

/** Optional true tree-search template. Its domain must simulate rather than execute real side effects. */
public final class SearchTreeDecisionProcedure<S> extends AbstractLoopingDecisionProcedure {
    private final SearchDomain<S> domain;
    private final SearchAlgorithm<S> algorithm;
    private final SearchBudget budget;
    public SearchTreeDecisionProcedure(SearchDomain<S> domain, SearchAlgorithm<S> algorithm, SearchBudget budget) {
        this.domain = java.util.Objects.requireNonNull(domain); this.algorithm = java.util.Objects.requireNonNull(algorithm);
        this.budget = java.util.Objects.requireNonNull(budget);
    }
    @Override public String id() { return "search-tree-" + algorithm.id(); }
    @Override protected PlanDecision planNextAction(AgentContext context, ActionRegistry actions) {
        S initial = domain.initialState(context);
        SearchResult<S> result = algorithm.search(initial, domain, context, actions, budget);
        ActionCall first = result.selectedNode().firstAction();
        if (first == null) throw new PlanningExhaustedException(result.iterations(), "search selected a root node without a first action", List.of());
        actions.validate(first);
        context.workingMemory().put("search.summary", Map.of("algorithm", result.algorithmId(), "nodesExpanded", result.nodesExpanded(),
                "iterations", result.iterations(), "depth", result.selectedNode().depth(), "score", result.score()));
        List<DecisionEvent> events = List.of(
                new DecisionEvent(context.cycle(), DecisionStage.PROPOSAL, "Search domain explored simulated alternatives using " + result.algorithmId() + "."),
                new DecisionEvent(context.cycle(), DecisionStage.EVALUATION, "Search expanded " + result.nodesExpanded() + " node(s); selected-path score=" + result.score() + "."),
                new DecisionEvent(context.cycle(), DecisionStage.SELECTION, "Selected the first executable action on the best explored path: " + first.actionId() + "."));
        return new PlanDecision(first, List.of(first), List.of(result.score()), events);
    }
}
