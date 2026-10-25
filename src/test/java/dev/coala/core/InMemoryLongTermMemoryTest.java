package dev.coala.core;

import dev.coala.core.memory.InMemoryLongTermMemory;
import dev.coala.core.memory.MemoryRecord;
import dev.coala.core.memory.MemoryType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.UUID;

class InMemoryLongTermMemoryTest {
    @Test
    void matchingUsesWholeTokensInsteadOfSubstringFalsePositives() {
        InMemoryLongTermMemory memory = new InMemoryLongTermMemory();
        MemoryRecord catapult = MemoryRecord.of(MemoryType.SEMANTIC, "The catapult is stored in the shed.");
        MemoryRecord cat = MemoryRecord.of(MemoryType.SEMANTIC, "The cat likes a warm window.");
        memory.store(catapult);
        memory.store(cat);

        assertEquals(java.util.List.of(cat), memory.retrieve(MemoryType.SEMANTIC, "cat", 10));
    }

    @Test
    void stopwordOnlyQueryReturnsNoArbitraryMemories() {
        InMemoryLongTermMemory memory = new InMemoryLongTermMemory();
        memory.store(MemoryRecord.of(MemoryType.SEMANTIC, "A stored semantic fact"));
        assertTrue(memory.retrieve(MemoryType.SEMANTIC, "the and for", 5).isEmpty());
    }

    @Test
    void retrievalRespectsMemoryTypeAndLimit() {
        InMemoryLongTermMemory memory = new InMemoryLongTermMemory();
        MemoryRecord first = MemoryRecord.of(MemoryType.EPISODIC, "Puzzle clue blue square");
        MemoryRecord second = MemoryRecord.of(MemoryType.EPISODIC, "Puzzle clue green circle");
        MemoryRecord semantic = MemoryRecord.of(MemoryType.SEMANTIC, "Puzzle means a problem or game");
        memory.store(first);
        memory.store(second);
        memory.store(semantic);

        assertEquals(1, memory.retrieve(MemoryType.EPISODIC, "puzzle clue", 1).size());
        assertTrue(memory.retrieve(MemoryType.EPISODIC, "puzzle clue", 1).getFirst().type() == MemoryType.EPISODIC);
        assertEquals(1, memory.retrieve(MemoryType.SEMANTIC, "puzzle", 5).size());
        assertTrue(memory.retrieve(MemoryType.EPISODIC, "puzzle", 0).isEmpty());
    }

    @Test
    void memoryCapacityEvictsOldestInsertedRecord() {
        InMemoryLongTermMemory memory = new InMemoryLongTermMemory(2);
        MemoryRecord first = MemoryRecord.of(MemoryType.EPISODIC, "first memorable entry");
        MemoryRecord second = MemoryRecord.of(MemoryType.EPISODIC, "second memorable entry");
        MemoryRecord third = MemoryRecord.of(MemoryType.EPISODIC, "third memorable entry");
        memory.store(first);
        memory.store(second);
        memory.store(third);
        assertEquals(2, memory.allRecords().size());
        assertFalse(memory.allRecords().contains(first));
        assertTrue(memory.allRecords().contains(second));
        assertTrue(memory.allRecords().contains(third));
    }

    @Test
    void memoryRecordContentIsBounded() {
        assertThrows(IllegalArgumentException.class, () ->
                MemoryRecord.of(MemoryType.EPISODIC, "x".repeat(MemoryRecord.MAX_CONTENT_CHARACTERS + 1)));
    }

    @Test
    void shortMeaningfulTokensCanBeRetrieved() {
        InMemoryLongTermMemory memory = new InMemoryLongTermMemory();
        MemoryRecord record = MemoryRecord.of(MemoryType.SEMANTIC, "AI and ML support agent reasoning");
        memory.store(record);
        assertEquals(java.util.List.of(record), memory.retrieve(MemoryType.SEMANTIC, "AI", 5));
    }

    @Test
    void blankQueryReturnsMostRecentRecordsWithinLimit() {
        InMemoryLongTermMemory memory = new InMemoryLongTermMemory();
        MemoryRecord older = new MemoryRecord(UUID.randomUUID(), MemoryType.EPISODIC, "older entry",
                Instant.parse("2020-01-01T00:00:00Z"));
        MemoryRecord newer = new MemoryRecord(UUID.randomUUID(), MemoryType.EPISODIC, "newer entry",
                Instant.parse("2021-01-01T00:00:00Z"));
        memory.store(older);
        memory.store(newer);

        assertEquals(java.util.List.of(newer), memory.retrieve(MemoryType.EPISODIC, " ", 1));
    }
}
