package dev.coala.actions.search;

import java.util.List;

/** Provider port for a web-search integration. Vendor and authentication are intentionally unspecified. */
@FunctionalInterface
public interface WebSearchProvider {
    List<WebSearchResult> search(String query, int maxResults);
}
