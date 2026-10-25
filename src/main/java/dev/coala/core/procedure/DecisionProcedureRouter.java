package dev.coala.core.procedure;

import dev.coala.core.ActionRegistry;
import dev.coala.core.AgentContext;
import dev.coala.core.AgentRunResult;

import java.util.Map;

/** Optional procedure router; not required by the CoALA core and should be enabled only after evaluation. */
public final class DecisionProcedureRouter implements CoalaDecisionProcedure {
    private final Map<String, CoalaDecisionProcedure> procedures;
    private final String defaultProcedureId;
    private final ProcedureRoutingPolicy policy;
    public DecisionProcedureRouter(Map<String, CoalaDecisionProcedure> procedures, String defaultProcedureId, ProcedureRoutingPolicy policy) {
        if (procedures == null || procedures.isEmpty()) throw new IllegalArgumentException("At least one procedure is required");
        this.procedures = Map.copyOf(procedures);
        if (!this.procedures.containsKey(defaultProcedureId)) throw new IllegalArgumentException("Default procedure not registered");
        this.defaultProcedureId = defaultProcedureId; this.policy = policy == null ? (goal, context, available) -> defaultProcedureId : policy;
    }
    @Override public String id() { return "procedure-router"; }
    @Override public AgentRunResult run(String goal, AgentContext context, ActionRegistry actions, int maxCycles) {
        String selectedId = policy.select(goal, context, procedures);
        CoalaDecisionProcedure selected = procedures.get(selectedId);
        if (selected == null) selected = procedures.get(defaultProcedureId);
        context.workingMemory().put("procedure.router.selected", selected.id());
        return selected.run(goal, context, actions, maxCycles);
    }
}
