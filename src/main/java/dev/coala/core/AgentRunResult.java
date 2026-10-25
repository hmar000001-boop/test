package dev.coala.core;

import java.util.List;

public record AgentRunResult(
        String procedure,
        String goal,
        String finalObservation,
        int cyclesExecuted,
        TerminationReason terminationReason,
        List<DecisionEvent> events) {
    public AgentRunResult {
        events = events == null ? List.of() : List.copyOf(events);
        finalObservation = finalObservation == null ? "" : finalObservation;
    }
}
