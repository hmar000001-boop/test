package dev.coala.core.learning;

import dev.coala.core.ActionArgumentDefinition;
import dev.coala.core.ActionCall;
import dev.coala.core.ActionResult;
import dev.coala.core.AgentContext;
import dev.coala.core.ActionRisk;
import dev.coala.core.DecisionProcedureException;
import dev.coala.core.LearningAction;
import dev.coala.core.memory.MemoryRecord;
import dev.coala.core.memory.MemoryType;
import dev.coala.core.memory.MemoryTextSanitizer;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Explicit learning action for a validated declarative knowledge item. */
public class SemanticMemoryLearningAction implements LearningAction {
    @Override public String id() { return "learn_semantic_fact"; }
    @Override public String description() { return "Store a declarative fact or qualified inference in semantic long-term memory."; }
    @Override public Set<String> requiredArguments() { return Set.of("fact"); }
    @Override public Set<String> optionalArguments() { return Set.of("status", "provenance", "confidence", "limitations"); }
    @Override public ActionRisk risk() { return ActionRisk.LOW; }
    @Override public List<ActionArgumentDefinition> argumentDefinitions() {
        return List.of(ActionArgumentDefinition.requiredString("fact", "Declarative statement to retain"),
                new ActionArgumentDefinition("status", ActionArgumentDefinition.ValueType.ENUM, "Whether observed or inferred", false, Set.of("observed", "inferred", "uncertain"), 16),
                ActionArgumentDefinition.optionalString("provenance", "Evidence or source reference"),
                new ActionArgumentDefinition("confidence", ActionArgumentDefinition.ValueType.DECIMAL, "Non-calibrated confidence label in [0,1]", false, Set.of(), 10),
                ActionArgumentDefinition.optionalString("limitations", "Known caveats or conditions"));
    }
    @Override public void validate(ActionCall call) {
        LearningAction.super.validate(call);
        try {
            double n = Double.parseDouble(call.argument("confidence", "0.5"));
            if (!Double.isFinite(n) || n < 0 || n > 1) throw new NumberFormatException();
        } catch (NumberFormatException ex) { throw new DecisionProcedureException("confidence must be a number between 0 and 1"); }
    }
    @Override public ActionResult execute(ActionCall call, AgentContext context) {
        String fact = MemoryTextSanitizer.sanitize(call.argument("fact", ""), MemoryRecord.MAX_CONTENT_CHARACTERS);
        if (fact.isBlank()) throw new DecisionProcedureException("Fact is empty after sanitization");
        Map<String, String> metadata = Map.of(
                "epistemicStatus", MemoryTextSanitizer.sanitize(call.argument("status", "uncertain"), MemoryRecord.MAX_METADATA_VALUE_CHARACTERS),
                "provenance", MemoryTextSanitizer.sanitize(call.argument("provenance", "unspecified"), MemoryRecord.MAX_METADATA_VALUE_CHARACTERS),
                "confidenceLabel", MemoryTextSanitizer.sanitize(call.argument("confidence", "0.5"), MemoryRecord.MAX_METADATA_VALUE_CHARACTERS),
                "limitations", MemoryTextSanitizer.sanitize(call.argument("limitations", ""), MemoryRecord.MAX_METADATA_VALUE_CHARACTERS));
        context.longTermMemory().store(MemoryRecord.of(MemoryType.SEMANTIC, fact, context.namespace(), metadata,
                Double.parseDouble(call.argument("confidence", "0.5"))));
        return ActionResult.continueWith("Stored one semantic knowledge item with provenance metadata.");
    }
}
