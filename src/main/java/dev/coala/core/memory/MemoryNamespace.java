package dev.coala.core.memory;

/** Logical scope for long-term memories; adapters must enforce scope at storage/retrieval boundaries. */
public record MemoryNamespace(String agentId, String sessionId, String tenantId, String domain) {
    public static final MemoryNamespace DEFAULT = new MemoryNamespace("default", "default", "default", "default");
    public MemoryNamespace {
        agentId = normalize(agentId, "default");
        sessionId = normalize(sessionId, "default");
        tenantId = normalize(tenantId, "default");
        domain = normalize(domain, "default");
    }
    private static String normalize(String value, String fallback) {
        String text = value == null || value.isBlank() ? fallback : value.trim();
        if (text.length() > 200) throw new IllegalArgumentException("Namespace value is too long");
        return text;
    }
    public boolean sameAgentSession(MemoryNamespace other) {
        return other != null && agentId.equals(other.agentId) && sessionId.equals(other.sessionId)
                && tenantId.equals(other.tenantId) && domain.equals(other.domain);
    }
}
