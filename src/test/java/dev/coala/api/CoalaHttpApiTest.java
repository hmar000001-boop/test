package dev.coala.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "server.address=127.0.0.1",
        "coala.api.token=",
        "coala.api.rate-limit.requests-per-minute=30"
})
class CoalaHttpApiTest {
    @LocalServerPort int port;
    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void listsAllSixUseCasesOverHttp() throws Exception {
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(uri("/api/use-cases")).GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(200, response.statusCode(), response.body());
        assertTrue(response.body().contains("dialogue-direct"));
        assertTrue(response.body().contains("coding-iterative"));
        assertTrue(response.body().contains("puzzle-deliberative"));
        assertTrue(response.body().contains("research-deliberative"));
        assertTrue(response.body().contains("travel-plan-first"));
        assertTrue(response.body().contains("support-iterative"));
    }

    @Test
    void createsSessionAndRunsOfflineUseCaseOverHttp() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri("/api/use-cases/dialogue-direct/run"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"goal\":\"Explain CoALA\"}"))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(200, response.statusCode(), response.body());
        assertTrue(response.body().contains("sessionId"));
        assertTrue(response.body().contains("direct-action demo response"));
    }

    @Test
    void unknownSessionReturnsNotFoundAndCannotBeCreatedByCaller() throws Exception {
        String body = "{\"goal\":\"Explain CoALA\",\"sessionId\":\"caller-chosen-session\"}";
        HttpRequest request = HttpRequest.newBuilder(uri("/api/use-cases/dialogue-direct/run"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(404, response.statusCode(), response.body());
        assertTrue(response.body().contains("Session not found"));
    }

    @Test
    void invalidGoalReturnsBadRequest() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri("/api/use-cases/dialogue-direct/run"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"goal\":\" \"}"))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(400, response.statusCode(), response.body());
    }

    @Test
    void malformedJsonReturnsBadRequest() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri("/api/use-cases/dialogue-direct/run"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"goal\": "))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(400, response.statusCode(), response.body());
        assertTrue(response.body().contains("Invalid request body"));
    }

    @Test
    void unknownUseCaseReturnsBadRequest() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri("/api/use-cases/unknown/run"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"goal\":\"test\"}"))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(400, response.statusCode(), response.body());
    }

    @Test
    void researchUseCaseRunsOverHttp() throws Exception {
        HttpResponse<String> response = postRun("research-deliberative", "Prepare a research brief on retrieval-augmented generation");
        assertEquals(200, response.statusCode(), response.body());
        assertTrue(response.body().contains("synthetic demo results"));
    }

    @Test
    void travelUseCaseRunsOverHttp() throws Exception {
        HttpResponse<String> response = postRun("travel-plan-first", "Plan a 4-day trip to Paris next month within CAD 1400, check my calendar, and suggest flights and hotels. Do not book anything.");
        assertEquals(200, response.statusCode(), response.body());
        assertTrue(response.body().contains("Proposed trip to Paris"));
        assertTrue(response.body().contains("No booking"));
    }

    @Test
    void supportUseCaseRunsOverHttp() throws Exception {
        HttpResponse<String> response = postRun("support-iterative", "I cannot sign in after several attempts; my account is locked.");
        assertEquals(200, response.statusCode(), response.body());
        assertTrue(response.body().contains("SUP-DEMO-1042"));
    }

    private HttpResponse<String> postRun(String id, String goal) throws Exception {
        String escaped = goal.replace("\\", "\\\\").replace("\"", "\\\"");
        HttpRequest request = HttpRequest.newBuilder(uri("/api/use-cases/" + id + "/run"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"goal\":\"" + escaped + "\"}"))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private URI uri(String path) { return URI.create("http://127.0.0.1:" + port + path); }
}
