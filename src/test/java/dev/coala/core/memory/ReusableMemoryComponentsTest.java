package dev.coala.core.memory;

import dev.coala.core.AgentContext;
import dev.coala.core.WorkingMemory;
import dev.coala.core.PromptContextPolicy;
import dev.coala.core.WorkingMemoryPromptContextBuilder;
import dev.coala.core.learning.EpisodicMemoryLearningAction;
import dev.coala.core.learning.SemanticMemoryLearningAction;
import dev.coala.core.ActionCall;
import dev.coala.core.memory.retrieval.HybridMemoryRetriever;
import dev.coala.core.memory.retrieval.LexicalMemoryRetriever;
import dev.coala.core.memory.retrieval.MemoryRetriever;
import dev.coala.core.memory.retrieval.RecencyImportanceRelevanceRetriever;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ReusableMemoryComponentsTest {
    @TempDir Path tempDir;

    @Test void scopedMemoryEnforcesNamespaceAndMaximumRecordRetention() {
        InMemoryLongTermMemory delegate = new InMemoryLongTermMemory(100);
        MemoryNamespace a = new MemoryNamespace("agent", "s-a", "tenant", "travel");
        MemoryNamespace b = new MemoryNamespace("agent", "s-b", "tenant", "travel");
        var scopeA = new ScopedLongTermMemory(delegate, a, new MaxAgeMemoryRetentionPolicy(Duration.ofDays(3), 2));
        var scopeB = new ScopedLongTermMemory(delegate, b, MemoryRetentionPolicy.keepAll());
        scopeA.store(MemoryRecord.of(MemoryType.SEMANTIC, "one", a, Map.of(), .2));
        scopeA.store(MemoryRecord.of(MemoryType.SEMANTIC, "two", a, Map.of(), .3));
        scopeA.store(MemoryRecord.of(MemoryType.SEMANTIC, "three", a, Map.of(), .4));
        scopeB.store(MemoryRecord.of(MemoryType.SEMANTIC, "private to session b", b, Map.of(), .9));
        assertEquals(2, scopeA.list(a, 20).size(), "maxRecords applies to the full scoped set");
        assertEquals(List.of("private to session b"), scopeB.list(b, 20).stream().map(MemoryRecord::content).toList());
        assertTrue(scopeA.retrieve(new MemoryQuery("private", Set.of(MemoryType.SEMANTIC), 10, a, null, null)).isEmpty());
        assertFalse(scopeA.delete(scopeB.list(b, 2).getFirst().id(), b));
    }

    @Test void fileBackedMemoryRoundTripsNamespaceMetadataAndImportance() {
        Path file = tempDir.resolve("memory.db");
        MemoryNamespace namespace = new MemoryNamespace("research", "session-7", "tenant-x", "papers");
        var first = new PersistentLongTermMemory(new FileMemoryRecordRepository(file, 100), 100);
        MemoryRecord original = MemoryRecord.of(MemoryType.EPISODIC, "Read the primary paper carefully", namespace,
                Map.of("source", "paper-1", "status", "reviewed"), .85);
        first.store(original);
        var reopened = new PersistentLongTermMemory(new FileMemoryRecordRepository(file, 100), 100);
        MemoryRecord found = reopened.list(namespace, 10).getFirst();
        assertEquals(original.id(), found.id());
        assertEquals(namespace, found.namespace());
        assertEquals(original.metadata(), found.metadata());
        assertEquals(.85, found.importance());
        assertEquals(original.content(), found.content());
    }

    @Test void lexicalHybridAndWeightedRetrievalPreserveProvenanceAndDeduplicate() {
        MemoryNamespace namespace = new MemoryNamespace("agent", "s1", "tenant", "general");
        InMemoryLongTermMemory store = new InMemoryLongTermMemory(20);
        MemoryRecord first = MemoryRecord.of(MemoryType.SEMANTIC, "quartz crystal oscillator timing", namespace, Map.of("origin", "manual"), .8);
        MemoryRecord second = MemoryRecord.of(MemoryType.EPISODIC, "A quartz device failed during vibration testing", namespace, Map.of(), .4);
        store.store(first); store.store(second);
        MemoryQuery query = new MemoryQuery("quartz oscillator", Set.of(), 10, namespace, null, null);
        var lexical = new LexicalMemoryRetriever();
        var lexicalResults = lexical.retrieve(query, store);
        assertFalse(lexicalResults.isEmpty());
        assertTrue(lexicalResults.stream().allMatch(item -> !item.reason().isBlank()));
        MemoryRetriever denseStub = (q, m) -> List.of(new ScoredMemory(first, .95, "test vector similarity"));
        var hybrid = new HybridMemoryRetriever(lexical, denseStub, .6, .4);
        var results = hybrid.retrieve(query, store);
        assertEquals(results.size(), results.stream().map(item -> item.record().id()).distinct().count());
        assertTrue(results.stream().anyMatch(item -> item.record().id().equals(first.id())));
        var weighted = new RecencyImportanceRelevanceRetriever(.5, .2, .3, Duration.ofDays(30)).retrieve(query, store);
        assertFalse(weighted.isEmpty());
        assertTrue(weighted.stream().allMatch(item -> Double.isFinite(item.score())));
    }

    @Test void promptContextPolicyRedactsSensitiveKeysBoundsContentAndIncludesGroundingOutputs() {
        WorkingMemory wm = new WorkingMemory();
        wm.put("goal", "Find current library docs");
        wm.put("password", "do-not-send-this");
        wm.put("http.weather.responseJson", "{\"forecast\":\"clear\"}");
        wm.put("irrelevant.internal.debug", "not included by default");
        wm.put("latest_observation", "x".repeat(1_200));
        AgentContext context = new AgentContext(wm, new InMemoryLongTermMemory());
        var policyRequest = PromptContextPolicy.defaultPolicy().requestFor("NEXT_ACTION", context);
        var prompt = new WorkingMemoryPromptContextBuilder().build(wm, policyRequest);
        assertTrue(prompt.rendered().contains("goal = Find current library docs"));
        assertTrue(prompt.rendered().contains("http.weather.responseJson"));
        assertFalse(prompt.rendered().contains("do-not-send-this"));
        assertFalse(prompt.rendered().contains("irrelevant.internal.debug"));
        assertTrue(prompt.rendered().length() <= 12_000);
        var redacted = new WorkingMemoryPromptContextBuilder().build(wm,
                new WorkingMemoryPromptContextBuilder.Request(Set.of("goal"), Set.of(),
                        List.of(java.util.regex.Pattern.compile("(?i)library")), 512, true));
        assertTrue(redacted.rendered().contains("[REDACTED]"));
        assertEquals(1, redacted.redactionsApplied());
    }

    @Test void learningActionsStoreNamespaceAndEvidenceMetadata() {
        MemoryNamespace namespace = new MemoryNamespace("agent", "session", "tenant", "coding");
        InMemoryLongTermMemory memory = new InMemoryLongTermMemory(20);
        AgentContext context = new AgentContext(new WorkingMemory(), memory, namespace, "session", "alice");
        context.workingMemory().put("goal", "Fix the test failure");
        context.workingMemory().put("latest_observation", "One unit test failed");
        new EpisodicMemoryLearningAction().execute(new ActionCall("learn_episode", Map.of("success", "false", "source", "test-run")), context);
        new SemanticMemoryLearningAction().execute(new ActionCall("learn_semantic_fact", Map.of(
                "fact", "Module X requires Java 21", "status", "observed", "provenance", "pom.xml", "confidence", "0.95")), context);
        var episodes = memory.retrieve(new MemoryQuery("test failure", Set.of(MemoryType.EPISODIC), 10, namespace, null, null));
        var facts = memory.retrieve(new MemoryQuery("Java 21", Set.of(MemoryType.SEMANTIC), 10, namespace, null, null));
        assertEquals(1, episodes.size());
        assertEquals("false", episodes.getFirst().metadata().get("success"));
        assertEquals(1, facts.size());
        assertEquals("pom.xml", facts.getFirst().metadata().get("provenance"));
        assertEquals(.95, facts.getFirst().importance());
    }

    @Test void learningActionsRedactCredentialLikeValuesBeforePersisting() {
        MemoryNamespace namespace = new MemoryNamespace("agent", "learn-safe", "tenant", "test");
        InMemoryLongTermMemory memory = new InMemoryLongTermMemory(20);
        AgentContext context = new AgentContext(new WorkingMemory(), memory, namespace, "learn-safe", "alice");
        new EpisodicMemoryLearningAction().execute(new ActionCall("learn_episode", Map.of(
                "content", "Trip completed; api_key=sk-12345678901234567890; outcome=ok")), context);
        new SemanticMemoryLearningAction().execute(new ActionCall("learn_semantic_fact", Map.of(
                "fact", "Service endpoint key=secret-password-123 should never be logged", "provenance", "test fixture")), context);
        List<MemoryRecord> records = memory.list(namespace, 20);
        assertEquals(2, records.size());
        assertTrue(records.stream().noneMatch(record -> record.content().contains("sk-12345678901234567890")));
        assertTrue(records.stream().noneMatch(record -> record.content().contains("secret-password-123")));
    }

    @Test void maintenanceDeletesOnlyRecordsSelectedByExplicitPolicy() {
        MemoryNamespace namespace = new MemoryNamespace("agent", "s", "t", "d");
        InMemoryLongTermMemory memory = new InMemoryLongTermMemory(20);
        MemoryRecord old = new MemoryRecord(null, MemoryType.EPISODIC, "old episode", Instant.now().minus(Duration.ofDays(20)), namespace, Map.of(), .2);
        MemoryRecord keep = MemoryRecord.of(MemoryType.SEMANTIC, "keep this fact", namespace, Map.of(), .9);
        memory.store(old); memory.store(keep);
        int deleted = new MemoryMaintenanceService().deleteMatching(memory, namespace, 10,
                (record, now) -> record.createdAt().isBefore(now.minus(Duration.ofDays(5))));
        assertEquals(1, deleted);
        assertEquals(List.of("keep this fact"), memory.list(namespace, 10).stream().map(MemoryRecord::content).toList());
    }
}
