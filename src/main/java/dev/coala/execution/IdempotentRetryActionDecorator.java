package dev.coala.execution;

import dev.coala.core.ActionCall;
import dev.coala.core.ActionExecutionException;
import dev.coala.core.ActionResult;
import dev.coala.core.AgentContext;

import java.util.function.Predicate;

/** Retries only actions explicitly declared idempotent and only failures accepted by a caller-supplied predicate. */
public final class IdempotentRetryActionDecorator implements ActionExecutionDecorator {
    private final int maxAttempts;
    private final Predicate<RuntimeException> retryable;
    public IdempotentRetryActionDecorator(int maxAttempts, Predicate<RuntimeException> retryable) {
        if (maxAttempts < 1 || maxAttempts > 5) throw new IllegalArgumentException("maxAttempts must be 1..5");
        this.maxAttempts = maxAttempts; this.retryable = java.util.Objects.requireNonNull(retryable);
    }
    @Override public dev.coala.core.CoalaAction decorate(dev.coala.core.CoalaAction action) {
        if (maxAttempts > 1 && !action.idempotent()) throw new IllegalArgumentException("Refusing to retry non-idempotent action: " + action.id());
        return new DelegatingAction(action) {
            @Override public ActionResult execute(ActionCall call, AgentContext context) {
                for (int attempt = 1; ; attempt++) {
                    try { return delegate.execute(call, context); }
                    catch (RuntimeException ex) {
                        if (attempt >= maxAttempts || !retryable.test(ex)) throw ex;
                    }
                }
            }
        };
    }
}
