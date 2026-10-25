package dev.coala.config;

import dev.coala.core.action.ActionOutputParser;
import dev.coala.core.action.LlmReasoningAction;
import dev.coala.core.action.MemoryRetrievalAction;
import dev.coala.core.action.RetrievalAction;
import dev.coala.core.action.ReasoningAction;
import dev.coala.core.procedure.DeliberativeDecisionProcedure;
import dev.coala.core.procedure.DirectActionDecisionProcedure;
import dev.coala.core.procedure.IterativeReasonActDecisionProcedure;
import dev.coala.demo.SessionMemoryStore;
import java.time.Duration;
import dev.coala.llm.LanguageModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CoalaCoreConfiguration {
    @Bean
    ActionOutputParser actionOutputParser() { return new ActionOutputParser(); }

    @Bean
    ReasoningAction reasoningAction(LanguageModel model,
            @Value("${coala.run.max-reasoning-calls:50}") int maxReasoningCalls) {
        return new LlmReasoningAction(model, maxReasoningCalls);
    }

    @Bean
    RetrievalAction memoryRetrievalAction() { return new MemoryRetrievalAction(); }

    @Bean
    SessionMemoryStore sessionMemoryStore(
            @Value("${coala.memory.max-sessions:1000}") int maxSessions,
            @Value("${coala.memory.idle-ttl:PT30M}") Duration idleTtl,
            @Value("${coala.memory.max-records-per-session:32}") int maxRecordsPerSession) {
        return new SessionMemoryStore(maxSessions, idleTtl, maxRecordsPerSession);
    }

    @Bean
    DirectActionDecisionProcedure directActionDecisionProcedure(ReasoningAction reasoning, ActionOutputParser parser) {
        return new DirectActionDecisionProcedure(reasoning, parser);
    }

    @Bean
    IterativeReasonActDecisionProcedure iterativeReasonActDecisionProcedure(ReasoningAction reasoning, ActionOutputParser parser) {
        return new IterativeReasonActDecisionProcedure(reasoning, parser);
    }

    @Bean
    DeliberativeDecisionProcedure deliberativeDecisionProcedure(
            ReasoningAction reasoning, RetrievalAction retrieval, ActionOutputParser parser,
            @Value("${coala.planning.max-candidates:6}") int maxCandidates,
            @Value("${coala.planning.max-proposal-attempts:3}") int maxProposalAttempts) {
        return new DeliberativeDecisionProcedure(reasoning, retrieval, parser, maxCandidates, maxProposalAttempts);
    }
}
