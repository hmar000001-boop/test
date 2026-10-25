package dev.coala;

import dev.coala.demo.DemoUseCaseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CoalaApplicationContextTest {
    @Autowired DemoUseCaseService useCases;

    @Test
    void defaultProfileStartsAndListsAllSixUseCases() {
        assertEquals(6, useCases.list().size());
    }

    @Test
    void researchUseCaseRetrievesPlansSearchesLearnsAndDelivers() {
        var response = useCases.run("research-deliberative", "Prepare a research brief on retrieval-augmented generation", 8, null);
        assertEquals("deliberative-proposal-evaluation-selection", response.run().procedure());
        assertEquals("TERMINAL_ACTION", response.run().terminationReason().name());
        assertTrue(response.run().finalObservation().contains("synthetic demo results"));
        assertTrue(response.run().events().stream().anyMatch(event -> event.stage().name().equals("RETRIEVAL")));
        assertEquals(4, response.run().cyclesExecuted());
    }

    @Test
    void travelUseCaseExecutesPlanInOrderAndNeverBooks() {
        var response = useCases.run("travel-plan-first",
                "Plan a 4-day trip to Paris next month within CAD 1400, check my calendar, and suggest flights and hotels. Do not book anything.",
                8, null);
        assertEquals("plan-first-revisable", response.run().procedure());
        assertEquals("TERMINAL_ACTION", response.run().terminationReason().name());
        assertTrue(response.run().finalObservation().contains("Proposed trip to Paris"));
        assertTrue(response.run().finalObservation().contains("CAD 1190"));
        assertTrue(response.run().finalObservation().contains("No booking"));
        assertTrue(response.run().finalObservation().contains("Budget check: WITHIN budget"));
        var executedActions = response.run().events().stream()
                .filter(event -> event.stage().name().equals("EXECUTION") || event.stage().name().equals("LEARNING"))
                .map(event -> event.summary().split(" -> ", 2)[0]).toList();
        assertEquals(java.util.List.of("check_calendar", "search_flights", "search_hotels", "draft_itinerary", "learn_episode", "present_itinerary"), executedActions);
        assertEquals(6, response.run().cyclesExecuted());
    }

    @Test
    void supportUseCaseClassifiesThenEscalatesLockedAccountInScriptedMode() {
        var response = useCases.run("support-iterative",
                "I cannot sign in after several attempts; my account is locked.", 4, null);
        assertEquals("iterative-reason-act", response.run().procedure());
        assertEquals("TERMINAL_ACTION", response.run().terminationReason().name());
        assertTrue(response.run().finalObservation().contains("SUP-DEMO-1042"));
        var executedActions = response.run().events().stream()
                .filter(event -> event.stage().name().equals("EXECUTION"))
                .map(event -> event.summary().split(" -> ", 2)[0]).toList();
        assertEquals(java.util.List.of("classify_support_case", "escalate_case"), executedActions);
        assertEquals(2, response.run().cyclesExecuted());
    }

    @Test
    void supportUseCaseUsesSafeGuidanceForANonElevatedRequest() {
        var response = useCases.run("support-iterative", "How can I find my account settings?", 4, null);
        assertEquals("TERMINAL_ACTION", response.run().terminationReason().name());
        assertTrue(response.run().finalObservation().contains("official recovery path"));
        assertFalse(response.run().finalObservation().contains("SUP-DEMO-1042"));
        assertEquals(2, response.run().cyclesExecuted());
    }

    @Test
    void defaultProfileRunsOfflineAndReturnsReusableSessionId() {
        var response = useCases.run("dialogue-direct", "Explain CoALA", 2, null);
        assertNotNull(response.sessionId());
        assertFalse(response.sessionId().isBlank());
        assertTrue(response.run().finalObservation().contains("direct-action demo response"));
    }
}
