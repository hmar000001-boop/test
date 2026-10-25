package dev.coala.core.procedure;

import dev.coala.core.ActionRegistry;
import dev.coala.core.AgentContext;
import dev.coala.core.AgentRunResult;

/** The top-level repeated decision procedure described by CoALA. */
public interface CoalaDecisionProcedure {
    String id();
    AgentRunResult run(String goal, AgentContext context, ActionRegistry actions, int maxCycles);
}
