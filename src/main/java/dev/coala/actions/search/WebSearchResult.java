package dev.coala.actions.search;

import java.net.URI;
import java.time.Instant;

/** Normalized, source-attributed web-search result. */
public record WebSearchResult(String title, String snippet, URI url, String source, Instant publishedAt) {
    public WebSearchResult {
        title = text(title, 300); snippet = text(snippet, 2_000); source = text(source, 200);
        if (url == null || !url.isAbsolute() || url.getHost() == null || url.getUserInfo() != null
                || !(url.getScheme().equalsIgnoreCase("http") || url.getScheme().equalsIgnoreCase("https")))
            throw new IllegalArgumentException("A source HTTP(S) URL without user-info is required");
    }
    private static String text(String value, int max) { String s = value == null ? "" : value; return s.length() <= max ? s : s.substring(0, max - 3) + "..."; }
}
