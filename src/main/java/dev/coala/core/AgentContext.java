package dev.coala.core;

import dev.coala.core.memory.LongTermMemory;
import dev.coala.core.memory.MemoryNamespace;

import java.util.Objects;

/** Per-run active state. Never share one AgentContext between concurrent runs. */
public final class AgentContext {
    private final WorkingMemory workingMemory;
    private final LongTermMemory longTermMemory;
    private int cycle;
    private final String sessionId;
    private final String principal;
    private final MemoryNamespace namespace;

    public AgentContext(WorkingMemory workingMemory, LongTermMemory longTermMemory) {
        this(workingMemory, longTermMemory, MemoryNamespace.DEFAULT, "", "anonymous");
    }

    public AgentContext(WorkingMemory workingMemory, LongTermMemory longTermMemory,
                        MemoryNamespace namespace, String sessionId, String principal) {
        this.workingMemory = Objects.requireNonNull(workingMemory, "workingMemory");
        this.longTermMemory = Objects.requireNonNull(longTermMemory, "longTermMemory");
        this.namespace = namespace == null ? MemoryNamespace.DEFAULT : namespace;
        this.sessionId = sessionId == null ? "" : sessionId;
        this.principal = principal == null ? "anonymous" : principal;
    }

    public WorkingMemory workingMemory() { return workingMemory; }
    public LongTermMemory longTermMemory() { return longTermMemory; }
    public int cycle() { return cycle; }
    public MemoryNamespace namespace() { return namespace; }
    public String sessionId() { return sessionId; }
    public String principal() { return principal; }

    public void setCycle(int cycle) {
        if (cycle < 1) throw new IllegalArgumentException("cycle must be positive");
        this.cycle = cycle;
    }
}
