package dev.coala.core.planning;

import dev.coala.core.*;
import dev.coala.core.action.ActionOutputParser;
import dev.coala.core.action.ActionQualityEvaluation;
import dev.coala.core.action.ReasoningAction;
import dev.coala.core.memory.InMemoryLongTermMemory;
import dev.coala.core.procedure.FixedActionSetDecisionProcedure;
import dev.coala.core.procedure.IterativeRefinementDecisionProcedure;
import dev.coala.core.procedure.PlanFirstDecisionProcedure;
import dev.coala.core.procedure.SearchTreeDecisionProcedure;
import dev.coala.core.planning.search.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ReusablePlanningComponentsTest {
    @Test void fixedActionSetUsesEvaluatorAndSelectionWithoutInvokingAnLlm() {
        AtomicInteger lowRuns = new AtomicInteger(), highRuns = new AtomicInteger();
        GroundingAction low = new TestAction("low_risk", ActionRisk.LOW, lowRuns, true);
        GroundingAction high = new TestAction("high_risk", ActionRisk.HIGH, highRuns, true);
        var procedure = new FixedActionSetDecisionProcedure(List.of(new ActionCall("high_risk", Map.of()), new ActionCall("low_risk", Map.of())), new RuleBasedCandidateEvaluator());
        var context = context(); var registry = new ActionRegistry(List.of(low, high));
        var result = procedure.run("choose safe action", context, registry, 3);
        assertEquals(1, lowRuns.get()); assertEquals(0, highRuns.get());
        assertEquals(TerminationReason.TERMINAL_ACTION, result.terminationReason());
        assertEquals("low_risk", context.workingMemory().getString("selected_action_id", ""));
    }

    @Test void planFirstExecutesOneValidatedStepPerCycle() {
        AtomicInteger reasonCalls = new AtomicInteger();
        ReasoningAction reasoning = (operation, instruction, context) -> {
            reasonCalls.incrementAndGet();
            return "{\"steps\":[{\"actionId\":\"step_one\",\"arguments\":{}},{\"actionId\":\"step_two\",\"arguments\":{}}]}";
        };
        AtomicInteger firstRuns = new AtomicInteger(), secondRuns = new AtomicInteger();
        var procedure = new PlanFirstDecisionProcedure(reasoning, new ActionOutputParser(), 3);
        var context = context();
        var result = procedure.run("run a short plan", context, new ActionRegistry(List.of(
                new TestAction("step_one", ActionRisk.LOW, firstRuns, false),
                new TestAction("step_two", ActionRisk.LOW, secondRuns, false))), 2);
        assertEquals(1, firstRuns.get()); assertEquals(1, secondRuns.get());
        assertEquals(1, reasonCalls.get(), "the remaining planned action should be reused next cycle");
        assertEquals(TerminationReason.MAX_CYCLES_REACHED, result.terminationReason());
        assertEquals(0, ((List<?>) context.workingMemory().get("plan.first_steps").orElseThrow()).size());
    }

    @Test void iterativeRefinementOnlyExecutesAcceptedFinalCandidate() {
        AtomicInteger reasonCalls = new AtomicInteger(), executed = new AtomicInteger();
        ReasoningAction reasoning = (operation, instruction, context) -> {
            int call = reasonCalls.incrementAndGet();
            return call == 1 ? "{\"actionId\":\"submit\",\"arguments\":{\"value\":\"bad\"}}"
                    : "{\"actionId\":\"submit\",\"arguments\":{\"value\":\"good\"}}";
        };
        GroundingAction submit = new GroundingAction() {
            public String id() { return "submit"; }
            public String description() { return "Submit accepted result"; }
            public Set<String> requiredArguments() { return Set.of("value"); }
            public ActionResult execute(ActionCall call, AgentContext context) { executed.incrementAndGet(); return ActionResult.finish(call.argument("value", "")); }
        };
        ActionQualityEvaluator quality = (candidate, context, actions) -> new ActionQualityEvaluation(
                candidate.argument("value", "").equals("good"), candidate.argument("value", "").equals("good") ? .95 : .2,
                candidate.argument("value", "").equals("good") ? "valid" : "replace bad value");
        var procedure = new IterativeRefinementDecisionProcedure(reasoning, new ActionOutputParser(), quality, 3, .8);
        var result = procedure.run("refine a candidate", context(), new ActionRegistry(List.of(submit)), 2);
        assertEquals(2, reasonCalls.get()); assertEquals(1, executed.get());
        assertEquals("good", result.finalObservation());
        assertEquals(TerminationReason.TERMINAL_ACTION, result.terminationReason());
    }

    @Test void searchAlgorithmsExploreSimulatedStateAndReturnRegisteredFirstAction() {
        GroundingAction goA = new TestAction("go_a", ActionRisk.LOW, new AtomicInteger(), true);
        GroundingAction goB = new TestAction("go_b", ActionRisk.LOW, new AtomicInteger(), true);
        ActionRegistry registry = new ActionRegistry(List.of(goA, goB));
        SearchDomain<Integer> domain = new SearchDomain<>() {
            public Integer initialState(AgentContext context) { return 0; }
            public List<SearchTransition<Integer>> expand(Integer state, AgentContext context, ActionRegistry actions) {
                return state == 0 ? List.of(new SearchTransition<>(new ActionCall("go_a", Map.of()), 1, 2, true),
                        new SearchTransition<>(new ActionCall("go_b", Map.of()), 2, 1, true)) : List.of();
            }
            public double evaluate(Integer state, AgentContext context) { return state == 1 ? 10 : 2; }
            public boolean terminal(Integer state, AgentContext context) { return state != 0; }
        };
        AgentContext context = context();
        SearchBudget budget = new SearchBudget(20, 3);
        for (SearchAlgorithm<Integer> algorithm : List.<SearchAlgorithm<Integer>>of(new BreadthFirstSearch<Integer>(), new DepthFirstSearch<Integer>(), new MonteCarloTreeSearch<Integer>(1.2, 42))) {
            SearchResult<Integer> result = algorithm.search(0, domain, context, registry, budget);
            assertTrue(Set.of("go_a", "go_b").contains(result.selectedNode().firstAction().actionId()));
            assertTrue(result.nodesExpanded() <= budget.maxNodes());
            assertTrue(Double.isFinite(result.score()));
        }
        var procedure = new SearchTreeDecisionProcedure<>(domain, new BreadthFirstSearch<Integer>(), budget);
        var runResult = procedure.run("pick simulated path", context(), registry, 1);
        assertEquals(TerminationReason.TERMINAL_ACTION, runResult.terminationReason());
    }

    @Test void searchTreeMaterializationNeverExceedsNodeBudgetUnderWideFanOut() {
        GroundingAction choose = new TestAction("choose", ActionRisk.LOW, new AtomicInteger(), true);
        ActionRegistry registry = new ActionRegistry(List.of(choose));
        SearchDomain<Integer> wideDomain = new SearchDomain<>() {
            public Integer initialState(AgentContext context) { return 0; }
            public List<SearchTransition<Integer>> expand(Integer state, AgentContext context, ActionRegistry actions) {
                if (state != 0) return List.of();
                return java.util.stream.IntStream.range(0, 500)
                        .mapToObj(i -> new SearchTransition<>(new ActionCall("choose", Map.of()), i + 1, i, true))
                        .toList();
            }
            public double evaluate(Integer state, AgentContext context) { return state; }
            public boolean terminal(Integer state, AgentContext context) { return state != 0; }
        };
        SearchBudget smallBudget = new SearchBudget(10, 4);
        for (SearchAlgorithm<Integer> algorithm : List.<SearchAlgorithm<Integer>>of(
                new BreadthFirstSearch<Integer>(), new DepthFirstSearch<Integer>(), new MonteCarloTreeSearch<Integer>(1.2, 7))) {
            SearchResult<Integer> result = algorithm.search(0, wideDomain, context(), registry, smallBudget);
            assertNotNull(result.selectedNode().parent());
            assertTrue(result.selectedNode().parent().children().size() <= 9,
                    algorithm.id() + " exceeded budget while materializing wide fan-out");
            assertTrue(result.nodesExpanded() <= smallBudget.maxNodes());
        }
        assertThrows(IllegalArgumentException.class, () -> new SearchBudget(1, 2));
    }

    private static AgentContext context() { return new AgentContext(new WorkingMemory(), new InMemoryLongTermMemory()); }
    private static final class TestAction implements GroundingAction {
        private final String id; private final ActionRisk risk; private final AtomicInteger runs; private final boolean terminal;
        TestAction(String id, ActionRisk risk, AtomicInteger runs, boolean terminal) { this.id=id; this.risk=risk; this.runs=runs; this.terminal=terminal; }
        public String id() { return id; } public String description() { return id; } public ActionRisk risk() { return risk; }
        public ActionResult execute(ActionCall call, AgentContext context) { runs.incrementAndGet(); return terminal ? ActionResult.finish(id) : ActionResult.continueWith(id); }
    }
}
