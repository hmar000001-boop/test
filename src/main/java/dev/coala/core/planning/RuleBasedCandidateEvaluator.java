package dev.coala.core.planning;

import dev.coala.core.ActionCall;
import dev.coala.core.ActionRegistry;
import dev.coala.core.ActionRisk;
import dev.coala.core.AgentContext;
import dev.coala.core.action.CandidateEvaluation;

import java.util.List;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

/** Deterministic baseline evaluator; application-specific constraints should be supplied as a scoring function. */
public final class RuleBasedCandidateEvaluator implements CandidateEvaluator {
    private final ToDoubleFunction<dev.coala.core.CoalaAction> actionScore;
    private final double rejectBelow;
    public RuleBasedCandidateEvaluator() { this(action -> switch (action.risk()) {
        case LOW -> 0.9; case MEDIUM -> 0.7; case HIGH -> 0.4; case CRITICAL -> 0.1;
    }, 0.0); }
    public RuleBasedCandidateEvaluator(ToDoubleFunction<dev.coala.core.CoalaAction> actionScore, double rejectBelow) {
        this.actionScore = java.util.Objects.requireNonNull(actionScore);
        if (!Double.isFinite(rejectBelow)) throw new IllegalArgumentException("rejectBelow must be finite");
        this.rejectBelow = rejectBelow;
    }
    @Override public CandidateEvaluation evaluate(List<ActionCall> candidates, AgentContext context, ActionRegistry actions) {
        if (candidates == null || candidates.size() < 2) throw new IllegalArgumentException("At least two candidates are required");
        List<Double> scores = candidates.stream().map(call -> actionScore.applyAsDouble(actions.require(call.actionId()))).toList();
        if (scores.stream().anyMatch(score -> !Double.isFinite(score))) throw new IllegalStateException("Rule-based evaluator produced a non-finite score");
        return new CandidateEvaluation(scores.stream().noneMatch(score -> score >= rejectBelow), scores);
    }
}
