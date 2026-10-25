package dev.coala.core.planning;

import dev.coala.core.ActionCall;
import dev.coala.core.ActionRegistry;
import dev.coala.core.AgentContext;
import dev.coala.core.action.ActionOutputParser;
import dev.coala.core.action.ActionQualityEvaluation;
import dev.coala.core.action.ReasoningAction;

/** LLM quality evaluator for a single candidate; scores are heuristic signals, not calibrated probabilities. */
public final class LlmActionQualityEvaluator implements ActionQualityEvaluator {
    private final ReasoningAction reasoning; private final ActionOutputParser parser;
    public LlmActionQualityEvaluator(ReasoningAction reasoning, ActionOutputParser parser) { this.reasoning = java.util.Objects.requireNonNull(reasoning); this.parser = java.util.Objects.requireNonNull(parser); }
    @Override public ActionQualityEvaluation evaluate(ActionCall candidate, AgentContext context, ActionRegistry actions) {
        var descriptor = actions.validate(candidate).descriptor();
        String instruction = "Assess this proposed NEXT action for the current goal and working-memory evidence. Return strict JSON {\"accepted\":false,\"score\":0.0,\"feedback\":\"specific improvement\"}. "
                + "Score must be 0..1, as a non-calibrated heuristic. Accept only if arguments and action choice are suitable; independent action validation still applies. "
                + "Candidate=" + candidate.actionId() + " kind=" + descriptor.kind() + " risk=" + descriptor.risk() + " args=" + candidate.arguments();
        ActionQualityEvaluation evaluation = parser.parseQualityEvaluation(reasoning.reason("EVALUATE_ACTION_QUALITY", instruction, context));
        context.workingMemory().put("reasoning.latest", null);
        return evaluation;
    }
}
