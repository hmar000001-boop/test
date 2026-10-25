package dev.coala.core.planning;

import dev.coala.core.ActionCall;
import dev.coala.core.action.CandidateEvaluation;

import java.util.List;
import java.util.Optional;

/** Selects the highest finite score; stable ties keep the first candidate. */
public final class HighestScoreCandidateSelectionPolicy implements CandidateSelectionPolicy {
    @Override public Optional<Selection> select(List<ActionCall> candidates, CandidateEvaluation evaluation) {
        if (candidates == null || candidates.isEmpty() || evaluation == null || evaluation.rejectAll()) return Optional.empty();
        if (evaluation.scores().size() != candidates.size()) throw new IllegalArgumentException("One score per candidate is required");
        int best = 0;
        for (int i = 1; i < evaluation.scores().size(); i++) if (evaluation.scores().get(i) > evaluation.scores().get(best)) best = i;
        return Optional.of(new Selection(best, candidates.get(best), evaluation.scores().get(best)));
    }
}
