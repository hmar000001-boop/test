package dev.coala.core.learning;

import dev.coala.core.AgentContext;
import dev.coala.core.AgentRunResult;
import dev.coala.core.DecisionEvent;
import dev.coala.core.DecisionStage;
import dev.coala.core.TerminationReason;
import dev.coala.core.WorkingMemory;
import dev.coala.core.memory.InMemoryLongTermMemory;
import dev.coala.core.memory.MemoryRecord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DecisionEpisodeRecorderTest {
    @Test void episodeIsBoundedAndCredentialLikeContentIsRedacted() {
        AgentContext context = new AgentContext(new WorkingMemory(), new InMemoryLongTermMemory());
        String longText = "api_key=sk-12345678901234567890 " + "x".repeat(5_000);
        var result = new AgentRunResult("direct", longText, longText, 7,
                TerminationReason.MAX_CYCLES_REACHED,
                java.util.stream.IntStream.range(0, 12)
                        .mapToObj(i -> new DecisionEvent(i, DecisionStage.REASONING, longText)).toList());
        DecisionEpisodeRecorder recorder = new DecisionEpisodeRecorder();
        MemoryRecord record = recorder.record(result, context, false);
        assertTrue(record.content().length() <= MemoryRecord.MAX_CONTENT_CHARACTERS);
        assertFalse(record.content().contains("sk-12345678901234567890"));
        assertEquals("false", record.metadata().get("successful"));
        assertEquals(7, Integer.parseInt(record.metadata().get("cycles")));
        recorder.recordAndStore(result, context, false);
        assertEquals(1, context.longTermMemory().list(context.namespace(), 10).size());
    }
}
