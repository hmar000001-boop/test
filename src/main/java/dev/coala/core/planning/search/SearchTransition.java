package dev.coala.core.planning.search;

import dev.coala.core.ActionCall;

/** One simulated transition. nextState must be an isolated, immutable or defensively copied simulation state. */
public record SearchTransition<S>(ActionCall action, S nextState, double reward, boolean terminal) {
    public SearchTransition {
        if (action == null || nextState == null) throw new IllegalArgumentException("action and nextState are required");
        if (!Double.isFinite(reward)) throw new IllegalArgumentException("reward must be finite");
    }
}
