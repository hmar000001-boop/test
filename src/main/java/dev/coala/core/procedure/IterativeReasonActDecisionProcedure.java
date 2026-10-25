package dev.coala.core.procedure;

import dev.coala.core.ActionCatalogFormatter;
import dev.coala.core.ActionRegistry;
import dev.coala.core.AgentContext;
import dev.coala.core.DecisionEvent;
import dev.coala.core.DecisionStage;
import dev.coala.core.PlanDecision;
import dev.coala.core.action.ActionOutputParser;
import dev.coala.core.action.ReasoningAction;

import java.util.List;

/** Repeated reason -> one action -> observation cycles, illustrated with a coding-agent demo. */
public final class IterativeReasonActDecisionProcedure extends AbstractLoopingDecisionProcedure {
    private final ReasoningAction reasoning;
    private final ActionOutputParser parser;
    private final ActionCatalogFormatter actionCatalogFormatter = new ActionCatalogFormatter();

    public IterativeReasonActDecisionProcedure(ReasoningAction reasoning, ActionOutputParser parser) {
        this.reasoning = reasoning;
        this.parser = parser;
    }

    @Override
    public String id() { return "iterative-reason-act"; }

    @Override
    protected PlanDecision planNextAction(AgentContext context, ActionRegistry actions) {
        String instruction = "OPERATION ITERATIVE_NEXT_ACTION. Choose exactly one next action as JSON: "
                + "{\"actionId\":\"...\",\"arguments\":{\"key\":\"string value\"}}. "
                + "Available actions and their argument contracts: " + actionCatalogFormatter.format(actions.descriptors()) + ". "
                + "Use the goal and latest observation in working memory. Propose only the next action for this cycle; "
                + "do not claim that a tool action has already happened. An action can finish the run by returning a terminal result. "
                + "Return JSON only.";
        String output = reasoning.reason("ITERATIVE_NEXT_ACTION", instruction, context);
        var selected = parser.parseAction(output);
        context.workingMemory().put("reasoning.latest", null);
        return new PlanDecision(selected, List.of(
                new DecisionEvent(context.cycle(), DecisionStage.REASONING, "Reasoned from the current observation and working-memory state."),
                new DecisionEvent(context.cycle(), DecisionStage.PROPOSAL, "Proposed one next action for this cycle."),
                new DecisionEvent(context.cycle(), DecisionStage.SELECTION, "Selected the single proposed action for execution.")));
    }

}
