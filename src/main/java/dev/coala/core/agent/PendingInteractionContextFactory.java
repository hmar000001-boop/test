package dev.coala.core.agent;

import dev.coala.actions.dialogue.PendingInteractionStore;
import dev.coala.core.AgentContext;

/** Decorates a context factory to deliver a submitted clarification answer into new working memory on resume. */
public final class PendingInteractionContextFactory implements AgentContextFactory {
    private final AgentContextFactory delegate;
    private final PendingInteractionStore store;
    public PendingInteractionContextFactory(AgentContextFactory delegate, PendingInteractionStore store) {
        this.delegate = java.util.Objects.requireNonNull(delegate); this.store = java.util.Objects.requireNonNull(store);
    }
    @Override public AgentContext create(AgentRunRequest request) {
        AgentContext context = delegate.create(request);
        if (!request.sessionId().isBlank()) store.consumeAnswer(request.sessionId(), request.principal()).ifPresent(answer -> {
            context.workingMemory().put("interaction.answer", answer.answer());
            context.workingMemory().put("interaction.answeredQuestion", answer.question());
            context.workingMemory().put("interaction.answerReceivedAt", answer.createdAt().toString());
        });
        return context;
    }
}
