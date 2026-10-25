package dev.coala.demo.action;

import dev.coala.core.ActionCall;
import dev.coala.core.ActionKind;
import dev.coala.core.ActionResult;
import dev.coala.core.AgentContext;
import dev.coala.core.GroundingAction;

import java.util.Set;

/** Mock digital puzzle environment used to make proposal/evaluation/selection visible. */
public final class PuzzleGroundingActions {
    private PuzzleGroundingActions() {}

    public static GroundingAction inspectClues() {
        return new PuzzleAction("inspect_clues", "Inspect clues in the mock text puzzle.", call ->
                ActionResult.continueWith("Puzzle observation: choose a color word. It is not red; the demo fixture's answer is blue."));
    }

    public static GroundingAction checkHypothesis() {
        return new PuzzleAction("check_hypothesis", "Check a candidate answer without ending the puzzle.", call -> {
            String answer = call.argument("answer", "");
            return ActionResult.continueWith("Hypothesis '" + answer + "' was " + (answer.equalsIgnoreCase("blue") ? "consistent with" : "inconsistent with") + " the mock puzzle fixture.");
        });
    }

    public static GroundingAction submitAnswer() {
        return new PuzzleAction("submit_answer", "Submit the final answer in the mock puzzle environment.", call -> {
            String answer = call.argument("answer", "");
            String result = answer.equalsIgnoreCase("blue")
                    ? "Puzzle completed: blue is correct."
                    : "Puzzle completed with an incorrect answer: " + answer;
            return ActionResult.finish(result);
        });
    }

    private interface Operation { ActionResult execute(ActionCall call); }
    private record PuzzleAction(String id, String description, Operation operation) implements GroundingAction {
        @Override public Set<String> requiredArguments() {
            return switch (id) {
                case "check_hypothesis", "submit_answer" -> Set.of("answer");
                default -> Set.of();
            };
        }
        @Override public ActionResult execute(ActionCall call, AgentContext context) { return operation.execute(call); }
    }
}
