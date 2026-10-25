package dev.coala.core.planning.search;

public record SearchResult<S>(SearchNode<S> selectedNode, int nodesExpanded, int iterations, double score, String algorithmId) {
    public SearchResult {
        if (selectedNode == null) throw new IllegalArgumentException("selectedNode is required");
        if (nodesExpanded < 0 || iterations < 0 || !Double.isFinite(score)) throw new IllegalArgumentException("Invalid search result counters/score");
    }
}
