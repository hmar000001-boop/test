package dev.coala.core.planning.search;

import dev.coala.core.ActionCall;

import java.util.ArrayList;
import java.util.List;

/** Tree node separates simulated state from executable action calls. */
public final class SearchNode<S> {
    private final S state;
    private final SearchNode<S> parent;
    private final ActionCall incomingAction;
    private final ActionCall firstAction;
    private final double pathReward;
    private final int depth;
    private final boolean terminal;
    private final List<SearchNode<S>> children = new ArrayList<>();
    private boolean expanded;
    private int visits;
    private double valueSum;

    public SearchNode(S state, SearchNode<S> parent, ActionCall incomingAction, double pathReward, int depth, boolean terminal) {
        this.state = java.util.Objects.requireNonNull(state);
        if (!Double.isFinite(pathReward)) throw new IllegalArgumentException("pathReward must be finite");
        if (depth < 0) throw new IllegalArgumentException("depth must be non-negative");
        if ((parent == null) != (incomingAction == null)) throw new IllegalArgumentException("root has no incoming action and non-root nodes require one");
        this.parent = parent; this.incomingAction = incomingAction;
        this.firstAction = parent == null ? null : (parent.firstAction == null ? incomingAction : parent.firstAction);
        this.pathReward = pathReward; this.depth = depth; this.terminal = terminal;
    }
    public S state() { return state; }
    public SearchNode<S> parent() { return parent; }
    public ActionCall incomingAction() { return incomingAction; }
    public ActionCall firstAction() { return firstAction; }
    public double pathReward() { return pathReward; }
    public int depth() { return depth; }
    public boolean terminal() { return terminal; }
    public List<SearchNode<S>> children() { return List.copyOf(children); }
    public boolean expanded() { return expanded; }
    public int visits() { return visits; }
    public double valueSum() { return valueSum; }
    public double meanValue() { return visits == 0 ? 0.0 : valueSum / visits; }
    void addChild(SearchNode<S> child) { children.add(child); }
    void markExpanded() { expanded = true; }
    void backpropagate(double value) {
        if (!Double.isFinite(value) || !Double.isFinite(valueSum + value))
            throw new IllegalStateException("MCTS backpropagation value overflowed or was non-finite");
        visits++;
        valueSum += value;
    }
}
