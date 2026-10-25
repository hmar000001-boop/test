package dev.coala.actions.dialogue;

import java.time.Instant;
import java.util.Optional;

/** Storage port for asynchronous human clarification; implementation should persist if process restarts must be tolerated. */
public interface PendingInteractionStore {
    PendingInteraction create(String sessionId, String principal, String question, Instant expiresAt);
    Optional<PendingInteraction> find(String interactionId);
    boolean submitAnswer(String interactionId, String sessionId, String principal, String answer);
    Optional<PendingInteraction> consumeAnswer(String sessionId, String principal);
    record PendingInteraction(String id, String sessionId, String principal, String question,
                              String answer, Instant createdAt, Instant expiresAt) {
        public boolean answered() { return answer != null; }
    }
}
