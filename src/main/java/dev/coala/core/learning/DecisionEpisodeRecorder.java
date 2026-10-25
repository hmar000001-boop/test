package dev.coala.core.learning;

import dev.coala.core.AgentContext;
import dev.coala.core.AgentRunResult;
import dev.coala.core.DecisionEvent;
import dev.coala.core.memory.MemoryRecord;
import dev.coala.core.memory.MemoryType;

import java.util.LinkedHashMap;
import java.util.Map;

/** Converts a run result into a compact episodic memory without persisting full prompts or raw model output. */
public final class DecisionEpisodeRecorder implements dev.coala.core.agent.AgentRunCompletionListener {
    public MemoryRecord record(AgentRunResult result, AgentContext context, boolean successful) {
        if (result == null || context == null) throw new IllegalArgumentException("result and context are required");
        StringBuilder content = new StringBuilder("Goal: ").append(safe(result.goal(), 700))
                .append(" | Procedure: ").append(safe(result.procedure(), 100))
                .append(" | Termination: ").append(result.terminationReason())
                .append(" | Cycles: ").append(result.cyclesExecuted())
                .append(" | Final observation: ").append(safe(result.finalObservation(), 700));
        int included = 0;
        for (DecisionEvent event : result.events()) {
            if (included++ >= 6) break;
            content.append(" | ").append(event.stage()).append(": ").append(safe(event.summary(), 160));
            if (content.length() >= MemoryRecord.MAX_CONTENT_CHARACTERS) break;
        }
        // Memory records have a hard size contract. Never let an oversized episode silently fail to persist.
        if (content.length() > MemoryRecord.MAX_CONTENT_CHARACTERS) {
            content.setLength(MemoryRecord.MAX_CONTENT_CHARACTERS - 3);
            content.append("...");
        }
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("procedure", safe(result.procedure(), 100));
        metadata.put("terminationReason", result.terminationReason().name());
        metadata.put("successful", Boolean.toString(successful));
        metadata.put("cycles", Integer.toString(result.cyclesExecuted()));
        return MemoryRecord.of(MemoryType.EPISODIC, content.toString(), context.namespace(), metadata, successful ? 0.7 : 0.4);
    }
    public void recordAndStore(AgentRunResult result, AgentContext context, boolean successful) {
        context.longTermMemory().store(record(result, context, successful));
    }
    @Override public void onCompleted(AgentRunResult result, AgentContext context) {
        boolean success = result.terminationReason() == dev.coala.core.TerminationReason.TERMINAL_ACTION;
        recordAndStore(result, context, success);
    }
    private static String safe(String value, int max) {
        return dev.coala.core.memory.MemoryTextSanitizer.sanitize(value, max);
    }
}
