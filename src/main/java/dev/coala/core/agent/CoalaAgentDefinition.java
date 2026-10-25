package dev.coala.core.agent;

import dev.coala.core.ActionRegistry;
import dev.coala.core.CoalaAction;
import dev.coala.core.procedure.CoalaDecisionProcedure;

import java.util.List;
import java.util.Objects;

/** Reusable agent assembly: selected procedure, registered action space, context factory and run bound. */
public record CoalaAgentDefinition(String id, CoalaDecisionProcedure decisionProcedure,
                                   ActionRegistry actions, AgentContextFactory contextFactory,
                                   int maxCycles, List<DecisionTraceListener> traceListeners, List<AgentRunCompletionListener> completionListeners) {
    public CoalaAgentDefinition {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("agent id must not be blank");
        Objects.requireNonNull(decisionProcedure, "decisionProcedure");
        Objects.requireNonNull(actions, "actions");
        Objects.requireNonNull(contextFactory, "contextFactory");
        if (maxCycles < 1 || maxCycles > 100) throw new IllegalArgumentException("maxCycles must be between 1 and 100");
        traceListeners = traceListeners == null ? List.of() : List.copyOf(traceListeners);
        completionListeners = completionListeners == null ? List.of() : List.copyOf(completionListeners);
    }

    public CoalaAgentDefinition(String id, CoalaDecisionProcedure procedure, ActionRegistry actions,
                                AgentContextFactory contextFactory, int maxCycles, List<DecisionTraceListener> traceListeners) {
        this(id, procedure, actions, contextFactory, maxCycles, traceListeners, List.of());
    }

    public CoalaAgentDefinition withCompletionListeners(List<AgentRunCompletionListener> listeners) {
        return new CoalaAgentDefinition(id, decisionProcedure, actions, contextFactory, maxCycles, traceListeners, listeners);
    }

    public static CoalaAgentDefinition of(String id, CoalaDecisionProcedure procedure,
                                          List<? extends CoalaAction> actions, AgentContextFactory contextFactory,
                                          int maxCycles) {
        return new CoalaAgentDefinition(id, procedure, new ActionRegistry(actions), contextFactory, maxCycles, List.of(), List.of());
    }

    public CoalaAgentDefinition withTraceListeners(List<DecisionTraceListener> listeners) {
        return new CoalaAgentDefinition(id, decisionProcedure, actions, contextFactory, maxCycles, listeners, completionListeners);
    }
}
