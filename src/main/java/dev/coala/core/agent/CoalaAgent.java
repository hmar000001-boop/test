package dev.coala.core.agent;

import dev.coala.core.AgentContext;
import dev.coala.core.AgentRunResult;
import dev.coala.core.DecisionEvent;

import java.util.Objects;
import java.util.UUID;

/** Convenient runtime facade assembled from a CoalaAgentDefinition. */
public final class CoalaAgent {
    private final CoalaAgentDefinition definition;

    public CoalaAgent(CoalaAgentDefinition definition) { this.definition = Objects.requireNonNull(definition, "definition"); }
    public String id() { return definition.id(); }
    public CoalaAgentDefinition definition() { return definition; }

    public AgentRunResult run(String goal) { return run(AgentRunRequest.goal(goal)); }

    public AgentRunResult run(AgentRunRequest request) {
        UUID runId = UUID.randomUUID();
        AgentContext context = definition.contextFactory().create(request);
        if (context == null) throw new IllegalStateException("AgentContextFactory returned null");
        publish(new DecisionTrace(runId, id(), request.sessionId(), "RUN_STARTED", null, "", "Agent run started", null));
        try {
            AgentRunResult result = definition.decisionProcedure().run(request.goal(), context,
                    definition.actions(), definition.maxCycles());
            for (DecisionEvent event : result.events()) publish(DecisionTrace.from(runId, id(), request.sessionId(), event));
            for (AgentRunCompletionListener listener : definition.completionListeners()) {
                try { listener.onCompleted(result, context); }
                catch (RuntimeException ignored) { /* Learning hooks are opt-in and cannot rewrite the completed result. */ }
            }
            publish(new DecisionTrace(runId, id(), request.sessionId(), "RUN_COMPLETED", result.cyclesExecuted(),
                    result.terminationReason().name(), "Agent run completed", null));
            return result;
        } catch (RuntimeException ex) {
            // Do not leak exception messages, prompts, tokens, or action arguments to generic traces.
            publish(new DecisionTrace(runId, id(), request.sessionId(), "RUN_FAILED", context.cycle(),
                    ex.getClass().getSimpleName(), "Agent run failed; inspect controlled application logs", null));
            throw ex;
        }
    }

    private void publish(DecisionTrace trace) {
        for (DecisionTraceListener listener : definition.traceListeners()) {
            try { listener.onTrace(trace); }
            catch (RuntimeException ignored) { /* Observability must not change decision semantics. */ }
        }
    }
}
