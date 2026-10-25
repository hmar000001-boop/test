package dev.coala.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WorkingMemoryTest {
    @Test
    void snapshotIsAnImmutableCopy() {
        WorkingMemory memory = new WorkingMemory();
        memory.put("goal", "first");
        Map<String, Object> snapshot = memory.snapshot();
        memory.put("goal", "second");
        assertEquals("first", snapshot.get("goal"));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.put("other", "value"));
    }

    @Test
    void actionObservationIsBounded() {
        assertThrows(IllegalArgumentException.class, () ->
                ActionResult.continueWith("x".repeat(ActionResult.MAX_OBSERVATION_CHARACTERS + 1)));
    }

    @Test
    void nullRemovesValue() {
        WorkingMemory memory = new WorkingMemory();
        memory.put("key", "value");
        memory.put("key", null);
        assertTrue(memory.get("key").isEmpty());
    }
    @Test
    void nestedValuesAreDefensivelyCopiedAndImmutable() {
        WorkingMemory memory = new WorkingMemory();
        List<String> sourceList = new ArrayList<>(List.of("before"));
        Map<String, Object> sourceMap = new LinkedHashMap<>();
        sourceMap.put("items", sourceList);
        memory.put("state", sourceMap);
        sourceList.add("after");

        Object stateValue = memory.get("state").orElseThrow();
        assertInstanceOf(Map.class, stateValue);
        Map<?, ?> state = (Map<?, ?>) stateValue;
        assertEquals(List.of("before"), state.get("items"));
        assertThrows(UnsupportedOperationException.class,
                () -> ((List<Object>) (List<?>) state.get("items")).add("mutation"));
        assertThrows(UnsupportedOperationException.class,
                () -> ((Map<Object, Object>) state).put("new", "value"));
    }

    @Test
    void unsupportedMutableObjectTypesAreRejected() {
        WorkingMemory memory = new WorkingMemory();
        assertThrows(IllegalArgumentException.class, () -> memory.put("mutable", new StringBuilder("unsafe")));
    }

}
