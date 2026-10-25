package dev.coala.security;

import dev.coala.core.ActionCall;
import dev.coala.core.ActionRisk;
import dev.coala.core.AgentContext;
import dev.coala.core.CoalaAction;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/** In-memory approval requests/grants bound to principal, action, exact arguments and expiry. */
public final class InMemoryActionApprovalPolicy implements ActionApprovalPolicy {
    public record PendingApproval(String id, String actionId, String argumentHash, String principal, Instant expiresAt) { }
    private final Predicate<CoalaAction> requiresApproval;
    private final Duration ttl;
    private final Map<String, PendingApproval> requests = new ConcurrentHashMap<>();
    private final Map<GrantKey, Instant> grants = new ConcurrentHashMap<>();
    private record GrantKey(String principal, String actionId, String argumentHash) { }

    public InMemoryActionApprovalPolicy() { this(action -> action.risk().ordinal() >= ActionRisk.HIGH.ordinal(), Duration.ofMinutes(10)); }
    public InMemoryActionApprovalPolicy(Predicate<CoalaAction> requiresApproval, Duration ttl) {
        this.requiresApproval = java.util.Objects.requireNonNull(requiresApproval);
        if (ttl == null || ttl.isZero() || ttl.isNegative()) throw new IllegalArgumentException("approval TTL must be positive");
        this.ttl = ttl;
    }
    @Override public ApprovalDecision authorize(CoalaAction action, ActionCall call, AgentContext context) {
        if (!requiresApproval.test(action)) return ApprovalDecision.grant();
        String principal = context.principal();
        String hash = argumentHash(call);
        GrantKey grantKey = new GrantKey(principal, call.actionId(), hash);
        Instant now = Instant.now();
        Instant expiresAt = grants.get(grantKey);
        if (expiresAt != null) {
            if (now.isBefore(expiresAt)) { grants.remove(grantKey, expiresAt); return ApprovalDecision.grant(); }
            grants.remove(grantKey, expiresAt);
        }
        requests.values().removeIf(req -> !now.isBefore(req.expiresAt()));
        PendingApproval existing = requests.values().stream().filter(req -> req.actionId().equals(call.actionId())
                && req.argumentHash().equals(hash) && req.principal().equals(principal) && now.isBefore(req.expiresAt())).findFirst().orElse(null);
        if (existing != null) return ApprovalDecision.pending(existing.id());
        String id = UUID.randomUUID().toString();
        requests.put(id, new PendingApproval(id, call.actionId(), hash, principal, now.plus(ttl)));
        return ApprovalDecision.pending(id);
    }
    /** Called only by an authenticated application handler, not by the LLM. */
    public boolean approve(String requestId, String authenticatedPrincipal) {
        if (requestId == null || authenticatedPrincipal == null) return false;
        PendingApproval request = requests.get(requestId);
        if (request == null || !request.principal().equals(authenticatedPrincipal)
                || !Instant.now().isBefore(request.expiresAt())) return false;
        // An unauthorized principal must not be able to consume or cancel someone else's request.
        if (!requests.remove(requestId, request)) return false;
        grants.put(new GrantKey(request.principal(), request.actionId(), request.argumentHash()), request.expiresAt());
        return true;
    }
    public boolean reject(String requestId, String authenticatedPrincipal) {
        PendingApproval request = requests.get(requestId);
        if (request == null || authenticatedPrincipal == null || !request.principal().equals(authenticatedPrincipal)) return false;
        return requests.remove(requestId, request);
    }
    public PendingApproval pending(String requestId) { return requests.get(requestId); }
    public int pendingCount() { return requests.size(); }

    public static String argumentHash(ActionCall call) {
        String canonical = call.actionId() + "\n" + call.arguments().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=" + e.getValue()).collect(java.util.stream.Collectors.joining("\n"));
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception ex) { throw new IllegalStateException("SHA-256 unavailable", ex); }
    }
}
