package dev.coala.actions.dialogue;

import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Bounded in-memory interaction store suitable for demos and single-process deployments. */
public final class InMemoryPendingInteractionStore implements PendingInteractionStore {
    private final Map<String, PendingInteraction> entries = new ConcurrentHashMap<>();
    private final int maxEntries;
    public InMemoryPendingInteractionStore(int maxEntries) { if (maxEntries < 1) throw new IllegalArgumentException("maxEntries must be positive"); this.maxEntries = maxEntries; }
    @Override public synchronized PendingInteraction create(String sessionId, String principal, String question, Instant expiresAt) {
        if (sessionId == null || sessionId.isBlank() || principal == null || principal.isBlank()) throw new IllegalArgumentException("session and principal required");
        if (question == null || question.isBlank() || question.length() > 4_000) throw new IllegalArgumentException("question must be 1..4000 chars");
        if (expiresAt == null || !expiresAt.isAfter(Instant.now())) throw new IllegalArgumentException("expiresAt must be in the future");
        cleanup();
        if (entries.size() >= maxEntries) throw new IllegalStateException("Pending-interaction capacity reached");
        PendingInteraction item = new PendingInteraction(UUID.randomUUID().toString(), sessionId, principal, question, null, Instant.now(), expiresAt);
        entries.put(item.id(), item); return item;
    }
    @Override public Optional<PendingInteraction> find(String id) { cleanup(); return Optional.ofNullable(entries.get(id)); }
    @Override public boolean submitAnswer(String id, String sessionId, String principal, String answer) {
        if (answer == null || answer.isBlank() || answer.length() > 4_000) throw new IllegalArgumentException("answer must be 1..4000 chars");
        final boolean[] changed = {false};
        entries.computeIfPresent(id, (key, value) -> {
            if (value.answer() == null && value.sessionId().equals(sessionId) && value.principal().equals(principal) && Instant.now().isBefore(value.expiresAt())) {
                changed[0] = true; return new PendingInteraction(value.id(), value.sessionId(), value.principal(), value.question(), answer, value.createdAt(), value.expiresAt());
            }
            return value;
        });
        return changed[0];
    }
    @Override public Optional<PendingInteraction> consumeAnswer(String sessionId, String principal) {
        cleanup();
        PendingInteraction candidate = entries.values().stream().filter(p -> p.sessionId().equals(sessionId) && p.principal().equals(principal) && p.answered())
                .max(Comparator.comparing(PendingInteraction::createdAt)).orElse(null);
        if (candidate == null || !entries.remove(candidate.id(), candidate)) return Optional.empty();
        return Optional.of(candidate);
    }
    private void cleanup() { Instant now = Instant.now(); entries.values().removeIf(p -> !now.isBefore(p.expiresAt())); }
}
