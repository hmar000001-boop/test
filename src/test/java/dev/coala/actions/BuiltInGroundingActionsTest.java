package dev.coala.actions;

import com.sun.net.httpserver.HttpServer;
import dev.coala.actions.calculator.CalculatorGroundingAction;
import dev.coala.actions.http.JsonHttpGroundingAction;
import dev.coala.actions.workspace.ReadOnlyWorkspaceGroundingAction;
import dev.coala.actions.search.WebSearchResult;
import dev.coala.core.*;
import dev.coala.core.memory.InMemoryLongTermMemory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BuiltInGroundingActionsTest {
    @TempDir Path tempDir;

    @Test void calculatorEvaluatesArithmeticButNeverEvaluatesArbitraryCode() {
        CalculatorGroundingAction calculator = new CalculatorGroundingAction();
        AgentContext context = new AgentContext(new WorkingMemory(), new InMemoryLongTermMemory());
        var result = calculator.execute(new ActionCall("calculate", Map.of("expression", "(2 + 3) * 4 - 6 / 2")), context);
        assertTrue(result.observation().contains("17"));
        assertEquals("17", context.workingMemory().getString("calculator.lastResult", ""));
        assertThrows(IllegalArgumentException.class, () -> calculator.execute(new ActionCall("calculate", Map.of("expression", "System.exit(0)")), context));
        assertThrows(IllegalArgumentException.class, () -> calculator.execute(new ActionCall("calculate", Map.of("expression", "1 / 0")), context));
    }

    @Test void workspaceSearchIsReadOnlyAndConfinedToConfiguredRoot() throws Exception {
        Path root = Files.createDirectory(tempDir.resolve("workspace"));
        Files.writeString(root.resolve("Main.java"), "class Main { // uniqueMarker }\n", StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("outside.txt"), "uniqueMarker outside\n", StandardCharsets.UTF_8);
        ReadOnlyWorkspaceGroundingAction search = new ReadOnlyWorkspaceGroundingAction(root, 4, 20_000, 10);
        AgentContext context = new AgentContext(new WorkingMemory(), new InMemoryLongTermMemory());
        var result = search.execute(new ActionCall("search_workspace", Map.of("query", "uniqueMarker", "extension", ".java")), context);
        assertTrue(result.observation().contains("1 matching"));
        String results = context.workingMemory().get("workspace.searchResults").orElseThrow().toString();
        assertTrue(results.contains("Main.java:1"));
        assertFalse(results.contains("outside.txt"));
        assertEquals("class Main { // uniqueMarker }\n", Files.readString(root.resolve("Main.java")));
        assertThrows(IllegalArgumentException.class, () -> search.execute(new ActionCall("search_workspace", Map.of("query", "x", "extension", "../outside.txt")), context));
    }

    @Test void webSearchResultsRejectNonHttpSchemesAndCredentialUrls() {
        assertThrows(IllegalArgumentException.class, () -> new WebSearchResult(
                "bad", "snippet", URI.create("httpj://example.com"), "search", Instant.now()));
        assertThrows(IllegalArgumentException.class, () -> new WebSearchResult(
                "bad", "snippet", URI.create("https://user:password@example.com"), "search", Instant.now()));
        assertDoesNotThrow(() -> new WebSearchResult(
                "good", "snippet", URI.create("https://example.com/article"), "search", Instant.now()));
    }

    @Test void jsonHttpActionUsesFixedLoopbackEndpointAndBoundsResponse() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/weather", exchange -> {
            byte[] bytes = "{\"forecast\":\"clear\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start();
        try {
            URI endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/weather");
            var action = new JsonHttpGroundingAction("read_weather", "Read configured mock weather endpoint", endpoint,
                    JsonHttpGroundingAction.Method.GET, List.of(ActionArgumentDefinition.optionalString("city", "City")),
                    ActionRisk.LOW, true, Duration.ofSeconds(2), 4_000, Map::of);
            AgentContext context = new AgentContext(new WorkingMemory(), new InMemoryLongTermMemory());
            var result = action.execute(new ActionCall("read_weather", Map.of("city", "Quebec City")), context);
            assertTrue(result.observation().contains("succeeded"));
            assertTrue(context.workingMemory().getString("http.read_weather.responseJson", "").contains("clear"));
            assertEquals(200, context.workingMemory().get("http.read_weather.status").orElseThrow());
            assertThrows(IllegalArgumentException.class, () -> new JsonHttpGroundingAction("bad", "bad", URI.create("http://example.com"),
                    JsonHttpGroundingAction.Method.GET, List.of(), ActionRisk.LOW, true, Duration.ofSeconds(1), 1000, Map::of));
        } finally { server.stop(0); }
    }
}
