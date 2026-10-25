package dev.coala.core;

import java.util.List;
import java.util.Objects;

/** Result of planning one next action, including structured candidates/evaluations when available. */
public record PlanDecision(
        ActionCall selectedAction,
        List<ActionCall> candidates,
        List<Double> evaluationScores,
        List<DecisionEvent> events) {

    public PlanDecision {
        Objects.requireNonNull(selectedAction, "selectedAction");
        candidates = candidates == null || candidates.isEmpty() ? List.of(selectedAction) : List.copyOf(candidates);
        evaluationScores = evaluationScores == null ? List.of() : List.copyOf(evaluationScores);
        events = events == null ? List.of() : List.copyOf(events);
        if (!candidates.contains(selectedAction)) {
            throw new IllegalArgumentException("selectedAction must be present in the planned candidate list");
        }
        if (!evaluationScores.isEmpty() && evaluationScores.size() != candidates.size()) {
            throw new IllegalArgumentException("evaluationScores must be empty or contain one score per candidate");
        }
        if (evaluationScores.stream().anyMatch(score -> score == null || !Double.isFinite(score))) {
            throw new IllegalArgumentException("evaluation scores must be finite numbers");
        }
    }

    /** Direct/iterative decision procedures may propose one candidate without explicit evaluation. */
    public PlanDecision(ActionCall selectedAction, List<DecisionEvent> events) {
        this(selectedAction, List.of(selectedAction), List.of(), events);
    }
}
