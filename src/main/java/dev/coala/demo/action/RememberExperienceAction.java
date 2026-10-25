package dev.coala.demo.action;

import dev.coala.core.ActionCall;
import dev.coala.core.ActionResult;
import dev.coala.core.AgentContext;
import dev.coala.core.LearningAction;
import dev.coala.core.DecisionProcedureException;
import dev.coala.core.memory.MemoryRecord;
import dev.coala.core.memory.MemoryType;

import java.util.Set;

/** Selected learning action: writes an experience to episodic long-term memory. */
public final class RememberExperienceAction implements LearningAction {
    @Override public String id() { return "remember_experience"; }
    @Override public String description() { return "Store the current task outcome as an episodic experience."; }
    @Override public Set<String> optionalArguments() { return Set.of("content"); }

    @Override
    public void validate(ActionCall call) {
        LearningAction.super.validate(call);
        if (call.arguments().containsKey("content") && call.argument("content", "").isBlank()) {
            throw new DecisionProcedureException("Action 'remember_experience' requires non-empty content when the optional field is supplied.");
        }
    }

    @Override
    public ActionResult execute(ActionCall call, AgentContext context) {
        String content = call.argument("content", "Completed goal: " + context.workingMemory().getString("goal", "(unknown goal)"));
        context.longTermMemory().store(MemoryRecord.of(MemoryType.EPISODIC, content));
        return ActionResult.continueWith("Stored one episodic memory record.");
    }
}
