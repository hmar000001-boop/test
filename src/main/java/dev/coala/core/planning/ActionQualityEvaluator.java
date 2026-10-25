package dev.coala.core.planning;

import dev.coala.core.ActionCall;
import dev.coala.core.ActionRegistry;
import dev.coala.core.AgentContext;
import dev.coala.core.action.ActionQualityEvaluation;

@FunctionalInterface
public interface ActionQualityEvaluator {
    ActionQualityEvaluation evaluate(ActionCall candidate, AgentContext context, ActionRegistry actions);
}
