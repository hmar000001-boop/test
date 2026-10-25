package dev.coala.core.agent;

import dev.coala.core.*;
import dev.coala.core.memory.InMemoryLongTermMemory;
import dev.coala.core.procedure.CoalaDecisionProcedure;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class AgentAssemblyTest {
    @Test void agentDefinitionCreatesContextsAndPublishesSafeTraceAndCompletionHook() {
        var action = new TestAction();
        var procedure = new CoalaDecisionProcedure() {
            public String id() { return "test-procedure"; }
            public AgentRunResult run(String goal, AgentContext context, ActionRegistry actions, int maxCycles) {
                context.workingMemory().put("test.marker", "context-created");
                return new AgentRunResult(id(), goal, "finished", 1, TerminationReason.TERMINAL_ACTION,
                        List.of(new DecisionEvent(1, DecisionStage.EXECUTION, "executed test action")));
            }
        };
        var trace = new ArrayList<DecisionTrace>();
        var completion = new AtomicBoolean();
        var definition = CoalaAgentDefinition.of("agent-test", procedure, List.of(action),
                AgentContextFactory.isolated(InMemoryLongTermMemory::new), 3)
                .withTraceListeners(List.of(trace::add))
                .withCompletionListeners(List.of((result, context) -> completion.set(context.workingMemory().getString("test.marker", "").equals("context-created"))));
        var agent = new CoalaAgent(definition);
        var result = agent.run(new AgentRunRequest("check assembly", "session-a", "principal-a", null));
        assertEquals(TerminationReason.TERMINAL_ACTION, result.terminationReason());
        assertTrue(completion.get());
        assertTrue(trace.stream().anyMatch(t -> t.eventType().equals("RUN_STARTED")));
        assertTrue(trace.stream().anyMatch(t -> t.eventType().equals("RUN_COMPLETED")));
        assertTrue(trace.stream().allMatch(t -> !t.summary().contains("secret-token")));
    }

    @Test void contextFactoryCreatesFreshWorkingMemoryForEachRun() {
        var factory = AgentContextFactory.isolated(InMemoryLongTermMemory::new);
        var first = factory.create(AgentRunRequest.goal("first"));
        first.workingMemory().put("x", "one");
        var second = factory.create(AgentRunRequest.goal("second"));
        assertTrue(second.workingMemory().get("x").isEmpty());
        assertNotSame(first.longTermMemory(), second.longTermMemory());
    }

    @Test void definitionRejectsInvalidCycleBudget() {
        assertThrows(IllegalArgumentException.class, () -> CoalaAgentDefinition.of("bad", new CoalaDecisionProcedure() {
            public String id() { return "p"; }
            public AgentRunResult run(String goal, AgentContext context, ActionRegistry actions, int maxCycles) { return null; }
        }, List.of(new TestAction()), AgentContextFactory.isolated(InMemoryLongTermMemory::new), 0));
    }

    private static final class TestAction implements GroundingAction {
        public String id() { return "test"; }
        public String description() { return "test action"; }
        public ActionResult execute(ActionCall call, AgentContext context) { return ActionResult.finish("finished"); }
    }
}
