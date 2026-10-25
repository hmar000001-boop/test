package dev.coala.execution;

import dev.coala.core.ActionArgumentDefinition;
import dev.coala.core.ActionCall;
import dev.coala.core.ActionDescriptor;
import dev.coala.core.ActionResult;
import dev.coala.core.ActionRisk;
import dev.coala.core.ActionKind;
import dev.coala.core.AgentContext;
import dev.coala.core.CoalaAction;

import java.util.List;
import java.util.Set;

public abstract class DelegatingAction implements CoalaAction {
    protected final CoalaAction delegate;
    protected DelegatingAction(CoalaAction delegate) { this.delegate = java.util.Objects.requireNonNull(delegate); }
    @Override public String id() { return delegate.id(); }
    @Override public String description() { return delegate.description(); }
    @Override public ActionKind kind() { return delegate.kind(); }
    @Override public Set<String> requiredArguments() { return delegate.requiredArguments(); }
    @Override public Set<String> optionalArguments() { return delegate.optionalArguments(); }
    @Override public List<ActionArgumentDefinition> argumentDefinitions() { return delegate.argumentDefinitions(); }
    @Override public ActionRisk risk() { return delegate.risk(); }
    @Override public boolean idempotent() { return delegate.idempotent(); }
    @Override public void validate(ActionCall call) { delegate.validate(call); }
    @Override public ActionDescriptor descriptor() {
        ActionDescriptor d = delegate.descriptor();
        return new ActionDescriptor(d.id(), d.kind(), d.description(), d.requiredArguments(), d.optionalArguments(), d.argumentDefinitions(), d.risk(), d.idempotent());
    }
    @Override public ActionResult execute(ActionCall call, AgentContext context) { return delegate.execute(call, context); }
}
