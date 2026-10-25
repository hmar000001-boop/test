package dev.coala.core.planning.search;

import java.util.ArrayDeque;
import java.util.Deque;

/** LIFO tree exploration with explicit node/depth budgets. */
public final class DepthFirstSearch<S> extends AbstractFrontierSearch<S> {
    @Override protected Deque<SearchNode<S>> createFrontier() { return new ArrayDeque<>(); }
    @Override protected void push(Deque<SearchNode<S>> frontier, SearchNode<S> node) { frontier.addFirst(node); }
    @Override public String id() { return "dfs"; }
}
