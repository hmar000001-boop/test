package dev.coala.core.agent;

import dev.coala.core.AgentContext;
import dev.coala.core.WorkingMemory;
import dev.coala.core.memory.LongTermMemory;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/** Creates isolated per-run working memory and supplies appropriately scoped long-term memory. */
@FunctionalInterface
public interface AgentContextFactory {
    AgentContext create(AgentRunRequest request);

    static AgentContextFactory isolated(Supplier<? extends LongTermMemory> memorySupplier) {
        Objects.requireNonNull(memorySupplier, "memorySupplier");
        return request -> new AgentContext(new WorkingMemory(), Objects.requireNonNull(memorySupplier.get(), "memory supplier returned null"), request.namespace(), request.sessionId(), request.principal());
    }

    static AgentContextFactory scoped(Function<AgentRunRequest, ? extends LongTermMemory> memoryResolver) {
        Objects.requireNonNull(memoryResolver, "memoryResolver");
        return request -> new AgentContext(new WorkingMemory(), Objects.requireNonNull(memoryResolver.apply(request), "memory resolver returned null"), request.namespace(), request.sessionId(), request.principal());
    }
}
