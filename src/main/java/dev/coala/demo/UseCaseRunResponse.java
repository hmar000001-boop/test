package dev.coala.demo;

import dev.coala.core.AgentRunResult;

/** API envelope allowing the caller to resume the same long-term-memory session later. */
public record UseCaseRunResponse(String sessionId, AgentRunResult run) {}
