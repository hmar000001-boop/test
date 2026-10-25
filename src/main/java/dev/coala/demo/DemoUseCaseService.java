package dev.coala.demo;

import dev.coala.actions.search.WebSearchGroundingAction;
import dev.coala.core.CoalaAction;
import dev.coala.core.AgentRunResult;
import dev.coala.core.agent.AgentContextFactory;
import dev.coala.core.agent.AgentRunRequest;
import dev.coala.core.agent.CoalaAgent;
import dev.coala.core.agent.CoalaAgentDefinition;
import dev.coala.core.action.ActionOutputParser;
import dev.coala.core.action.ReasoningAction;
import dev.coala.core.learning.EpisodicMemoryLearningAction;
import dev.coala.core.memory.MemoryNamespace;
import dev.coala.core.memory.MemoryRecord;
import dev.coala.core.memory.MemoryType;
import dev.coala.core.procedure.CoalaDecisionProcedure;
import dev.coala.core.procedure.DeliberativeDecisionProcedure;
import dev.coala.core.procedure.DirectActionDecisionProcedure;
import dev.coala.core.procedure.IterativeReasonActDecisionProcedure;
import dev.coala.core.procedure.PlanFirstDecisionProcedure;
import dev.coala.demo.action.CodingGroundingActions;
import dev.coala.demo.action.DialogueReplyAction;
import dev.coala.demo.action.PuzzleGroundingActions;
import dev.coala.demo.action.ResearchGroundingActions;
import dev.coala.demo.action.SupportTriageActions;
import dev.coala.demo.action.TravelGroundingActions;
import dev.coala.demo.provider.SimulatedWebSearchProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/** Wires demonstration-specific actions to reusable framework procedures. */
@Service
public class DemoUseCaseService {
    private final DirectActionDecisionProcedure direct;
    private final IterativeReasonActDecisionProcedure iterative;
    private final DeliberativeDecisionProcedure deliberative;
    private final PlanFirstDecisionProcedure planFirst;
    private final SessionMemoryStore sessionMemoryStore;
    private final int defaultMaxCycles;

    public DemoUseCaseService(DirectActionDecisionProcedure direct,
                              IterativeReasonActDecisionProcedure iterative,
                              DeliberativeDecisionProcedure deliberative,
                              ReasoningAction reasoning,
                              ActionOutputParser parser,
                              SessionMemoryStore sessionMemoryStore,
                              @Value("${coala.run.max-cycles:8}") int defaultMaxCycles) {
        this.direct = direct;
        this.iterative = iterative;
        this.deliberative = deliberative;
        this.planFirst = new PlanFirstDecisionProcedure(reasoning, parser, 8);
        this.sessionMemoryStore = sessionMemoryStore;
        if (defaultMaxCycles < 1 || defaultMaxCycles > 100) {
            throw new IllegalArgumentException("coala.run.max-cycles must be between 1 and 100");
        }
        this.defaultMaxCycles = defaultMaxCycles;
    }

    public List<DemoUseCaseDescriptor> list() {
        return List.of(
                new DemoUseCaseDescriptor("dialogue-direct", "Dialogue answerer", direct.id(),
                        "One directly generated action; illustrates direct action generation without a separate evaluation step."),
                new DemoUseCaseDescriptor("coding-iterative", "Coding agent (mock environment)", iterative.id(),
                        "Repeated reasoning, one grounding/learning action, observation, and next cycle."),
                new DemoUseCaseDescriptor("puzzle-deliberative", "Puzzle agent (mock digital environment)", deliberative.id(),
                        "Retrieval, multiple candidate proposals, evaluation, selection, and bounded re-proposal when rejected."),
                new DemoUseCaseDescriptor("research-deliberative", "Research brief (simulated web results)", deliberative.id(),
                        "Memory retrieval, competing next actions, source-attributed mock search, evidence formatting, episodic learning, and final delivery."),
                new DemoUseCaseDescriptor("travel-plan-first", "Travel planning (mock providers)", planFirst.id(),
                        "Prepare a multi-step plan, check the calendar first, inspect mock flight/hotel inventory, draft an itinerary, learn, and present without booking."),
                new DemoUseCaseDescriptor("support-iterative", "Support triage (mock environment)", iterative.id(),
                        "Repeated reason-act-observe cycles that classify a support request and select safe guidance or a simulated escalation."));
    }

    public UseCaseRunResponse run(String useCaseId, String goal, Integer maxCycles, String requestedSessionId) {
        if (!List.of("dialogue-direct", "coding-iterative", "puzzle-deliberative",
                "research-deliberative", "travel-plan-first", "support-iterative").contains(useCaseId)) {
            throw new IllegalArgumentException("Unknown use case id: " + useCaseId);
        }
        int boundedCycles = maxCycles == null ? defaultMaxCycles : maxCycles;
        if (boundedCycles < 1 || boundedCycles > 100) throw new IllegalArgumentException("maxCycles must be between 1 and 100");
        SessionMemoryStore.Session session = sessionMemoryStore.acquire(requestedSessionId);
        AgentRunResult result = switch (useCaseId) {
            case "dialogue-direct" -> runAgent(useCaseId, direct,
                    List.of(new DialogueReplyAction()), goal, boundedCycles, session);
            case "coding-iterative" -> runCoding(goal, boundedCycles, session);
            case "puzzle-deliberative" -> runPuzzle(goal, boundedCycles, session);
            case "research-deliberative" -> runResearch(goal, boundedCycles, session);
            case "travel-plan-first" -> runTravel(goal, boundedCycles, session);
            case "support-iterative" -> runSupport(goal, boundedCycles, session);
            default -> throw new IllegalArgumentException("Unknown use case id: " + useCaseId);
        };
        return new UseCaseRunResponse(session.sessionId(), result);
    }

    private AgentRunResult runCoding(String goal, int maxCycles, SessionMemoryStore.Session session) {
        return runAgent("coding-iterative", iterative, List.of(
                CodingGroundingActions.inspectProject(),
                CodingGroundingActions.applyPatch(),
                CodingGroundingActions.runTests(),
                new EpisodicMemoryLearningAction(),
                CodingGroundingActions.respond()), goal, maxCycles, session);
    }

    private AgentRunResult runPuzzle(String goal, int maxCycles, SessionMemoryStore.Session session) {
        return runAgent("puzzle-deliberative", deliberative, List.of(
                PuzzleGroundingActions.inspectClues(),
                PuzzleGroundingActions.checkHypothesis(),
                PuzzleGroundingActions.submitAnswer()), goal, maxCycles, session);
    }

    private AgentRunResult runResearch(String goal, int maxCycles, SessionMemoryStore.Session session) {
        MemoryNamespace namespace = namespace("research-deliberative", session.sessionId(), "research");
        ensureSeed(session, namespace, "research-policy",
                "Research briefs should preserve source attribution, distinguish evidence from interpretation, and explicitly label uncertainty and simulated sources.");
        return runAgent("research-deliberative", deliberative, List.of(
                new WebSearchGroundingAction(new SimulatedWebSearchProvider(), 5),
                ResearchGroundingActions.formatEvidenceBrief(),
                new EpisodicMemoryLearningAction(),
                ResearchGroundingActions.deliverResearchBrief()), goal, maxCycles, session, namespace);
    }

    private AgentRunResult runTravel(String goal, int maxCycles, SessionMemoryStore.Session session) {
        MemoryNamespace namespace = namespace("travel-plan-first", session.sessionId(), "travel");
        return runAgent("travel-plan-first", planFirst, List.of(
                TravelGroundingActions.checkCalendar(),
                TravelGroundingActions.searchFlights(),
                TravelGroundingActions.searchHotels(),
                TravelGroundingActions.draftItinerary(),
                new EpisodicMemoryLearningAction(),
                TravelGroundingActions.presentItinerary()), goal, maxCycles, session, namespace);
    }

    private AgentRunResult runSupport(String goal, int maxCycles, SessionMemoryStore.Session session) {
        MemoryNamespace namespace = namespace("support-iterative", session.sessionId(), "support");
        return runAgent("support-iterative", iterative, List.of(
                SupportTriageActions.classifyCase(),
                SupportTriageActions.suggestSafeSteps(),
                SupportTriageActions.escalateCase()), goal, maxCycles, session, namespace);
    }

    private AgentRunResult runAgent(String agentId, CoalaDecisionProcedure procedure,
                                    List<? extends CoalaAction> actions, String goal, int maxCycles,
                                    SessionMemoryStore.Session session) {
        return runAgent(agentId, procedure, actions, goal, maxCycles, session, MemoryNamespace.DEFAULT);
    }

    private AgentRunResult runAgent(String agentId, CoalaDecisionProcedure procedure,
                                    List<? extends CoalaAction> actions, String goal, int maxCycles,
                                    SessionMemoryStore.Session session, MemoryNamespace namespace) {
        AgentContextFactory contextFactory = AgentContextFactory.scoped(request -> session.longTermMemory());
        CoalaAgentDefinition definition = CoalaAgentDefinition.of(agentId, procedure, actions, contextFactory, maxCycles);
        CoalaAgent agent = new CoalaAgent(definition);
        return agent.run(new AgentRunRequest(goal, session.sessionId(), "demo", namespace));
    }

    private static MemoryNamespace namespace(String agentId, String sessionId, String domain) {
        return new MemoryNamespace(agentId, sessionId, "demo", domain);
    }

    private static void ensureSeed(SessionMemoryStore.Session session, MemoryNamespace namespace,
                                   String seedId, String content) {
        boolean exists = session.longTermMemory().list(namespace, 1_000).stream()
                .anyMatch(record -> seedId.equals(record.metadata().get("demoSeed")));
        if (!exists) {
            session.longTermMemory().store(MemoryRecord.of(MemoryType.SEMANTIC, content, namespace,
                    Map.of("demoSeed", seedId, "source", "demo-fixture"), 0.85));
        }
    }
}
