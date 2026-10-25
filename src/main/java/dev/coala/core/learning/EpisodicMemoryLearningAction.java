package dev.coala.core.learning;

import dev.coala.core.ActionArgumentDefinition;
import dev.coala.core.ActionCall;
import dev.coala.core.ActionResult;
import dev.coala.core.AgentContext;
import dev.coala.core.ActionRisk;
import dev.coala.core.LearningAction;
import dev.coala.core.DecisionProcedureException;
import dev.coala.core.memory.MemoryRecord;
import dev.coala.core.memory.MemoryType;
import dev.coala.core.memory.MemoryTextSanitizer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Explicit learning action for a bounded episodic experience record. */
public final class EpisodicMemoryLearningAction implements LearningAction {
    @Override public String id() { return "learn_episode"; }
    @Override public String description() { return "Store an experience or run outcome in episodic long-term memory."; }
    @Override public Set<String> optionalArguments() { return Set.of("content", "success", "importance", "source"); }
    @Override public ActionRisk risk() { return ActionRisk.LOW; }
    @Override public List<ActionArgumentDefinition> argumentDefinitions() {
        return List.of(ActionArgumentDefinition.optionalString("content", "Explicit episode content; defaults to a summary of the current goal and latest action/observation."),
                new ActionArgumentDefinition("success", ActionArgumentDefinition.ValueType.ENUM, "Outcome label", false, Set.of("true", "false", "unknown"), 10),
                new ActionArgumentDefinition("importance", ActionArgumentDefinition.ValueType.DECIMAL, "Importance from 0 to 1", false, Set.of(), 10),
                ActionArgumentDefinition.optionalString("source", "Optional provenance label"));
    }
    @Override public void validate(ActionCall call) {
        LearningAction.super.validate(call);
        String value = call.argument("importance", "0.5");
        try { double n = Double.parseDouble(value); if (n < 0 || n > 1 || !Double.isFinite(n)) throw new NumberFormatException(); }
        catch (NumberFormatException ex) { throw new DecisionProcedureException("importance must be a number between 0 and 1"); }
    }
    @Override public ActionResult execute(ActionCall call, AgentContext context) {
        String content = call.argument("content", "");
        if (content.isBlank()) {
            content = "Goal: " + context.workingMemory().getString("goal", "(unknown)")
                    + " | Latest action: " + context.workingMemory().getString("latest_action", "(none)")
                    + " | Outcome: " + context.workingMemory().getString("latest_observation", "(no observation)");
        }
        content = MemoryTextSanitizer.sanitize(content, MemoryRecord.MAX_CONTENT_CHARACTERS);
        if (content.isBlank()) throw new DecisionProcedureException("Episode content is empty after sanitization");
        double importance = Double.parseDouble(call.argument("importance", "0.5"));
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("success", MemoryTextSanitizer.sanitize(call.argument("success", "unknown"), MemoryRecord.MAX_METADATA_VALUE_CHARACTERS));
        metadata.put("source", MemoryTextSanitizer.sanitize(call.argument("source", "decision-cycle"), MemoryRecord.MAX_METADATA_VALUE_CHARACTERS));
        context.longTermMemory().store(MemoryRecord.of(MemoryType.EPISODIC, content, context.namespace(), metadata, importance));
        return ActionResult.continueWith("Stored one episodic experience in long-term memory.");
    }
}
