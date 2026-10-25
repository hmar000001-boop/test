package dev.coala.core.action;

import dev.coala.core.AgentContext;

import java.util.Objects;

/** Optional reasoning operation for long observations; preserves the original observation separately. */
public final class ObservationSummarizer {
    private final ReasoningAction reasoning;
    public ObservationSummarizer(ReasoningAction reasoning) { this.reasoning = Objects.requireNonNull(reasoning); }
    public String summarize(String observation, AgentContext context, int maxSummaryCharacters) {
        if (observation == null) observation = "";
        if (maxSummaryCharacters < 50 || maxSummaryCharacters > 8_000) throw new IllegalArgumentException("maxSummaryCharacters must be 50..8000");
        context.workingMemory().put("observation.original", observation);
        String prompt = "Summarize the observation for the current goal in factual, actionable terms. Preserve IDs, errors, constraints, values and details required by the next action. Do not invent facts. Maximum "
                + maxSummaryCharacters + " characters. Observation:\n" + observation;
        String summary = reasoning.reason("SUMMARIZE_OBSERVATION", prompt, context).trim();
        if (summary.length() > maxSummaryCharacters) summary = summary.substring(0, maxSummaryCharacters);
        context.workingMemory().put("observation.summary", summary);
        context.workingMemory().put("observation.summary_source_chars", observation.length());
        context.workingMemory().put("reasoning.latest", null);
        return summary;
    }
}
