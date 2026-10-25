package dev.coala.execution;

import dev.coala.core.ActionCall;
import dev.coala.core.ActionExecutionException;
import dev.coala.core.ActionResult;
import dev.coala.core.AgentContext;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Optional timeout wrapper. The action must honor interruption for cancellation to stop underlying I/O/work. */
public final class TimeoutActionDecorator implements ActionExecutionDecorator {
    private final ExecutorService executor;
    private final Duration timeout;
    public TimeoutActionDecorator(ExecutorService executor, Duration timeout) {
        this.executor = java.util.Objects.requireNonNull(executor);
        if (timeout == null || timeout.isZero() || timeout.isNegative()) throw new IllegalArgumentException("timeout must be positive");
        this.timeout = timeout;
    }
    @Override public dev.coala.core.CoalaAction decorate(dev.coala.core.CoalaAction action) {
        return new DelegatingAction(action) {
            @Override public ActionResult execute(ActionCall call, AgentContext context) {
                Future<ActionResult> future = executor.submit(() -> delegate.execute(call, context));
                try { return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS); }
                catch (TimeoutException ex) { future.cancel(true); throw new ActionExecutionException("Action timed out: " + id(), ex); }
                catch (InterruptedException ex) { future.cancel(true); Thread.currentThread().interrupt(); throw new ActionExecutionException("Interrupted waiting for action: " + id(), ex); }
                catch (ExecutionException ex) {
                    Throwable cause = ex.getCause();
                    if (cause instanceof RuntimeException runtime) throw runtime;
                    throw new ActionExecutionException("Action failed: " + id(), cause);
                }
            }
        };
    }
}
