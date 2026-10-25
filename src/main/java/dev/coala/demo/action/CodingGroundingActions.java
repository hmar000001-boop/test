package dev.coala.demo.action;

import dev.coala.core.ActionCall;
import dev.coala.core.ActionResult;
import dev.coala.core.AgentContext;
import dev.coala.core.GroundingAction;

import java.util.Set;


/** Deterministic mock digital environment for the coding-agent example; it never executes supplied code. */
public final class CodingGroundingActions {
    private CodingGroundingActions() {}

    public static GroundingAction inspectProject() {
        return new FixedAction("inspect_project", "Inspect the demo repository and observe its current state.", call ->
                ActionResult.continueWith("Mock repository inspection: src/Calculator.java contains a documented off-by-one defect in the demo fixture; tests are available."));
    }

    public static GroundingAction applyPatch() {
        return new FixedAction("apply_patch", "Apply a textual patch to the mock repository (no filesystem change).", call -> {
            String change = call.argument("change", "correct the off-by-one boundary");
            return ActionResult.continueWith("Mock patch recorded: " + change + ". No real source files were changed.");
        });
    }

    public static GroundingAction runTests() {
        return new FixedAction("run_tests", "Run the predefined demo test fixture and return its observation.", call ->
                ActionResult.continueWith("Mock test runner observation: 12/12 predefined fixture tests pass. No arbitrary code was executed."));
    }

    public static GroundingAction respond() {
        return new FixedAction("respond", "Return the coding task outcome to the user and finish.", call ->
                ActionResult.finish(call.argument("text", "The mock coding workflow completed.")));
    }

    private interface Operation { ActionResult execute(ActionCall call); }

    private record FixedAction(String id, String description, Operation operation) implements GroundingAction {
        @Override public Set<String> requiredArguments() {
            return switch (id) {
                case "apply_patch" -> Set.of("change");
                case "respond" -> Set.of("text");
                default -> Set.of();
            };
        }
        @Override public ActionResult execute(ActionCall call, AgentContext context) { return operation.execute(call); }
    }
}
