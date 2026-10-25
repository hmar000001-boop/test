package dev.coala.core.planning.search;

import java.util.ArrayDeque;
import java.util.Deque;

/** FIFO tree exploration with explicit node/depth budgets. */
public final class BreadthFirstSearch<S> extends AbstractFrontierSearch<S> {
    @Override protected Deque<SearchNode<S>> createFrontier() { return new ArrayDeque<>(); }
    @Override protected void push(Deque<SearchNode<S>> frontier, SearchNode<S> node) { frontier.addLast(node); }
    @Override public String id() { return "bfs"; }
}
