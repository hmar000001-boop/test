package dev.coala.core.planning;

import dev.coala.core.ActionCall;
import dev.coala.core.action.CandidateEvaluation;

import java.util.List;
import java.util.Optional;

/** Selects argmax only when the best score meets an explicit configured threshold. */
public final class ThresholdCandidateSelectionPolicy implements CandidateSelectionPolicy {
    private final double minimumScore;
    public ThresholdCandidateSelectionPolicy(double minimumScore) { if (!Double.isFinite(minimumScore)) throw new IllegalArgumentException("minimumScore must be finite"); this.minimumScore = minimumScore; }
    @Override public Optional<Selection> select(List<ActionCall> candidates, CandidateEvaluation evaluation) {
        Optional<Selection> selection = new HighestScoreCandidateSelectionPolicy().select(candidates, evaluation);
        return selection.filter(best -> best.score() >= minimumScore);
    }
}
