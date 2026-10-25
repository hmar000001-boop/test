package dev.coala.demo.action;

import dev.coala.core.ActionCall;
import dev.coala.core.ActionResult;
import dev.coala.core.AgentContext;
import dev.coala.core.GroundingAction;

import java.util.Set;

/** Grounding action for the dialogue environment. */
public final class DialogueReplyAction implements GroundingAction {
    @Override public String id() { return "respond"; }
    @Override public String description() { return "Send a final text response in the dialogue environment."; }

    @Override public Set<String> requiredArguments() { return Set.of("text"); }

    @Override
    public ActionResult execute(ActionCall call, AgentContext context) {
        String text = call.argument("text", "The model selected the dialogue response action but supplied no text.");
        context.workingMemory().put("dialogue_response", text);
        return ActionResult.finish(text);
    }
}
