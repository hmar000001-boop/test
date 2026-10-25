package dev.coala.core.planning;

import dev.coala.core.ActionCall;
import dev.coala.core.ActionCatalogFormatter;
import dev.coala.core.ActionRegistry;
import dev.coala.core.AgentContext;
import dev.coala.core.action.ActionOutputParser;
import dev.coala.core.action.CandidateEvaluation;
import dev.coala.core.action.ReasoningAction;

import java.util.List;
import java.util.stream.IntStream;

/** LLM-based candidate assessor. Scores are uncalibrated ordinal judgments. */
public final class LlmCandidateEvaluator implements CandidateEvaluator {
    private final ReasoningAction reasoning;
    private final ActionOutputParser parser;
    private final ActionCatalogFormatter formatter;
    public LlmCandidateEvaluator(ReasoningAction reasoning, ActionOutputParser parser) {
        this(reasoning, parser, new ActionCatalogFormatter());
    }
    public LlmCandidateEvaluator(ReasoningAction reasoning, ActionOutputParser parser, ActionCatalogFormatter formatter) {
        this.reasoning = java.util.Objects.requireNonNull(reasoning); this.parser = java.util.Objects.requireNonNull(parser);
        this.formatter = java.util.Objects.requireNonNull(formatter);
    }
    @Override public CandidateEvaluation evaluate(List<ActionCall> candidates, AgentContext context, ActionRegistry actions) {
        if (candidates == null || candidates.size() < 2) throw new IllegalArgumentException("At least two candidates are required");
        String rendered = IntStream.range(0, candidates.size()).mapToObj(i -> {
            ActionCall c = candidates.get(i); var d = actions.require(c.actionId()).descriptor();
            return i + ": id=" + c.actionId() + ", kind=" + d.kind() + ", risk=" + d.risk() + ", description=" + d.description() + ", arguments=" + c.arguments();
        }).collect(java.util.stream.Collectors.joining("\n"));
        String instruction = "Evaluate the ordered candidates as NEXT actions for the current goal, using the available observations and memory. "
                + "Return strict JSON {\"rejectAll\":false,\"scores\":[0.0,0.0]}; one finite number per candidate in the same order. "
                + "Set rejectAll=true if none is appropriate, feasible or safe enough. Higher score is better but is not a probability. "
                + "Action catalogue: " + formatter.format(actions.descriptors()) + "\nCandidates:\n" + rendered;
        CandidateEvaluation result = parser.parseEvaluation(reasoning.reason("EVALUATE_CANDIDATES", instruction, context), candidates.size());
        context.workingMemory().put("reasoning.latest", null);
        return result;
    }
}
