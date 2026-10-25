package dev.coala.core.agent;

import dev.coala.core.memory.MemoryNamespace;

/** Framework-level run input for context factories; optional fields do not imply authentication. */
public record AgentRunRequest(String goal, String sessionId, String principal, MemoryNamespace namespace) {
    public AgentRunRequest {
        if (goal == null || goal.isBlank()) throw new IllegalArgumentException("goal must not be blank");
        sessionId = sessionId == null ? "" : sessionId;
        principal = principal == null ? "anonymous" : principal;
        namespace = namespace == null ? MemoryNamespace.DEFAULT : namespace;
    }
    public static AgentRunRequest goal(String goal) { return new AgentRunRequest(goal, "", "anonymous", MemoryNamespace.DEFAULT); }
}
