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
import dev.coala.core.action.ReasoningAction;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/** Creates a short structured plan, stores it in working memory, and executes one registered step per cycle. */
public final class PlanFirstDecisionProcedure extends AbstractLoopingDecisionProcedure {
    private final ReasoningAction reasoning;
    private final ActionOutputParser parser;
    private final ActionCatalogFormatter formatter = new ActionCatalogFormatter();
    private final int maxSteps;
    private final Predicate<String> replanCondition;

    public PlanFirstDecisionProcedure(ReasoningAction reasoning, ActionOutputParser parser, int maxSteps) {
        this(reasoning, parser, maxSteps, observation -> {
            String value = observation == null ? "" : observation.toLowerCase(java.util.Locale.ROOT);
            return value.contains("failure:") || value.contains("error:") || value.contains("needs_replan");
        });
    }
    public PlanFirstDecisionProcedure(ReasoningAction reasoning, ActionOutputParser parser, int maxSteps, Predicate<String> replanCondition) {
        this.reasoning = java.util.Objects.requireNonNull(reasoning); this.parser = java.util.Objects.requireNonNull(parser);
        if (maxSteps < 1 || maxSteps > 20) throw new IllegalArgumentException("maxSteps must be 1..20");
        this.maxSteps = maxSteps; this.replanCondition = java.util.Objects.requireNonNull(replanCondition);
    }
    @Override public String id() { return "plan-first-revisable"; }

    @Override protected PlanDecision planNextAction(AgentContext context, ActionRegistry actions) {
        String observation = context.workingMemory().getString("latest_observation", "");
        List<ActionCall> pending = readPending(context.workingMemory().get("plan.first_steps").orElse(null));
        boolean revise = replanCondition.test(observation);
        if (pending.isEmpty() || revise) {
            if (revise && !pending.isEmpty()) context.workingMemory().put("plan.replaced_steps", pending.stream().map(PlanFirstDecisionProcedure::state).toList());
            String prompt = "Create a plan of 1 to " + maxSteps + " executable NEXT ACTIONS for the goal, using current memory/observations. "
                    + "Return strict JSON {\"steps\":[{\"actionId\":\"...\",\"arguments\":{\"key\":\"string value\"}}]}. "
                    + "Only use the following registered actions and valid argument contracts: " + formatter.format(actions.descriptors()) + ". "
                    + "A plan is a proposal, not execution. Prefer short plans. JSON only."
                    + (revise ? " Previous observation suggests the old plan needs revision: " + observation : "");
            List<ActionCall> proposed = parser.parseActionPlan(reasoning.reason("PLAN_FIRST", prompt, context), maxSteps);
            for (ActionCall step : proposed) {
                var action = actions.validate(step);
                if (action.kind() != ActionKind.GROUNDING && action.kind() != ActionKind.LEARNING)
                    throw new DecisionProcedureException("Plan step must target a registered grounding or learning action: " + step.actionId());
            }
            pending = new ArrayList<>(proposed);
            context.workingMemory().put("plan.first_steps", pending.stream().map(PlanFirstDecisionProcedure::state).toList());
            context.workingMemory().put("reasoning.latest", null);
        }
        ActionCall selected = pending.remove(0);
        context.workingMemory().put("plan.first_steps", pending.stream().map(PlanFirstDecisionProcedure::state).toList());
        context.workingMemory().put("plan.selectedAction", state(selected));
        List<DecisionEvent> events = List.of(
                new DecisionEvent(context.cycle(), DecisionStage.PROPOSAL, "Prepared/reused a plan with " + (pending.size() + 1) + " step(s) before selecting the next one."),
                new DecisionEvent(context.cycle(), DecisionStage.SELECTION, "Selected the first remaining plan step: " + selected.actionId() + "."));
        return new PlanDecision(selected, List.of(selected), List.of(), events);
    }
    private static List<ActionCall> readPending(Object value) {
        if (!(value instanceof List<?> list)) return new ArrayList<>();
        List<ActionCall> result = new ArrayList<>();
        try {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> map) || !(map.get("actionId") instanceof String id) || !(map.get("arguments") instanceof Map<?, ?> args)) return new ArrayList<>();
                java.util.LinkedHashMap<String,String> normalized = new java.util.LinkedHashMap<>();
                for (var entry : args.entrySet()) { if (!(entry.getKey() instanceof String k) || !(entry.getValue() instanceof String v)) return new ArrayList<>(); normalized.put(k,v); }
                result.add(new ActionCall(id, normalized));
            }
            return result;
        } catch (RuntimeException ex) { return new ArrayList<>(); }
    }
    private static Map<String,Object> state(ActionCall c) { return Map.of("actionId", c.actionId(), "arguments", c.arguments()); }
}
