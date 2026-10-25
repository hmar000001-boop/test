package dev.coala.core;

import dev.coala.core.action.ActionOutputParser;
import dev.coala.core.action.LlmReasoningAction;
import dev.coala.core.action.MemoryRetrievalAction;
import dev.coala.core.action.ReasoningAction;
import dev.coala.core.memory.InMemoryLongTermMemory;
import dev.coala.core.memory.MemoryRecord;
import dev.coala.core.memory.MemoryType;
import dev.coala.core.procedure.DeliberativeDecisionProcedure;
import dev.coala.core.procedure.DirectActionDecisionProcedure;
import dev.coala.core.procedure.IterativeReasonActDecisionProcedure;
import dev.coala.demo.action.CodingGroundingActions;
import dev.coala.demo.action.DialogueReplyAction;
import dev.coala.demo.action.PuzzleGroundingActions;
import dev.coala.demo.action.RememberExperienceAction;
import dev.coala.llm.LanguageModel;
import dev.coala.llm.LanguageModelException;
import dev.coala.llm.ScriptedLanguageModel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class DecisionProcedureTest {
    private final ScriptedLanguageModel model = new ScriptedLanguageModel();
    private final ActionOutputParser parser = new ActionOutputParser();
    private final LlmReasoningAction reasoning = new LlmReasoningAction(model);

    @Test
    void directProcedureFinishesWithOneGroundingActionAndRecordsReasoning() {
        var context = new AgentContext(new WorkingMemory(), new InMemoryLongTermMemory());
        var procedure = new DirectActionDecisionProcedure(reasoning, parser);
        AgentRunResult result = procedure.run("Explain CoALA briefly", context,
                new ActionRegistry(List.of(new DialogueReplyAction())), 5);
        assertEquals(TerminationReason.TERMINAL_ACTION, result.terminationReason());
        assertEquals(1, result.cyclesExecuted());
        assertTrue(result.finalObservation().contains("direct-action demo response"));
        assertTrue(result.events().stream().anyMatch(e -> e.stage() == DecisionStage.REASONING));
    }

    @Test
    void iterativeCodingProcedureUsesRepeatedCyclesAndSelectedLearningAction() {
        var context = new AgentContext(new WorkingMemory(), new InMemoryLongTermMemory());
        var procedure = new IterativeReasonActDecisionProcedure(reasoning, parser);
        var actions = new ActionRegistry(List.of(
                CodingGroundingActions.inspectProject(), CodingGroundingActions.applyPatch(),
                CodingGroundingActions.runTests(), new RememberExperienceAction(), CodingGroundingActions.respond()));
        AgentRunResult result = procedure.run("Fix the demo issue and verify it", context, actions, 8);
        assertEquals(TerminationReason.TERMINAL_ACTION, result.terminationReason());
        assertEquals(5, result.cyclesExecuted());
        assertTrue(result.events().stream().anyMatch(e -> e.stage() == DecisionStage.LEARNING));
        assertTrue(result.finalObservation().contains("mock coding workflow completed"));
        assertEquals("respond", context.workingMemory().getString("selected_action_id", ""));
        assertTrue(context.workingMemory().get("selected_action_arguments").isPresent());
    }

    @Test
    void deliberativeProcedureRetrievesProposesEvaluatesAndSelects() {
        var memory = new InMemoryLongTermMemory();
        memory.store(MemoryRecord.of(MemoryType.EPISODIC, "Previous demo puzzle experience: inspect clues first."));
        var context = new AgentContext(new WorkingMemory(), memory);
        var procedure = new DeliberativeDecisionProcedure(reasoning, new MemoryRetrievalAction(), parser, 6);
        var actions = new ActionRegistry(List.of(PuzzleGroundingActions.inspectClues(),
                PuzzleGroundingActions.checkHypothesis(), PuzzleGroundingActions.submitAnswer()));
        AgentRunResult result = procedure.run("Solve the color puzzle", context, actions, 4);
        assertEquals(TerminationReason.TERMINAL_ACTION, result.terminationReason());
        assertEquals("Puzzle completed: blue is correct.", result.finalObservation());
        assertTrue(result.events().stream().anyMatch(e -> e.stage() == DecisionStage.RETRIEVAL));
        assertTrue(result.events().stream().anyMatch(e -> e.stage() == DecisionStage.PROPOSAL));
        assertTrue(result.events().stream().anyMatch(e -> e.stage() == DecisionStage.EVALUATION));
        assertTrue(result.events().stream().anyMatch(e -> e.stage() == DecisionStage.SELECTION));
        assertTrue(result.events().stream().anyMatch(e -> e.stage() == DecisionStage.REASONING));
        Object candidates = context.workingMemory().get("plan.candidates").orElseThrow();
        Object evaluations = context.workingMemory().get("plan.evaluations").orElseThrow();
        assertInstanceOf(List.class, candidates);
        assertEquals(2, ((List<?>) candidates).size());
        assertInstanceOf(List.class, evaluations);
        assertEquals(2, ((List<?>) evaluations).size());
        assertTrue(context.workingMemory().get("plan.selectedAction").isPresent());
    }

    @Test
    void deliberativeProcedureReproposesWhenCandidateSetIsRejected() {
        AtomicInteger proposalCalls = new AtomicInteger();
        AtomicInteger evaluationCalls = new AtomicInteger();
        java.util.concurrent.atomic.AtomicBoolean structuredCandidatesVisibleDuringEvaluation = new java.util.concurrent.atomic.AtomicBoolean();
        LanguageModel rejectThenAccept = prompt -> {
            if (prompt.contains("OPERATION=PROPOSE_CANDIDATES")) {
                proposalCalls.incrementAndGet();
                return "{\"candidates\":["
                        + "{\"actionId\":\"inspect_clues\",\"arguments\":{}},"
                        + "{\"actionId\":\"check_hypothesis\",\"arguments\":{\"answer\":\"blue\"}}]}";
            }
            if (prompt.contains("OPERATION=EVALUATE_CANDIDATES")) {
                if (prompt.contains("plan.candidates =") && prompt.contains("inspect_clues")) {
                    structuredCandidatesVisibleDuringEvaluation.set(true);
                }
                return evaluationCalls.incrementAndGet() == 1
                        ? "{\"rejectAll\":true,\"scores\":[0.8,0.2]}"
                        : "{\"rejectAll\":false,\"scores\":[0.9,0.1]}";
            }
            throw new IllegalArgumentException("Unexpected operation");
        };
        var context = new AgentContext(new WorkingMemory(), new InMemoryLongTermMemory());
        var procedure = new DeliberativeDecisionProcedure(
                new LlmReasoningAction(rejectThenAccept), new MemoryRetrievalAction(), parser, 4, 3);
        AtomicInteger executedActions = new AtomicInteger();
        GroundingAction inspect = new GroundingAction() {
            @Override public String id() { return "inspect_clues"; }
            @Override public String description() { return "Inspect clues"; }
            @Override public ActionResult execute(ActionCall call, AgentContext ctx) {
                executedActions.incrementAndGet();
                return ActionResult.continueWith("inspected");
            }
        };
        GroundingAction check = new GroundingAction() {
            @Override public String id() { return "check_hypothesis"; }
            @Override public String description() { return "Check a hypothesis"; }
            @Override public Set<String> requiredArguments() { return Set.of("answer"); }
            @Override public ActionResult execute(ActionCall call, AgentContext ctx) {
                executedActions.incrementAndGet();
                return ActionResult.continueWith("checked");
            }
        };
        AgentRunResult result = procedure.run("Explore puzzle options", context,
                new ActionRegistry(List.of(inspect, check)), 1);
        assertEquals(2, proposalCalls.get());
        assertEquals(2, evaluationCalls.get());
        assertEquals(1, executedActions.get(), "No action should execute until a candidate set is accepted");
        assertTrue(result.events().stream().anyMatch(e -> e.stage() == DecisionStage.SELECTION
                && e.summary().contains("Rejected all candidates")));
        assertEquals("inspect_clues", context.workingMemory().getString("selected_action_id", ""));
    }

    @Test
    void repeatedCandidateRejectionStopsAtBoundAndPreservesPlanningTrace() {
        LanguageModel alwaysReject = prompt -> {
            if (prompt.contains("OPERATION=PROPOSE_CANDIDATES")) {
                return "{\"candidates\":[{\"actionId\":\"inspect\",\"arguments\":{}},"
                        + "{\"actionId\":\"check\",\"arguments\":{\"answer\":\"blue\"}}]}";
            }
            return "{\"rejectAll\":true,\"scores\":[0.2,0.1]}";
        };
        AtomicInteger executed = new AtomicInteger();
        GroundingAction inspect = new GroundingAction() {
            @Override public String id() { return "inspect"; }
            @Override public String description() { return "Inspect"; }
            @Override public ActionResult execute(ActionCall call, AgentContext ctx) {
                executed.incrementAndGet(); return ActionResult.continueWith("inspected");
            }
        };
        GroundingAction check = new GroundingAction() {
            @Override public String id() { return "check"; }
            @Override public String description() { return "Check"; }
            @Override public Set<String> requiredArguments() { return Set.of("answer"); }
            @Override public ActionResult execute(ActionCall call, AgentContext ctx) {
                executed.incrementAndGet(); return ActionResult.continueWith("checked");
            }
        };
        var procedure = new DeliberativeDecisionProcedure(new LlmReasoningAction(alwaysReject),
                new MemoryRetrievalAction(), parser, 4, 2);
        var context = new AgentContext(new WorkingMemory(), new InMemoryLongTermMemory());

        PlanningExhaustedException exception = assertThrows(PlanningExhaustedException.class,
                () -> procedure.run("test rejection", context, new ActionRegistry(List.of(inspect, check)), 1));
        assertEquals(2, exception.attempts());
        assertTrue(exception.events().stream().anyMatch(event -> event.stage() == DecisionStage.SELECTION
                && event.summary().contains("Rejected all candidates")));
        assertEquals(0, executed.get());
        assertTrue(context.workingMemory().get("selected_action_id").isEmpty());
    }

    @Test
    void selectedActionNameAndArgumentsAreWrittenToWorkingMemoryBeforeExecution() {
        LanguageModel oneAction = prompt -> "{\"actionId\":\"capture\",\"arguments\":{\"value\":\"expected\"}}";
        var context = new AgentContext(new WorkingMemory(), new InMemoryLongTermMemory());
        AtomicReference<Object> selectedActionAtExecution = new AtomicReference<>();
        AtomicReference<Object> planSelectedActionAtExecution = new AtomicReference<>();
        AtomicReference<Object> planCandidatesAtExecution = new AtomicReference<>();
        GroundingAction capture = new GroundingAction() {
            @Override public String id() { return "capture"; }
            @Override public String description() { return "Capture selected state"; }
            @Override public Set<String> requiredArguments() { return Set.of("value"); }
            @Override public ActionResult execute(ActionCall call, AgentContext ctx) {
                selectedActionAtExecution.set(ctx.workingMemory().get("selected_action").orElse(null));
                planSelectedActionAtExecution.set(ctx.workingMemory().get("plan.selectedAction").orElse(null));
                planCandidatesAtExecution.set(ctx.workingMemory().get("plan.candidates").orElse(null));
                assertEquals("capture", ctx.workingMemory().getString("selected_action_id", ""));
                assertEquals("expected", call.argument("value", ""));
                return ActionResult.finish("done");
            }
        };
        new DirectActionDecisionProcedure(new LlmReasoningAction(oneAction), parser)
                .run("Capture", context, new ActionRegistry(List.of(capture)), 1);
        assertNotNull(selectedActionAtExecution.get());
        assertTrue(selectedActionAtExecution.get().toString().contains("expected"));
        assertNotNull(planSelectedActionAtExecution.get());
        assertTrue(planSelectedActionAtExecution.get().toString().contains("expected"));
        assertEquals(1, ((List<?>) planCandidatesAtExecution.get()).size());
    }

    @Test
    void providerFailuresRemainProviderFailures() {
        LanguageModel broken = prompt -> { throw new IllegalStateException("provider unavailable"); };
        var procedure = new DirectActionDecisionProcedure(new LlmReasoningAction(broken), parser);
        var context = new AgentContext(new WorkingMemory(), new InMemoryLongTermMemory());
        assertThrows(LanguageModelException.class,
                () -> procedure.run("test", context,
                        new ActionRegistry(List.of(new DialogueReplyAction())), 1));
    }

    @Test
    void reasoningCallBudgetStopsExcessiveIterations() {
        var boundedReasoning = new LlmReasoningAction(model, 1);
        var procedure = new IterativeReasonActDecisionProcedure(boundedReasoning, parser);
        var context = new AgentContext(new WorkingMemory(), new InMemoryLongTermMemory());
        var actions = new ActionRegistry(List.of(
                CodingGroundingActions.inspectProject(), CodingGroundingActions.applyPatch(),
                CodingGroundingActions.runTests(), new RememberExperienceAction(), CodingGroundingActions.respond()));
        assertThrows(DecisionProcedureException.class,
                () -> procedure.run("Fix the mock issue", context, actions, 8));
    }

    @Test
    void malformedActionOutputFailsRatherThanExecutingUnknownAction() {
        assertThrows(DecisionProcedureException.class, () -> parser.parseAction("not json"));
    }

    @Test
    void unknownModelSelectedActionFailsBeforeActionExecution() {
        LanguageModel unknownAction = prompt -> "{\"actionId\":\"not-allowed\",\"arguments\":{}}";
        var procedure = new DirectActionDecisionProcedure(new LlmReasoningAction(unknownAction), parser);
        var context = new AgentContext(new WorkingMemory(), new InMemoryLongTermMemory());
        AtomicInteger count = new AtomicInteger();
        GroundingAction registered = new GroundingAction() {
            @Override public String id() { return "registered"; }
            @Override public String description() { return "test action"; }
            @Override public ActionResult execute(ActionCall call, AgentContext ctx) {
                count.incrementAndGet();
                return ActionResult.finish("should not run");
            }
        };
        assertThrows(InvalidModelOutputException.class, () -> procedure.run("test", context,
                new ActionRegistry(List.of(registered)), 1));
        assertEquals(0, count.get());
        assertTrue(context.workingMemory().get("selected_action_id").isEmpty());
    }

    @Test
    void oversizedModelOutputIsRejectedBeforeItIsStoredInWorkingMemory() {
        LanguageModel oversized = prompt -> "x".repeat(LlmReasoningAction.MAX_MODEL_OUTPUT_CHARACTERS + 1);
        var context = new AgentContext(new WorkingMemory(), new InMemoryLongTermMemory());
        var reasoning = new LlmReasoningAction(oversized);
        context.setCycle(1);
        assertThrows(InvalidModelOutputException.class,
                () -> reasoning.reason("test", "return an action", context));
        assertTrue(context.workingMemory().get("reasoning.latest").isEmpty());
    }

    @Test
    void concurrentRunsKeepWorkingMemoryAndCycleStateIsolated() throws Exception {
        LanguageModel perGoal = prompt -> {
            String goal = prompt.contains("GOAL=alpha") ? "alpha" : "beta";
            return "{\"actionId\":\"capture\",\"arguments\":{\"text\":\"" + goal + "\"}}";
        };
        var sharedProcedure = new DirectActionDecisionProcedure(new LlmReasoningAction(perGoal), parser);
        GroundingAction capture = new GroundingAction() {
            @Override public String id() { return "capture"; }
            @Override public String description() { return "Capture a label"; }
            @Override public java.util.Set<String> requiredArguments() { return java.util.Set.of("text"); }
            @Override public ActionResult execute(ActionCall call, AgentContext ctx) {
                ctx.workingMemory().put("captured", call.argument("text", ""));
                return ActionResult.finish(call.argument("text", ""));
            }
        };
        var registry = new ActionRegistry(List.of(capture));
        var alphaContext = new AgentContext(new WorkingMemory(), new InMemoryLongTermMemory());
        var betaContext = new AgentContext(new WorkingMemory(), new InMemoryLongTermMemory());
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var alpha = executor.submit(() -> sharedProcedure.run("alpha", alphaContext, registry, 1));
            var beta = executor.submit(() -> sharedProcedure.run("beta", betaContext, registry, 1));
            assertEquals("alpha", alpha.get().finalObservation());
            assertEquals("beta", beta.get().finalObservation());
            assertEquals("alpha", alphaContext.workingMemory().getString("captured", ""));
            assertEquals("beta", betaContext.workingMemory().getString("captured", ""));
            assertEquals(1, alpha.get().cyclesExecuted());
            assertEquals(1, beta.get().cyclesExecuted());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void actionExecutionFailuresAreTypedSeparately() {
        LanguageModel selectRegistered = prompt -> "{\"actionId\":\"boom\",\"arguments\":{}}";
        GroundingAction boom = new GroundingAction() {
            @Override public String id() { return "boom"; }
            @Override public String description() { return "throws during execution"; }
            @Override public ActionResult execute(ActionCall call, AgentContext ctx) {
                throw new IllegalStateException("provider-like action failure");
            }
        };
        var procedure = new DirectActionDecisionProcedure(new LlmReasoningAction(selectRegistered), parser);
        var context = new AgentContext(new WorkingMemory(), new InMemoryLongTermMemory());
        assertThrows(ActionExecutionException.class, () -> procedure.run("test", context,
                new ActionRegistry(List.of(boom)), 1));
    }
}
