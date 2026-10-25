package dev.coala.core.planning;

import dev.coala.core.ActionCall;
import dev.coala.core.ActionRegistry;
import dev.coala.core.AgentContext;

import java.util.List;

/** Proposes valid executable action calls for planners that need multiple alternatives. */
@FunctionalInterface
public interface CandidateProposer {
    List<ActionCall> propose(AgentContext context, ActionRegistry actions, int maxCandidates, String feedback);
}
