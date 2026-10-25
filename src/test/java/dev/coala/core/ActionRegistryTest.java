package dev.coala.core;

import dev.coala.demo.action.DialogueReplyAction;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ActionRegistryTest {
    private final ActionRegistry registry = new ActionRegistry(List.of(new DialogueReplyAction()));

    @Test
    void requiredArgumentsAreEnforcedBeforeExecution() {
        assertThrows(DecisionProcedureException.class,
                () -> registry.validate(new ActionCall("respond", Map.of())));
    }

    @Test
    void undeclaredArgumentsAreRejected() {
        assertThrows(DecisionProcedureException.class,
                () -> registry.validate(new ActionCall("respond", Map.of("text", "hello", "unexpected", "x"))));
    }

    @Test
    void aggregateArgumentPayloadIsBounded() {
        Map<String, String> args = new java.util.LinkedHashMap<>();
        for (int i = 0; i < 9; i++) args.put("key" + i, "x".repeat(3_600));
        assertThrows(IllegalArgumentException.class, () -> new ActionCall("respond", args));
    }

    @Test
    void unknownActionIdsAreRejected() {
        assertThrows(DecisionProcedureException.class,
                () -> registry.validate(new ActionCall("not-registered", Map.of())));
    }
}
