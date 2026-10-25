package dev.coala.core.procedure;

import dev.coala.core.ActionCall;
import dev.coala.core.ActionRegistry;
import dev.coala.core.AgentContext;
import dev.coala.core.DecisionEvent;
import dev.coala.core.DecisionProcedureException;
import dev.coala.core.DecisionStage;
import dev.coala.core.PlanDecision;
import dev.coala.core.PlanningExhaustedException;
import dev.coala.core.action.CandidateEvaluation;
import dev.coala.core.planning.CandidateEvaluator;
import dev.coala.core.planning.CandidateSelectionPolicy;
import dev.coala.core.planning.HighestScoreCandidateSelectionPolicy;

import java.util.List;
import java.util.Map;

/** Selects among a known finite set of action calls using configurable evaluation and selection. */
public final class FixedActionSetDecisionProcedure extends AbstractLoopingDecisionProcedure {
    private final List<ActionCall> candidates;
    private final CandidateEvaluator evaluator;
    private final CandidateSelectionPolicy selector;
    public FixedActionSetDecisionProcedure(List<ActionCall> candidates, CandidateEvaluator evaluator) {
        this(candidates, evaluator, new HighestScoreCandidateSelectionPolicy());
    }
    public FixedActionSetDecisionProcedure(List<ActionCall> candidates, CandidateEvaluator evaluator, CandidateSelectionPolicy selector) {
        if (candidates == null || candidates.size() < 2) throw new IllegalArgumentException("At least two action calls are required");
        this.candidates = List.copyOf(candidates);
        if (new java.util.HashSet<>(this.candidates).size() != this.candidates.size()) throw new IllegalArgumentException("Fixed candidate action calls must be distinct");
        this.evaluator = java.util.Objects.requireNonNull(evaluator); this.selector = java.util.Objects.requireNonNull(selector);
    }
    @Override public String id() { return "fixed-action-set-evaluation"; }
    @Override protected PlanDecision planNextAction(AgentContext context, ActionRegistry actions) {
        for (ActionCall candidate : candidates) actions.validate(candidate);
        CandidateEvaluation evaluation = evaluator.evaluate(candidates, context, actions);
        if (evaluation == null || evaluation.scores().size() != candidates.size()) throw new DecisionProcedureException("Evaluator must return exactly one score per candidate");
        var selection = selector.select(candidates, evaluation);
        List<DecisionEvent> events = List.of(
                new DecisionEvent(context.cycle(), DecisionStage.PROPOSAL, "Loaded " + candidates.size() + " fixed candidate action calls."),
                new DecisionEvent(context.cycle(), DecisionStage.EVALUATION, "Evaluated fixed action set; scores=" + evaluation.scores() + ", rejectAll=" + evaluation.rejectAll() + "."));
        if (selection.isEmpty()) throw new PlanningExhaustedException(1, "fixed action set rejected by evaluator/selection policy", events);
        var choice = selection.get();
        CandidateSelectionPolicy.validateSelection(candidates, evaluation, choice);
        actions.validate(choice.action());
        context.workingMemory().put("plan.candidates", candidates.stream().map(c -> Map.of("actionId", c.actionId(), "arguments", c.arguments())).toList());
        context.workingMemory().put("plan.evaluations", java.util.stream.IntStream.range(0, evaluation.scores().size()).mapToObj(i -> Map.<String,Object>of("candidateIndex", i, "score", evaluation.scores().get(i))).toList());
        events = new java.util.ArrayList<>(events); events.add(new DecisionEvent(context.cycle(), DecisionStage.SELECTION, "Selected fixed candidate " + choice.index() + " (" + choice.action().actionId() + ")."));
        return new PlanDecision(choice.action(), candidates, evaluation.scores(), events);
    }
}
