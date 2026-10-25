package dev.coala.core.planning.search;

import dev.coala.core.ActionRegistry;
import dev.coala.core.AgentContext;

import java.util.List;
import java.util.Random;

/** Budgeted MCTS for domains that can safely simulate transitions without external side effects. */
public final class MonteCarloTreeSearch<S> implements SearchAlgorithm<S> {
    private final double explorationConstant;
    private final Random random;

    public MonteCarloTreeSearch(double explorationConstant, long seed) {
        if (!Double.isFinite(explorationConstant) || explorationConstant < 0)
            throw new IllegalArgumentException("explorationConstant must be non-negative");
        this.explorationConstant = explorationConstant;
        this.random = new Random(seed);
    }

    @Override public String id() { return "mcts"; }

    @Override
    public SearchResult<S> search(S initialState, SearchDomain<S> domain, AgentContext context,
                                  ActionRegistry actions, SearchBudget budget) {
        if (initialState == null || domain == null || context == null || actions == null || budget == null)
            throw new IllegalArgumentException("initialState, domain, context, actions and budget are required");
        SearchNode<S> root = new SearchNode<>(initialState, null, null, 0, 0, domain.terminal(initialState, context));
        int nodesCreated = 1; // Includes root and all materialized tree descendants.
        int nodesExpanded = 0;
        int iterations = 0;

        // Iterations are separately bounded by maxNodes; the materialized tree also never exceeds maxNodes.
        for (int iteration = 0; iteration < budget.maxNodes(); iteration++) {
            iterations++;
            SearchNode<S> node = root;
            while (node.expanded() && !node.terminal() && node.depth() < budget.maxDepth()
                    && !node.children().isEmpty()) {
                node = selectUct(node);
            }

            if (!node.expanded() && !node.terminal() && node.depth() < budget.maxDepth() && nodesCreated < budget.maxNodes()) {
                List<SearchTransition<S>> transitions = domain.expand(node.state(), context, actions);
                node.markExpanded();
                nodesExpanded++;
                if (transitions != null) {
                    for (SearchTransition<S> transition : transitions) {
                        if (nodesCreated >= budget.maxNodes()) break;
                        if (transition == null) throw new IllegalArgumentException("Search domain returned a null transition");
                        AbstractFrontierSearch.validateAction(transition.action(), actions);
                        SearchNode<S> child = new SearchNode<>(transition.nextState(), node, transition.action(),
                                AbstractFrontierSearch.finite(node.pathReward() + transition.reward()), node.depth() + 1,
                                transition.terminal() || domain.terminal(transition.nextState(), context));
                        node.addChild(child);
                        nodesCreated++;
                    }
                    if (!node.children().isEmpty()) {
                        List<SearchNode<S>> children = node.children();
                        node = children.get(random.nextInt(children.size()));
                    }
                }
            }

            double value = rollout(node, domain, context, actions, budget.maxDepth());
            for (SearchNode<S> cursor = node; cursor != null; cursor = cursor.parent()) cursor.backpropagate(value);
        }

        SearchNode<S> selected = root.children().stream()
                .max(java.util.Comparator.comparingInt(SearchNode<S>::visits)
                        .thenComparingDouble(SearchNode<S>::meanValue)).orElse(null);
        if (selected == null) throw new IllegalStateException("MCTS produced no first action within its budget");
        return new SearchResult<>(selected, nodesExpanded, iterations, selected.meanValue(), id());
    }

    private SearchNode<S> selectUct(SearchNode<S> parent) {
        double log = Math.log(Math.max(1, parent.visits()));
        return parent.children().stream().max(java.util.Comparator.comparingDouble(child -> {
            if (child.visits() == 0) return Double.POSITIVE_INFINITY;
            return child.meanValue() + explorationConstant * Math.sqrt(log / child.visits());
        })).orElseThrow();
    }

    private double rollout(SearchNode<S> start, SearchDomain<S> domain, AgentContext context,
                           ActionRegistry actions, int maxDepth) {
        SearchNode<S> current = start;
        // Rollout state is transient; its depth is hard-bounded and it creates no persistent tree nodes.
        while (!current.terminal() && current.depth() < maxDepth) {
            List<SearchTransition<S>> transitions = domain.expand(current.state(), context, actions);
            if (transitions == null || transitions.isEmpty()) break;
            SearchTransition<S> transition = transitions.get(random.nextInt(transitions.size()));
            if (transition == null) throw new IllegalArgumentException("Search domain returned a null transition");
            AbstractFrontierSearch.validateAction(transition.action(), actions);
            current = new SearchNode<>(transition.nextState(), current, transition.action(),
                    AbstractFrontierSearch.finite(current.pathReward() + transition.reward()), current.depth() + 1,
                    transition.terminal() || domain.terminal(transition.nextState(), context));
        }
        double value = AbstractFrontierSearch.finite(current.pathReward() + AbstractFrontierSearch.finite(domain.evaluate(current.state(), context)));
        return value;
    }
}
