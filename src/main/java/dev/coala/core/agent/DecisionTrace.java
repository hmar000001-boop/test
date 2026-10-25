package dev.coala.core.agent;

import dev.coala.core.DecisionEvent;

import java.time.Instant;
import java.util.UUID;

/** Safe structured trace envelope for logs, tests, dashboards, or metrics adapters. */
public record DecisionTrace(UUID runId, String agentId, String sessionId, String eventType,
                            Integer cycle, String stage, String summary, Instant occurredAt) {
    public DecisionTrace {
        runId = runId == null ? UUID.randomUUID() : runId;
        agentId = agentId == null ? "unknown" : agentId;
        sessionId = sessionId == null ? "" : sessionId;
        eventType = eventType == null ? "EVENT" : eventType;
        stage = stage == null ? "" : stage;
        summary = summary == null ? "" : summary;
        occurredAt = occurredAt == null ? Instant.now() : occurredAt;
    }
    public static DecisionTrace from(UUID runId, String agentId, String sessionId, DecisionEvent event) {
        String safeSummary = switch (event.stage()) {
            case OBSERVATION -> "Observation incorporated into working memory";
            case REASONING -> "Reasoning operation recorded";
            case RETRIEVAL -> "Retrieval operation recorded";
            case PROPOSAL -> "Candidate proposal operation recorded";
            case EVALUATION -> "Candidate evaluation operation recorded";
            case SELECTION -> "Action selection operation recorded";
            case EXECUTION -> "Grounding execution result recorded";
            case LEARNING -> "Learning action result recorded";
            case TERMINATION -> "Decision procedure termination recorded";
        };
        return new DecisionTrace(runId, agentId, sessionId, "DECISION_EVENT", event.cycle(),
                event.stage().name(), safeSummary, Instant.now());
    }
}
