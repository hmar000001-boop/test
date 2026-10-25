package dev.coala.core;

import dev.coala.core.action.ActionOutputParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ActionOutputParserTest {
    private final ActionOutputParser parser = new ActionOutputParser();

    @Test
    void candidateSetMustContainAtLeastTwoCandidates() {
        assertThrows(DecisionProcedureException.class, () -> parser.parseCandidates(
                "{\"candidates\":[{\"actionId\":\"a\",\"arguments\":{}}]}", 4));
    }

    @Test
    void duplicateCandidateCallsAreRejected() {
        String json = "{\"candidates\":["
                + "{\"actionId\":\"inspect\",\"arguments\":{}},"
                + "{\"actionId\":\"inspect\",\"arguments\":{}}]}";
        assertThrows(DecisionProcedureException.class, () -> parser.parseCandidates(json, 4));
    }

    @Test
    void argumentsObjectIsRequiredEvenWhenNoArgumentsAreNeeded() {
        assertThrows(DecisionProcedureException.class,
                () -> parser.parseAction("{\"actionId\":\"x\"}"));
        assertThrows(DecisionProcedureException.class,
                () -> parser.parseCandidates("{\"candidates\":[{\"actionId\":\"a\"},{\"actionId\":\"b\",\"arguments\":{}}]}", 4));
    }

    @Test
    void actionArgumentsMustBeStrings() {
        assertThrows(DecisionProcedureException.class, () -> parser.parseAction(
                "{\"actionId\":\"x\",\"arguments\":{\"count\":2}}"));
    }

    @Test
    void evaluationMustReturnDecisionAndOneScorePerCandidate() {
        assertThrows(DecisionProcedureException.class,
                () -> parser.parseEvaluation("{\"rejectAll\":false,\"scores\":[1]}", 2));
        assertThrows(DecisionProcedureException.class,
                () -> parser.parseEvaluation("{\"scores\":[1,2]}", 2));
    }
    @Test
    void trailingJsonValuesAreRejected() {
        assertThrows(DecisionProcedureException.class,
                () -> parser.parseAction("{\"actionId\":\"x\",\"arguments\":{}} {\"ignored\":true}"));
    }

    @Test
    void duplicateJsonKeysAreRejected() {
        assertThrows(DecisionProcedureException.class,
                () -> parser.parseAction("{\"actionId\":\"x\",\"actionId\":\"y\",\"arguments\":{}}"));
    }

    @Test
    void unexpectedSchemaFieldsAreRejected() {
        assertThrows(DecisionProcedureException.class,
                () -> parser.parseAction("{\"actionId\":\"x\",\"arguments\":{},\"debug\":true}"));
        assertThrows(DecisionProcedureException.class,
                () -> parser.parseEvaluation("{\"rejectAll\":false,\"scores\":[1,0],\"extra\":1}", 2));
    }

    @Test
    void contentAfterAJsonFenceIsRejected() {
        assertThrows(DecisionProcedureException.class,
                () -> parser.parseAction("```json\n{\"actionId\":\"x\",\"arguments\":{}}\n``` extra"));
    }

}
