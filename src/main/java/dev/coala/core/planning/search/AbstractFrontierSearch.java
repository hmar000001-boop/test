package dev.coala.core.planning.search;

import dev.coala.core.ActionKind;
import dev.coala.core.ActionRegistry;
import dev.coala.core.AgentContext;
import dev.coala.core.CoalaAction;

import java.util.ArrayDeque;
import java.util.Deque;

/** Shared bounded frontier expansion for BFS and DFS. */
abstract class AbstractFrontierSearch<S> implements SearchAlgorithm<S> {
    protected abstract Deque<SearchNode<S>> createFrontier();
    protected abstract void push(Deque<SearchNode<S>> frontier, SearchNode<S> node);

    @Override
    public SearchResult<S> search(S initialState, SearchDomain<S> domain, AgentContext context,
                                  ActionRegistry actions, SearchBudget budget) {
        if (initialState == null || domain == null || context == null || actions == null || budget == null)
            throw new IllegalArgumentException("initialState, domain, context, actions and budget are required");
        SearchNode<S> root = new SearchNode<>(initialState, null, null, 0, 0, domain.terminal(initialState, context));
        Deque<SearchNode<S>> frontier = createFrontier();
        frontier.add(root);
        SearchNode<S> best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        int expandedNodes = 0;
        int iterations = 0;
        int nodesCreated = 1; // The root counts against maxNodes.

        while (!frontier.isEmpty()) {
            SearchNode<S> node = frontier.removeFirst();
            iterations++;
            if (node.firstAction() != null) {
                double score = finite(node.pathReward() + finite(domain.evaluate(node.state(), context)));
                if (best == null || score > bestScore) {
                    best = node;
                    bestScore = score;
                }
            }
            if (node.depth() >= budget.maxDepth() || node.terminal() || nodesCreated >= budget.maxNodes()) continue;

            var transitions = domain.expand(node.state(), context, actions);
            node.markExpanded();
            expandedNodes++;
            if (transitions == null || transitions.isEmpty()) continue;

            // Only materialize as many children as remain in the tree-node budget.
            for (var transition : transitions) {
                if (nodesCreated >= budget.maxNodes()) break;
                if (transition == null) throw new IllegalArgumentException("Search domain returned a null transition");
                validateAction(transition.action(), actions);
                SearchNode<S> child = new SearchNode<>(transition.nextState(), node, transition.action(),
                        finite(node.pathReward() + transition.reward()), node.depth() + 1,
                        transition.terminal() || domain.terminal(transition.nextState(), context));
                node.addChild(child);
                nodesCreated++;
                push(frontier, child);
            }
        }
        if (best == null) throw new IllegalStateException("Search produced no executable first action within its budget");
        return new SearchResult<>(best, expandedNodes, iterations, bestScore, id());
    }

    protected static void validateAction(dev.coala.core.ActionCall call, ActionRegistry actions) {
        CoalaAction action = actions.validate(call);
        if (action.kind() != ActionKind.GROUNDING && action.kind() != ActionKind.LEARNING)
            throw new IllegalArgumentException("Search transition must select grounding/learning action");
    }
    protected static double finite(double score) {
        if (!Double.isFinite(score)) throw new IllegalStateException("Search domain returned non-finite evaluation");
        return score;
    }
}
