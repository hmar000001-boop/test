package dev.coala.actions.dialogue;

import dev.coala.core.ActionArgumentDefinition;
import dev.coala.core.ActionCall;
import dev.coala.core.ActionResult;
import dev.coala.core.ActionRisk;
import dev.coala.core.AgentContext;
import dev.coala.core.GroundingAction;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/** Creates an asynchronous clarification request and suspends the current run. */
public final class HumanClarificationAction implements GroundingAction {
    private final PendingInteractionStore store;
    private final Duration ttl;
    public HumanClarificationAction(PendingInteractionStore store, Duration ttl) {
        this.store = java.util.Objects.requireNonNull(store);
        if (ttl == null || ttl.isNegative() || ttl.isZero()) throw new IllegalArgumentException("ttl must be positive");
        this.ttl = ttl;
    }
    @Override public String id() { return "ask_user"; }
    @Override public String description() { return "Ask the user for missing information and suspend until an answer arrives."; }
    @Override public Set<String> requiredArguments() { return Set.of("question"); }
    @Override public Set<String> optionalArguments() { return Set.of("contextKey"); }
    @Override public ActionRisk risk() { return ActionRisk.LOW; }
    @Override public List<ActionArgumentDefinition> argumentDefinitions() {
        return List.of(ActionArgumentDefinition.requiredString("question", "One concise, answerable clarification question"),
                ActionArgumentDefinition.optionalString("contextKey", "Working-memory key the caller may use when resuming"));
    }
    @Override public ActionResult execute(ActionCall call, AgentContext context) {
        if (context.sessionId().isBlank()) throw new IllegalStateException("Human clarification requires a stable session ID to resume");
        var item = store.create(context.sessionId(), context.principal(), call.argument("question", ""), Instant.now().plus(ttl));
        context.workingMemory().put("interaction.pending", java.util.Map.of("interactionId", item.id(), "question", item.question(), "contextKey", call.argument("contextKey", "interaction.answer")));
        return ActionResult.awaitingInput("Awaiting user answer. Interaction ID: " + item.id());
    }
}
