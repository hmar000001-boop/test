package dev.coala.core.learning;

import dev.coala.core.ActionArgumentDefinition;
import dev.coala.core.ActionCall;
import dev.coala.core.ActionResult;
import dev.coala.core.AgentContext;
import dev.coala.core.LearningAction;
import dev.coala.core.DecisionProcedureException;
import dev.coala.core.memory.MemoryRecord;
import dev.coala.core.memory.MemoryType;
import dev.coala.core.memory.MemoryTextSanitizer;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Stores a selected reflection as qualified semantic knowledge; it is never invoked automatically. */
public final class ReflectionToSemanticMemoryLearningAction implements LearningAction {
    @Override public String id() { return "learn_reflection"; }
    @Override public String description() { return "Store an explicitly selected reflection as qualified semantic knowledge."; }
    @Override public Set<String> optionalArguments() { return Set.of("content", "provenance", "confidence"); }
    @Override public List<ActionArgumentDefinition> argumentDefinitions() {
        return List.of(ActionArgumentDefinition.optionalString("content", "Reflection text; defaults to reflection.latest in working memory"),
                ActionArgumentDefinition.optionalString("provenance", "Experience or evidence reference"),
                new ActionArgumentDefinition("confidence", ActionArgumentDefinition.ValueType.DECIMAL, "Non-calibrated confidence label in [0,1]", false, Set.of(), 10));
    }
    @Override public void validate(ActionCall call) {
        LearningAction.super.validate(call);
        try { double v = Double.parseDouble(call.argument("confidence", "0.5")); if (!Double.isFinite(v) || v < 0 || v > 1) throw new NumberFormatException(); }
        catch (NumberFormatException ex) { throw new DecisionProcedureException("confidence must be between 0 and 1"); }
    }
    @Override public ActionResult execute(ActionCall call, AgentContext context) {
        String content = call.argument("content", context.workingMemory().getString("reflection.latest", ""));
        if (content.isBlank()) throw new DecisionProcedureException("No reflection was supplied or available in working memory");
        content = MemoryTextSanitizer.sanitize(content, MemoryRecord.MAX_CONTENT_CHARACTERS);
        if (content.isBlank()) throw new DecisionProcedureException("Reflection is empty after sanitization");
        String confidence = call.argument("confidence", "0.5");
        context.longTermMemory().store(MemoryRecord.of(MemoryType.SEMANTIC, content, context.namespace(),
                Map.of("epistemicStatus", "inferred",
                        "provenance", MemoryTextSanitizer.sanitize(call.argument("provenance", "reflection"), MemoryRecord.MAX_METADATA_VALUE_CHARACTERS),
                        "confidenceLabel", confidence), Double.parseDouble(confidence)));
        return ActionResult.continueWith("Stored selected reflection as semantic memory.");
    }
}
