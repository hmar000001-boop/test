package dev.coala.core.procedure;

import dev.coala.core.ActionCall;
import dev.coala.core.ActionCatalogFormatter;
import dev.coala.core.ActionKind;
import dev.coala.core.ActionRegistry;
import dev.coala.core.AgentContext;
import dev.coala.core.DecisionEvent;
import dev.coala.core.DecisionProcedureException;
import dev.coala.core.DecisionStage;
import dev.coala.core.PlanDecision;
import dev.coala.core.PlanningExhaustedException;
import dev.coala.core.action.ActionOutputParser;
import dev.coala.core.action.ActionQualityEvaluation;
import dev.coala.core.action.ReasoningAction;
import dev.coala.core.planning.ActionQualityEvaluator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Refines an action proposal internally; no selected action executes until it passes the configured acceptance check. */
public final class IterativeRefinementDecisionProcedure extends AbstractLoopingDecisionProcedure {
    private final ReasoningAction reasoning;
    private final ActionOutputParser parser;
    private final ActionQualityEvaluator evaluator;
    private final int maxAttempts;
    private final double minimumScore;
    private final ActionCatalogFormatter formatter = new ActionCatalogFormatter();

    public IterativeRefinementDecisionProcedure(ReasoningAction reasoning, ActionOutputParser parser,
                                                ActionQualityEvaluator evaluator, int maxAttempts, double minimumScore) {
        this.reasoning = java.util.Objects.requireNonNull(reasoning); this.parser = java.util.Objects.requireNonNull(parser);
        this.evaluator = java.util.Objects.requireNonNull(evaluator);
        if (maxAttempts < 1 || maxAttempts > 10) throw new IllegalArgumentException("maxAttempts must be 1..10");
        if (!Double.isFinite(minimumScore) || minimumScore < 0 || minimumScore > 1) throw new IllegalArgumentException("minimumScore must be in [0,1]");
        this.maxAttempts = maxAttempts; this.minimumScore = minimumScore;
    }
    @Override public String id() { return "iterative-refinement-before-execution"; }
    @Override protected PlanDecision planNextAction(AgentContext context, ActionRegistry actions) {
        List<DecisionEvent> events = new ArrayList<>(); List<ActionCall> versions = new ArrayList<>();
        String feedback = ""; ActionCall previous = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            String operation = attempt == 1 ? "PROPOSE_ACTION" : "REFINE_ACTION";
            String instruction = (attempt == 1 ? "Propose one action" : "Refine the previous action proposal")
                    + " for the current goal and evidence. Return strict JSON {\"actionId\":\"...\",\"arguments\":{\"key\":\"string value\"}}. "
                    + "Allowed registered actions: " + formatter.format(actions.descriptors()) + ". Never execute the action in this reasoning step. JSON only."
                    + (previous == null ? "" : " Previous candidate=" + previous.actionId() + " args=" + previous.arguments() + "; critique=" + feedback);
            ActionCall candidate = parser.parseAction(reasoning.reason(operation, instruction, context));
            try {
                var action = actions.validate(candidate);
                if (action.kind() != ActionKind.GROUNDING && action.kind() != ActionKind.LEARNING) throw new DecisionProcedureException("Candidate must target grounding or learning action");
            } catch (DecisionProcedureException ex) {
                previous = candidate; feedback = ex.getMessage(); versions.add(candidate);
                context.workingMemory().put("plan.refinementFeedback", feedback);
                events.add(new DecisionEvent(context.cycle(), DecisionStage.PROPOSAL, "Rejected invalid action proposal on refinement attempt " + attempt + "."));
                continue;
            }
            versions.add(candidate); context.workingMemory().put("plan.refinementCandidate", state(candidate));
            ActionQualityEvaluation quality = evaluator.evaluate(candidate, context, actions);
            context.workingMemory().put("plan.refinementEvaluation", Map.of("accepted", quality.accepted(), "score", quality.score(), "feedback", quality.feedback()));
            events.add(new DecisionEvent(context.cycle(), DecisionStage.EVALUATION, "Refinement attempt " + attempt + " scored " + quality.score() + "; accepted=" + quality.accepted() + "."));
            if (quality.accepted() && quality.score() >= minimumScore) {
                context.workingMemory().put("reasoning.latest", null);
                events.add(new DecisionEvent(context.cycle(), DecisionStage.SELECTION, "Selected the validated proposal after internal refinement; no intermediate proposal was executed."));
                return new PlanDecision(candidate, versions, List.of(), events);
            }
            previous = candidate; feedback = quality.feedback().isBlank() ? "quality score below minimum " + minimumScore : quality.feedback();
        }
        throw new PlanningExhaustedException(maxAttempts, feedback, events);
    }
    private static Map<String,Object> state(ActionCall c) { return Map.of("actionId", c.actionId(), "arguments", c.arguments()); }
}
