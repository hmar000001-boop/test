package dev.coala.core.planning;

import dev.coala.core.ActionCall;
import dev.coala.core.ActionCatalogFormatter;
import dev.coala.core.ActionKind;
import dev.coala.core.ActionRegistry;
import dev.coala.core.AgentContext;
import dev.coala.core.DecisionProcedureException;
import dev.coala.core.action.ActionOutputParser;
import dev.coala.core.action.ReasoningAction;

import java.util.List;

/** LLM-backed candidate generation, independent from the enclosing decision loop. */
public final class LlmCandidateProposer implements CandidateProposer {
    private final ReasoningAction reasoning;
    private final ActionOutputParser parser;
    private final ActionCatalogFormatter formatter;
    public LlmCandidateProposer(ReasoningAction reasoning, ActionOutputParser parser) {
        this(reasoning, parser, new ActionCatalogFormatter());
    }
    public LlmCandidateProposer(ReasoningAction reasoning, ActionOutputParser parser, ActionCatalogFormatter formatter) {
        this.reasoning = java.util.Objects.requireNonNull(reasoning); this.parser = java.util.Objects.requireNonNull(parser);
        this.formatter = java.util.Objects.requireNonNull(formatter);
    }
    @Override public List<ActionCall> propose(AgentContext context, ActionRegistry actions, int maxCandidates, String feedback) {
        if (maxCandidates < 2 || maxCandidates > 20) throw new IllegalArgumentException("maxCandidates must be 2..20");
        String instruction = "Generate at least 2 and at most " + maxCandidates + " distinct candidate action calls as strict JSON: {\"candidates\":[{\"actionId\":\"...\",\"arguments\":{\"key\":\"string value\"}}]}. "
                + "Use only this action catalogue and satisfy every declared argument contract: " + formatter.format(actions.descriptors())
                + ". Use the goal, observations and retrieved memories. Do not claim an action has already executed. JSON only."
                + (feedback == null || feedback.isBlank() ? "" : " Previous candidate set was rejected for this reason: " + feedback + ". Produce materially different valid alternatives.");
        List<ActionCall> candidates = parser.parseCandidates(reasoning.reason("PROPOSE_CANDIDATES", instruction, context), maxCandidates);
        for (ActionCall candidate : candidates) {
            var action = actions.validate(candidate);
            if (action.kind() != ActionKind.GROUNDING && action.kind() != ActionKind.LEARNING)
                throw new DecisionProcedureException("Only grounding/learning actions may be selected: " + candidate.actionId());
        }
        context.workingMemory().put("reasoning.latest", null);
        return candidates;
    }
}
