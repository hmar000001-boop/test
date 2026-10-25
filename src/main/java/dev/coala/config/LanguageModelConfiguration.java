package dev.coala.config;

import dev.coala.llm.LanguageModel;
import dev.coala.llm.ScriptedLanguageModel;
import dev.coala.llm.SpringAiLanguageModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LanguageModelConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "coala.llm", name = "provider", havingValue = "scripted", matchIfMissing = true)
    LanguageModel scriptedLanguageModel() {
        return new ScriptedLanguageModel();
    }

    @Bean
    @ConditionalOnProperty(prefix = "coala.llm", name = "provider", havingValue = "openai")
    LanguageModel springAiLanguageModel(ChatClient.Builder builder) {
        return new SpringAiLanguageModel(builder);
    }
}
