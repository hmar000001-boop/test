package dev.coala.core.procedure;

import dev.coala.core.ActionCall;
import dev.coala.core.ActionExecutionException;
import dev.coala.core.ActionRegistry;
import dev.coala.core.ActionResult;
import dev.coala.core.AgentContext;
import dev.coala.core.AgentRunResult;
import dev.coala.core.CoalaAction;
import dev.coala.core.DecisionEvent;
import dev.coala.core.DecisionProcedureException;
import dev.coala.core.DecisionStage;
import dev.coala.core.PlanDecision;
import dev.coala.core.InvalidModelOutputException;
import dev.coala.core.TerminationReason;
import dev.coala.llm.LanguageModelException;

import java.util.Map;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared implementation of the observation -> planning -> execution loop.
 * This base class is a software reuse choice, not a named CoALA component.
 */
public abstract class AbstractLoopingDecisionProcedure implements CoalaDecisionProcedure {
    public static final int MAX_GOAL_CHARACTERS = 4_000;

    @Override
    public final AgentRunResult run(String goal, AgentContext context, ActionRegistry actions, int maxCycles) {
        if (goal == null || goal.isBlank()) throw new IllegalArgumentException("goal must not be blank");
        if (goal.length() > MAX_GOAL_CHARACTERS) throw new IllegalArgumentException("goal must be no more than " + MAX_GOAL_CHARACTERS + " characters");
        goal = goal.trim();
        if (maxCycles < 1 || maxCycles > 100) throw new IllegalArgumentException("maxCycles must be between 1 and 100");

        context.workingMemory().put("goal", goal.trim());
        context.workingMemory().put("latest_observation", "User request received: " + goal.trim());
        List<DecisionEvent> events = new ArrayList<>();
        String finalObservation = "";

        for (int cycle = 1; cycle <= maxCycles; cycle++) {
            context.setCycle(cycle);
            String observation = context.workingMemory().getString("latest_observation", "");
            events.add(new DecisionEvent(cycle, DecisionStage.OBSERVATION,
                    truncate(observation, 240)));

            // Do not let a previous cycle's plan be mistaken for the current selection.
            context.workingMemory().put("plan.candidates", null);
            context.workingMemory().put("plan.evaluations", null);
            context.workingMemory().put("plan.selectedAction", null);
            context.workingMemory().put("plan.selectionRejected", null);
            context.workingMemory().put("plan.last_rejection", null);
            context.workingMemory().put("plan.previousRejectedCandidates", null);
            context.workingMemory().put("selected_action", null);
            context.workingMemory().put("selected_action_id", null);
            context.workingMemory().put("selected_action_arguments", null);

            PlanDecision plan;
            try {
                plan = planNextAction(context, actions);
            } catch (DecisionProcedureException | LanguageModelException ex) {
                throw ex;
            } catch (RuntimeException ex) {
                throw new DecisionProcedureException(id() + " failed during planning at cycle " + cycle, ex);
            }
            events.addAll(plan.events());

            ActionCall selected = plan.selectedAction();
            // Validate before committing the plan state or allowing the action adapter to run.
            CoalaAction action;
            try {
                action = actions.validate(selected);
            } catch (DecisionProcedureException ex) {
                throw new InvalidModelOutputException("The selected action did not satisfy the registered action contract.", ex);
            }
            context.workingMemory().put("plan.candidates", plan.candidates().stream()
                    .map(AbstractLoopingDecisionProcedure::toActionState).toList());
            context.workingMemory().put("plan.evaluations", plan.evaluationScores().isEmpty()
                    ? List.of()
                    : java.util.stream.IntStream.range(0, plan.evaluationScores().size())
                            .mapToObj(index -> Map.<String, Object>of(
                                    "candidateIndex", index, "score", plan.evaluationScores().get(index)))
                            .toList());
            context.workingMemory().put("plan.selectedAction", toActionState(plan.selectedAction()));
            // Figure 3A describes parsed action names and arguments being written back
            // to working memory and then used to execute the selected action.
            context.workingMemory().put("selected_action", Map.of(
                    "actionId", selected.actionId(),
                    "arguments", selected.arguments()));
            context.workingMemory().put("selected_action_id", selected.actionId());
            context.workingMemory().put("selected_action_arguments", selected.arguments());
            try {
                ActionResult result = action.execute(selected, context);
                finalObservation = result.observation();
                context.workingMemory().put("latest_action", selected.actionId());
                context.workingMemory().put("latest_observation", result.observation());
                events.add(new DecisionEvent(cycle, action.kind() == dev.coala.core.ActionKind.LEARNING
                        ? DecisionStage.LEARNING : DecisionStage.EXECUTION,
                        selected.actionId() + " -> " + truncate(result.observation(), 240)));
                if (result.awaitingInput()) {
                    events.add(new DecisionEvent(cycle, DecisionStage.TERMINATION, "Action is awaiting a user or external input; resume the interaction in a later request."));
                    return new AgentRunResult(id(), goal, finalObservation, cycle,
                            TerminationReason.AWAITING_INPUT, events);
                }
                if (result.terminal()) {
                    events.add(new DecisionEvent(cycle, DecisionStage.TERMINATION, "Selected action marked the run complete."));
                    return new AgentRunResult(id(), goal, finalObservation, cycle,
                            TerminationReason.TERMINAL_ACTION, events);
                }
            } catch (LanguageModelException ex) {
                throw ex;
            } catch (RuntimeException ex) {
                throw new ActionExecutionException("Action '" + selected.actionId() + "' failed at cycle " + cycle, ex);
            }
        }

        events.add(new DecisionEvent(maxCycles, DecisionStage.TERMINATION,
                "Safety bound reached: maxCycles=" + maxCycles + "."));
        return new AgentRunResult(id(), goal, finalObservation, maxCycles,
                TerminationReason.MAX_CYCLES_REACHED, events);
    }

    protected abstract PlanDecision planNextAction(AgentContext context, ActionRegistry actions);

    private static Map<String, Object> toActionState(ActionCall action) {
        return Map.of("actionId", action.actionId(), "arguments", action.arguments());
    }

    protected static String truncate(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max - 3) + "...";
    }
}
