package dev.coala.core.planning;

import dev.coala.core.ActionCall;
import dev.coala.core.DecisionProcedureException;
import dev.coala.core.action.CandidateEvaluation;

import java.util.List;
import java.util.Optional;

/** Optional candidate selection operation; direct procedures need not use this abstraction. */
@FunctionalInterface
public interface CandidateSelectionPolicy {
    Optional<Selection> select(List<ActionCall> candidates, CandidateEvaluation evaluation);

    /** Defends the planner boundary against faulty/custom selection implementations. */
    static void validateSelection(List<ActionCall> candidates, CandidateEvaluation evaluation, Selection selection) {
        if (candidates == null || evaluation == null || selection == null)
            throw new DecisionProcedureException("Selection policy returned an incomplete result");
        if (selection.index() >= candidates.size() || selection.index() < 0)
            throw new DecisionProcedureException("Selection policy returned an out-of-range candidate index");
        if (!candidates.get(selection.index()).equals(selection.action()))
            throw new DecisionProcedureException("Selected action does not match the candidate at the selected index");
        if (evaluation.scores().size() != candidates.size()
                || Double.compare(evaluation.scores().get(selection.index()), selection.score()) != 0)
            throw new DecisionProcedureException("Selected score does not match the evaluation for the selected candidate");
        if (evaluation.rejectAll()) throw new DecisionProcedureException("Selection policy selected a candidate after the evaluator rejected the entire candidate set");
    }

    record Selection(int index, ActionCall action, double score) {
        public Selection {
            if (index < 0) throw new IllegalArgumentException("selection index cannot be negative");
            if (action == null) throw new IllegalArgumentException("selected action is required");
            if (!Double.isFinite(score)) throw new IllegalArgumentException("selected score must be finite");
        }
    }
}
