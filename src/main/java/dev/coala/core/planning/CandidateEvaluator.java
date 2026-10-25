package dev.coala.core.planning;

import dev.coala.core.ActionCall;
import dev.coala.core.ActionRegistry;
import dev.coala.core.AgentContext;
import dev.coala.core.action.CandidateEvaluation;

import java.util.List;

/** Evaluates candidate suitability as the next action. Scores are ranking signals, not probabilities. */
@FunctionalInterface
public interface CandidateEvaluator {
    CandidateEvaluation evaluate(List<ActionCall> candidates, AgentContext context, ActionRegistry actions);
}
