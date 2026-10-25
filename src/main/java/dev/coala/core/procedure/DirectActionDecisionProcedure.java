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

/** Direct action generation: one reasoning operation proposes the next action. */
public final class DirectActionDecisionProcedure extends AbstractLoopingDecisionProcedure {
    private final ReasoningAction reasoning;
    private final ActionOutputParser parser;
    private final ActionCatalogFormatter actionCatalogFormatter = new ActionCatalogFormatter();

    public DirectActionDecisionProcedure(ReasoningAction reasoning, ActionOutputParser parser) {
        this.reasoning = reasoning;
        this.parser = parser;
    }

    @Override
    public String id() { return "direct-action"; }

    @Override
    protected PlanDecision planNextAction(AgentContext context, ActionRegistry actions) {
        String instruction = "OPERATION DIRECT_ACTION. Generate exactly one next action call as JSON: "
                + "{\"actionId\":\"...\",\"arguments\":{\"key\":\"string value\"}}. "
                + "Choose only from the available actions and respect their argument contracts: "
                + actionCatalogFormatter.format(actions.descriptors()) + ". Use the goal and current working-memory state. "
                + "Do not claim that an action has already happened. No markdown or explanation outside JSON.";
        String output = reasoning.reason("DIRECT_ACTION", instruction, context);
        var selected = parser.parseAction(output);
        context.workingMemory().put("reasoning.latest", null);
        return new PlanDecision(selected, List.of(
                new DecisionEvent(context.cycle(), DecisionStage.REASONING, "Performed one direct reasoning operation over working memory."),
                new DecisionEvent(context.cycle(), DecisionStage.PROPOSAL, "Generated one action candidate directly."),
                new DecisionEvent(context.cycle(), DecisionStage.SELECTION, "Selected the directly generated action; no separate evaluation stage is implemented by this procedure.")));
    }

}
