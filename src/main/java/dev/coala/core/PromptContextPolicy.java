package dev.coala.core;

/** Chooses the working-memory subset for one reasoning operation. */
@FunctionalInterface
public interface PromptContextPolicy {
    WorkingMemoryPromptContextBuilder.Request requestFor(String operation, AgentContext context);

    static PromptContextPolicy defaultPolicy() {
        return (operation, context) -> {
            SetBuilder keys = new SetBuilder();
            // Planning operations use a small stable view by default; use cases can opt into more keys.
            keys.add("goal", "latest_observation", "latest_action", "retrieved_memories", "plan.candidates",
                    "plan.evaluations", "plan.last_rejection", "plan.previousRejectedCandidates", "plan.first_steps",
                    "plan.refinementCandidate", "plan.refinementEvaluation", "reasoning.call_count",
                    "reasoning.last_operation", "reflection.latest", "interaction.pending", "interaction.answer",
                    "retrieved_skills", "web_search.results", "calculator.lastResult", "calculator.lastExpression",
                    "research.topic", "research.brief", "research.source_count", "research.final",
                    "travel.calendar", "travel.destination", "travel.flight_options", "travel.hotel_options",
                    "travel.proposed_itinerary", "travel.estimate_cad", "travel.calendar.checked",
                    "travel.budget_cad", "travel.within_budget", "support.triage");
            // Built-in grounding actions use a per-action namespace for outputs. Include those
            // non-sensitive result keys dynamically without dumping unrelated working memory.
            context.workingMemory().snapshot().keySet().stream()
                    .filter(key -> key.startsWith("http.") || key.startsWith("workspace.") || key.startsWith("web_search."))
                    .forEach(key -> keys.add(key));
            return new WorkingMemoryPromptContextBuilder.Request(keys.values(), java.util.Set.of(),
                    WorkingMemoryPromptContextBuilder.defaultRedactionPatterns(), 12_000, true);
        };
    }

    final class SetBuilder {
        private final java.util.Set<String> values = new java.util.LinkedHashSet<>();
        void add(String... names) { java.util.Collections.addAll(values, names); }
        java.util.Set<String> values() { return java.util.Set.copyOf(values); }
    }
}
