package dev.coala.actions.search;

import dev.coala.core.ActionArgumentDefinition;
import dev.coala.core.ActionCall;
import dev.coala.core.ActionResult;
import dev.coala.core.ActionRisk;
import dev.coala.core.AgentContext;
import dev.coala.core.GroundingAction;

import java.util.List;
import java.util.Set;

/** Optional search grounding adapter using a configured provider port. */
public final class WebSearchGroundingAction implements GroundingAction {
    private final WebSearchProvider provider;
    private final int maxResults;
    public WebSearchGroundingAction(WebSearchProvider provider, int maxResults) {
        this.provider = java.util.Objects.requireNonNull(provider);
        if (maxResults < 1 || maxResults > 20) throw new IllegalArgumentException("maxResults must be 1..20");
        this.maxResults = maxResults;
    }
    @Override public String id() { return "web_search"; }
    @Override public String description() { return "Search a configured web-search provider and return source-attributed snippets."; }
    @Override public Set<String> requiredArguments() { return Set.of("query"); }
    @Override public Set<String> optionalArguments() { return Set.of("maxResults"); }
    @Override public List<ActionArgumentDefinition> argumentDefinitions() {
        return List.of(ActionArgumentDefinition.requiredString("query", "Search terms"),
                new ActionArgumentDefinition("maxResults", ActionArgumentDefinition.ValueType.INTEGER, "Optional number of results up to configured limit", false, Set.of(), 3));
    }
    @Override public ActionRisk risk() { return ActionRisk.LOW; }
    @Override public boolean idempotent() { return true; }
    @Override public ActionResult execute(ActionCall call, AgentContext context) {
        int requested = Integer.parseInt(call.argument("maxResults", Integer.toString(maxResults)));
        if (requested < 1 || requested > maxResults) throw new IllegalArgumentException("maxResults must be 1.." + maxResults);
        List<WebSearchResult> results = provider.search(call.argument("query", ""), requested);
        if (results == null) results = List.of();
        List<java.util.Map<String, Object>> normalized = results.stream().filter(java.util.Objects::nonNull).limit(requested).map(item -> java.util.Map.<String,Object>of(
                "title", item.title(), "snippet", item.snippet(), "url", item.url().toString(), "source", item.source(),
                "publishedAt", item.publishedAt() == null ? "unknown" : item.publishedAt().toString())).toList();
        context.workingMemory().put("web_search.results", normalized);
        return ActionResult.continueWith("Web search returned " + normalized.size() + " source-attributed result(s); details are in working memory.");
    }
}
