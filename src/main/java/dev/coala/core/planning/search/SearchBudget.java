package dev.coala.core.planning.search;

/** Hard limits for the materialized search tree. maxNodes includes the root node. */
public record SearchBudget(int maxNodes, int maxDepth) {
    public SearchBudget {
        if (maxNodes < 2 || maxNodes > 10_000 || maxDepth < 1 || maxDepth > 32)
            throw new IllegalArgumentException("Search budget out of range (maxNodes 2..10000, maxDepth 1..32)");
    }
    public static SearchBudget defaults() { return new SearchBudget(500, 8); }
}
