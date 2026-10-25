package dev.coala.core;

import dev.coala.demo.SessionMemoryStore;
import dev.coala.core.memory.MemoryRecord;
import dev.coala.core.memory.MemoryType;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class SessionMemoryStoreTest {
    @Test
    void recordLimitMustRetainSeededDemoMemories() {
        assertThrows(IllegalArgumentException.class,
                () -> new SessionMemoryStore(10, Duration.ofMinutes(1), 2));
        var store = new SessionMemoryStore(10, Duration.ofMinutes(1), 3);
        var session = store.createSession();
        assertEquals(3, session.longTermMemory().allRecords().size());
    }

    @Test
    void sameSessionRetainsLearnedLongTermMemoryAcrossRequests() {
        SessionMemoryStore store = new SessionMemoryStore(10, Duration.ofMinutes(1));
        var created = store.createSession();
        var firstRequest = store.acquire(created.sessionId());
        firstRequest.longTermMemory().store(MemoryRecord.of(MemoryType.EPISODIC, "unique learned clue"));

        var secondRequest = store.acquire(created.sessionId());
        assertTrue(secondRequest.longTermMemory().retrieve(MemoryType.EPISODIC, "unique learned clue", 5)
                .stream().anyMatch(record -> record.content().equals("unique learned clue")));
    }

    @Test
    void differentSessionsDoNotShareLearnedMemories() {
        SessionMemoryStore store = new SessionMemoryStore(10, Duration.ofMinutes(1));
        var first = store.createSession();
        var second = store.createSession();
        first.longTermMemory().store(MemoryRecord.of(MemoryType.EPISODIC, "private session-a note"));
        var other = store.acquire(second.sessionId());
        assertTrue(other.longTermMemory().allRecords().stream()
                .noneMatch(record -> record.content().equals("private session-a note")),
                "A learned record from one session must not be stored in another session's memory");
    }

    @Test
    void missingSessionIdGeneratesReusableOpaqueId() {
        SessionMemoryStore store = new SessionMemoryStore(10, Duration.ofMinutes(1));
        var session = store.acquire(null);
        assertFalse(session.sessionId().isBlank());
        assertTrue(session.sessionId().matches("[a-f0-9-]{36}"));
        assertEquals(session.sessionId(), store.acquire(session.sessionId()).sessionId());
    }

    @Test
    void callerCannotCreateOrReserveAnArbitrarySessionId() {
        SessionMemoryStore store = new SessionMemoryStore(10, Duration.ofMinutes(1));
        assertThrows(java.util.NoSuchElementException.class, () -> store.acquire("chosen-session"));
        assertEquals(0, store.activeSessionCount());
    }

    @Test
    void invalidSessionIdIsRejected() {
        SessionMemoryStore store = new SessionMemoryStore(10, Duration.ofMinutes(1));
        assertThrows(IllegalArgumentException.class, () -> store.acquire("contains spaces"));
    }
    @Test
    void capacityEvictsLeastRecentlyUsedSessionAndOldIdCannotRecreateIt() {
        SessionMemoryStore store = new SessionMemoryStore(1, Duration.ofMinutes(1));
        var first = store.createSession();
        var second = store.createSession();
        assertEquals(1, store.activeSessionCount());
        assertThrows(java.util.NoSuchElementException.class, () -> store.acquire(first.sessionId()));
        assertEquals(second.sessionId(), store.acquire(second.sessionId()).sessionId());
    }

}
