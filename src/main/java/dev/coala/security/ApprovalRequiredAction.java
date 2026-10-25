package dev.coala.security;

import dev.coala.core.ActionArgumentDefinition;
import dev.coala.core.ActionCall;
import dev.coala.core.ActionDescriptor;
import dev.coala.core.ActionResult;
import dev.coala.core.ActionRisk;
import dev.coala.core.AgentContext;
import dev.coala.core.CoalaAction;

import java.util.List;
import java.util.Set;

/** Decorates an action with approval; grant is bound to the authenticated principal and exact call arguments. */
public final class ApprovalRequiredAction implements CoalaAction {
    private final CoalaAction delegate;
    private final ActionApprovalPolicy policy;
    public ApprovalRequiredAction(CoalaAction delegate, ActionApprovalPolicy policy) {
        this.delegate = java.util.Objects.requireNonNull(delegate); this.policy = java.util.Objects.requireNonNull(policy);
    }
    @Override public String id() { return delegate.id(); }
    @Override public String description() { return delegate.description() + " (approval policy enforced by wrapper)"; }
    @Override public dev.coala.core.ActionKind kind() { return delegate.kind(); }
    @Override public Set<String> requiredArguments() { return delegate.requiredArguments(); }
    @Override public Set<String> optionalArguments() { return delegate.optionalArguments(); }
    @Override public List<ActionArgumentDefinition> argumentDefinitions() { return delegate.argumentDefinitions(); }
    @Override public ActionRisk risk() { return delegate.risk(); }
    @Override public boolean idempotent() { return delegate.idempotent(); }
    @Override public void validate(ActionCall call) { delegate.validate(call); }
    @Override public ActionDescriptor descriptor() {
        ActionDescriptor d = delegate.descriptor();
        return new ActionDescriptor(d.id(), d.kind(), description(), d.requiredArguments(), d.optionalArguments(), d.argumentDefinitions(), d.risk(), d.idempotent());
    }
    @Override public ActionResult execute(ActionCall call, AgentContext context) {
        ActionApprovalPolicy.ApprovalDecision decision = policy.authorize(delegate, call, context);
        if (!decision.approved()) {
            context.workingMemory().put("interaction.pendingApproval", java.util.Map.of(
                    "requestId", decision.requestId(), "actionId", call.actionId(), "argumentHash", InMemoryActionApprovalPolicy.argumentHash(call)));
            return ActionResult.awaitingInput("Approval required. Request ID: " + decision.requestId());
        }
        context.workingMemory().put("interaction.pendingApproval", null);
        return delegate.execute(call, context);
    }
}
