package dev.coala.security;

import dev.coala.actions.dialogue.HumanClarificationAction;
import dev.coala.actions.dialogue.InMemoryPendingInteractionStore;
import dev.coala.core.*;
import dev.coala.core.agent.*;
import dev.coala.core.memory.InMemoryLongTermMemory;
import dev.coala.core.memory.MemoryNamespace;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ApprovalAndInteractionTest {
    @Test void highRiskActionWaitsForExactPrincipalBoundApproval() {
        AtomicInteger executions = new AtomicInteger();
        GroundingAction delegate = new GroundingAction() {
            public String id() { return "publish"; }
            public String description() { return "Publish an artifact"; }
            public Set<String> requiredArguments() { return Set.of("target"); }
            public ActionRisk risk() { return ActionRisk.HIGH; }
            public ActionResult execute(ActionCall call, AgentContext context) { executions.incrementAndGet(); return ActionResult.finish("published"); }
        };
        var approvals = new InMemoryActionApprovalPolicy();
        var protectedAction = new ApprovalRequiredAction(delegate, approvals);
        var context = new AgentContext(new WorkingMemory(), new InMemoryLongTermMemory(),
                new MemoryNamespace("agent", "session", "tenant", "domain"), "session", "alice");
        var call = new ActionCall("publish", Map.of("target", "release-v1"));
        var first = protectedAction.execute(call, context);
        assertTrue(first.awaitingInput());
        assertEquals(0, executions.get());
        @SuppressWarnings("unchecked") Map<String,Object> pending = (Map<String,Object>) context.workingMemory().get("interaction.pendingApproval").orElseThrow();
        String requestId = (String) pending.get("requestId");
        assertFalse(approvals.approve(requestId, "mallory"));
        assertTrue(approvals.approve(requestId, "alice"));
        assertTrue(protectedAction.execute(call, context).terminal());
        assertEquals(1, executions.get());
        assertTrue(protectedAction.execute(new ActionCall("publish", Map.of("target", "release-v2")), context).awaitingInput(), "approval does not transfer to different arguments");
    }

    @Test void clarificationCanBeAnsweredOnlyByMatchingSessionAndPrincipalThenInjectedIntoResumeContext() {
        var store = new InMemoryPendingInteractionStore(10);
        var action = new HumanClarificationAction(store, Duration.ofMinutes(5));
        var namespace = new MemoryNamespace("agent", "session-1", "tenant", "travel");
        var context = new AgentContext(new WorkingMemory(), new InMemoryLongTermMemory(), namespace, "session-1", "alice");
        context.workingMemory().put("goal", "Plan a trip");
        var result = action.execute(new ActionCall("ask_user", Map.of("question", "Which dates?")), context);
        assertTrue(result.awaitingInput());
        @SuppressWarnings("unchecked") Map<String,Object> pending = (Map<String,Object>) context.workingMemory().get("interaction.pending").orElseThrow();
        String interactionId = (String) pending.get("interactionId");
        assertFalse(store.submitAnswer(interactionId, "session-2", "alice", "November"));
        assertFalse(store.submitAnswer(interactionId, "session-1", "mallory", "November"));
        assertTrue(store.submitAnswer(interactionId, "session-1", "alice", "November 12 to 16"));
        var factory = new PendingInteractionContextFactory(AgentContextFactory.isolated(InMemoryLongTermMemory::new), store);
        var resumed = factory.create(new AgentRunRequest("Continue trip planning", "session-1", "alice", namespace));
        assertEquals("November 12 to 16", resumed.workingMemory().getString("interaction.answer", ""));
        assertEquals("Which dates?", resumed.workingMemory().getString("interaction.answeredQuestion", ""));
    }
}
