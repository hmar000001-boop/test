package dev.coala.core.action;

import java.util.List;

/** Parsed evaluation result for a candidate set. Scores are finite and positionally align with candidates. */
public record CandidateEvaluation(boolean rejectAll, List<Double> scores) {
    public CandidateEvaluation {
        if (scores == null || scores.size() < 2)
            throw new IllegalArgumentException("At least two candidate scores are required");
        scores = List.copyOf(scores);
        if (scores.stream().anyMatch(score -> score == null || !Double.isFinite(score)))
            throw new IllegalArgumentException("Candidate scores must be finite numbers");
    }
}
