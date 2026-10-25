package dev.coala.execution;

import dev.coala.core.ActionCall;
import dev.coala.core.ActionResult;
import dev.coala.core.AgentContext;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Consumer;

/** Emits outcome-only telemetry; it never emits argument values, prompts, credentials or result bodies. */
public final class TracingActionDecorator implements ActionExecutionDecorator {
    public record Trace(String actionId, String outcome, long elapsedMillis, int cycle, Instant timestamp) { }
    private final Consumer<Trace> sink;
    public TracingActionDecorator(Consumer<Trace> sink) { this.sink = Objects.requireNonNull(sink); }
    @Override public dev.coala.core.CoalaAction decorate(dev.coala.core.CoalaAction action) {
        return new DelegatingAction(action) {
            @Override public ActionResult execute(ActionCall call, AgentContext context) {
                long start = System.nanoTime(); String outcome = "failure";
                try { ActionResult result = delegate.execute(call, context); outcome = result.awaitingInput() ? "awaiting_input" : result.terminal() ? "terminal" : "success"; return result; }
                finally {
                    try { sink.accept(new Trace(id(), outcome, Duration.ofNanos(System.nanoTime() - start).toMillis(), context.cycle(), Instant.now())); }
                    catch (RuntimeException ignored) { /* Telemetry must not change action semantics. */ }
                }
            }
        };
    }
}
