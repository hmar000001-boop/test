package dev.coala.core.procedure;

import dev.coala.core.AgentContext;

import java.util.Map;

/** Experimental metareasoning hook for choosing among fully configured decision procedures. */
@FunctionalInterface
public interface ProcedureRoutingPolicy {
    String select(String goal, AgentContext context, Map<String, CoalaDecisionProcedure> availableProcedures);
}
