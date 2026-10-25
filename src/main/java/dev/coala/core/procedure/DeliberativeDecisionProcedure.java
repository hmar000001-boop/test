package dev.coala.core.procedure;

import dev.coala.core.ActionCall;
import dev.coala.core.ActionRegistry;
import dev.coala.core.AgentContext;
import dev.coala.core.DecisionEvent;
import dev.coala.core.DecisionProcedureException;
import dev.coala.core.DecisionStage;
import dev.coala.core.PlanDecision;
import dev.coala.core.PlanningExhaustedException;
import dev.coala.core.action.ActionOutputParser;
import dev.coala.core.action.CandidateEvaluation;
import dev.coala.core.action.RetrievalAction;
import dev.coala.core.action.ReasoningAction;
import dev.coala.core.planning.CandidateEvaluator;
import dev.coala.core.planning.CandidateProposer;
import dev.coala.core.planning.CandidateSelectionPolicy;
import dev.coala.core.planning.HighestScoreCandidateSelectionPolicy;
import dev.coala.core.planning.LlmCandidateEvaluator;
import dev.coala.core.planning.LlmCandidateProposer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Deliberative procedure template: optional retrieval, proposal/evaluation/selection and bounded re-proposal. */
public final class DeliberativeDecisionProcedure extends AbstractLoopingDecisionProcedure {
    private final RetrievalAction retrieval;
    private final CandidateProposer proposer;
    private final CandidateEvaluator evaluator;
    private final CandidateSelectionPolicy selectionPolicy;
    private final int maxCandidates;
    private final int maxProposalAttempts;

    public DeliberativeDecisionProcedure(ReasoningAction reasoning, RetrievalAction retrieval,
                                         ActionOutputParser parser, int maxCandidates) {
        this(reasoning, retrieval, parser, maxCandidates, 3);
    }
    public DeliberativeDecisionProcedure(ReasoningAction reasoning, RetrievalAction retrieval,
                                         ActionOutputParser parser, int maxCandidates, int maxProposalAttempts) {
        this(retrieval, new LlmCandidateProposer(reasoning, parser), new LlmCandidateEvaluator(reasoning, parser),
                new HighestScoreCandidateSelectionPolicy(), maxCandidates, maxProposalAttempts);
    }
    public DeliberativeDecisionProcedure(RetrievalAction retrieval, CandidateProposer proposer,
                                         CandidateEvaluator evaluator, CandidateSelectionPolicy selectionPolicy,
                                         int maxCandidates, int maxProposalAttempts) {
        this.retrieval = java.util.Objects.requireNonNull(retrieval);
        this.proposer = java.util.Objects.requireNonNull(proposer);
        this.evaluator = java.util.Objects.requireNonNull(evaluator);
        this.selectionPolicy = java.util.Objects.requireNonNull(selectionPolicy);
        if (maxCandidates < 2 || maxCandidates > 20) throw new IllegalArgumentException("maxCandidates must be between 2 and 20");
        if (maxProposalAttempts < 1 || maxProposalAttempts > 10) throw new IllegalArgumentException("maxProposalAttempts must be between 1 and 10");
        this.maxCandidates = maxCandidates; this.maxProposalAttempts = maxProposalAttempts;
    }
    @Override public String id() { return "deliberative-proposal-evaluation-selection"; }

    @Override protected PlanDecision planNextAction(AgentContext context, ActionRegistry actions) {
        List<DecisionEvent> events = new ArrayList<>();
        var memories = retrieval.retrieve(context.workingMemory().getString("goal", ""), context, 3);
        events.add(new DecisionEvent(context.cycle(), DecisionStage.RETRIEVAL,
                "Retrieval phase completed; " + memories.size() + " memory record(s) were included in the bounded working-memory context."));
        String rejectionFeedback = "";
        for (int attempt = 1; attempt <= maxProposalAttempts; attempt++) {
            context.workingMemory().put("plan.candidates", null);
            context.workingMemory().put("plan.evaluations", null);
            context.workingMemory().put("plan.selectedAction", null);
            context.workingMemory().put("plan.selectionRejected", null);
            List<ActionCall> candidates;
            try {
                candidates = proposer.propose(context, actions, maxCandidates, rejectionFeedback);
                events.add(new DecisionEvent(context.cycle(), DecisionStage.REASONING, "Used the configured candidate proposer (attempt " + attempt + ")."));
            } catch (DecisionProcedureException ex) {
                context.workingMemory().put("reasoning.latest", null);
                rejectionFeedback = safeReason(ex.getMessage());
                context.workingMemory().put("plan.last_rejection", rejectionFeedback);
                events.add(new DecisionEvent(context.cycle(), DecisionStage.PROPOSAL, "Rejected invalid candidate output on attempt " + attempt + "."));
                events.add(new DecisionEvent(context.cycle(), DecisionStage.SELECTION, "No candidate selected; returning to proposal."));
                continue;
            }
            context.workingMemory().put("plan.candidates", candidates.stream().map(DeliberativeDecisionProcedure::toActionState).toList());
            context.workingMemory().put("plan.evaluations", List.of());
            events.add(new DecisionEvent(context.cycle(), DecisionStage.PROPOSAL, "Generated " + candidates.size() + " distinct, validated candidate action(s)."));
            CandidateEvaluation evaluation;
            try {
                evaluation = evaluator.evaluate(candidates, context, actions);
                if (evaluation == null) throw new DecisionProcedureException("Evaluator returned no result");
                if (evaluation.scores().size() != candidates.size()) throw new DecisionProcedureException("Evaluator must return one score per candidate");
            } catch (DecisionProcedureException ex) {
                context.workingMemory().put("reasoning.latest", null);
                rejectionFeedback = safeReason(ex.getMessage());
                context.workingMemory().put("plan.last_rejection", rejectionFeedback);
                context.workingMemory().put("plan.previousRejectedCandidates", candidates.stream().map(DeliberativeDecisionProcedure::toActionState).toList());
                events.add(new DecisionEvent(context.cycle(), DecisionStage.EVALUATION, "Candidate evaluation failed validation."));
                events.add(new DecisionEvent(context.cycle(), DecisionStage.SELECTION, "No candidate selected; returning to proposal."));
                continue;
            }
            context.workingMemory().put("plan.evaluations", java.util.stream.IntStream.range(0, evaluation.scores().size())
                    .mapToObj(i -> Map.<String,Object>of("candidateIndex", i, "score", evaluation.scores().get(i))).toList());
            context.workingMemory().put("plan.selectionRejected", evaluation.rejectAll());
            events.add(new DecisionEvent(context.cycle(), DecisionStage.EVALUATION,
                    "Evaluation scores=" + evaluation.scores() + "; rejectAll=" + evaluation.rejectAll() + "."));
            var selected = selectionPolicy.select(candidates, evaluation);
            if (selected.isEmpty()) {
                rejectionFeedback = evaluation.rejectAll() ? "the evaluator rejected all candidates" : "selection policy rejected all candidates or found no acceptable score";
                context.workingMemory().put("plan.last_rejection", rejectionFeedback);
                context.workingMemory().put("plan.previousRejectedCandidates", candidates.stream().map(DeliberativeDecisionProcedure::toActionState).toList());
                events.add(new DecisionEvent(context.cycle(), DecisionStage.SELECTION, "No candidate was selected; returning to proposal. Reason: " + rejectionFeedback));
                continue;
            }
            CandidateSelectionPolicy.Selection choice = selected.get();
            try {
                CandidateSelectionPolicy.validateSelection(candidates, evaluation, choice);
                actions.validate(choice.action());
            } catch (DecisionProcedureException ex) {
                rejectionFeedback = safeReason(ex.getMessage());
                context.workingMemory().put("plan.last_rejection", rejectionFeedback);
                context.workingMemory().put("plan.previousRejectedCandidates", candidates.stream().map(DeliberativeDecisionProcedure::toActionState).toList());
                events.add(new DecisionEvent(context.cycle(), DecisionStage.SELECTION, "Selection result failed validation; returning to proposal."));
                continue;
            }
            ActionCall action = choice.action();
            context.workingMemory().put("plan.selectedAction", toActionState(action));
            context.workingMemory().put("plan.selectionRejected", false);
            context.workingMemory().put("plan.last_rejection", null);
            context.workingMemory().put("plan.previousRejectedCandidates", null);
            events.add(new DecisionEvent(context.cycle(), DecisionStage.SELECTION,
                    "Selected candidate " + choice.index() + " (" + action.actionId() + ") with score " + choice.score() + "."));
            return new PlanDecision(action, candidates, evaluation.scores(), events);
        }
        throw new PlanningExhaustedException(maxProposalAttempts, rejectionFeedback, events);
    }
    private static Map<String,Object> toActionState(ActionCall call) { return Map.of("actionId", call.actionId(), "arguments", call.arguments()); }
    private static String safeReason(String message) { if (message == null || message.isBlank()) return "invalid candidate/evaluation output"; return message.length() <= 240 ? message : message.substring(0, 237) + "..."; }
}
