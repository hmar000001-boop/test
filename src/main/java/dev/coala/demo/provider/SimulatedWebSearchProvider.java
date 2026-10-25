package dev.coala.demo.provider;

import dev.coala.actions.search.WebSearchProvider;
import dev.coala.actions.search.WebSearchResult;

import java.net.URI;
import java.time.Instant;
import java.util.List;

/**
 * Deterministic, synthetic web-search provider for offline demonstrations.
 * It does not access the Internet. Replace this adapter with a real provider
 * implementation before treating returned snippets as evidence.
 */
public final class SimulatedWebSearchProvider implements WebSearchProvider {
    @Override
    public List<WebSearchResult> search(String query, int maxResults) {
        String safeQuery = query == null || query.isBlank() ? "unspecified topic" : query.trim();
        List<WebSearchResult> fixtures = List.of(
                new WebSearchResult(
                        "Simulated overview: " + shorten(safeQuery, 90),
                        "Synthetic fixture: begin by defining the question, the population or system in scope, and what would count as useful evidence.",
                        URI.create("https://example.org/coala-demo/overview"), "SIMULATED-DEMO", Instant.parse("2026-01-01T00:00:00Z")),
                new WebSearchResult(
                        "Simulated comparison guide",
                        "Synthetic fixture: compare alternatives against explicit criteria, retain the source for each claim, and separate observed facts from interpretation.",
                        URI.create("https://example.org/coala-demo/comparison"), "SIMULATED-DEMO", Instant.parse("2026-02-01T00:00:00Z")),
                new WebSearchResult(
                        "Simulated limitations note",
                        "Synthetic fixture: this result is not a live source. Verify important claims against original, current sources before relying on them.",
                        URI.create("https://example.org/coala-demo/limitations"), "SIMULATED-DEMO", Instant.parse("2026-03-01T00:00:00Z"))
        );
        return fixtures.subList(0, Math.min(Math.max(1, maxResults), fixtures.size()));
    }

    private static String shorten(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max - 3) + "...";
    }
}
