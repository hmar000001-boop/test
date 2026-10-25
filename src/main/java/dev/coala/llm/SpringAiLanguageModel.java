package dev.coala.llm;

import org.springframework.ai.chat.client.ChatClient;

/**
 * The only Spring AI usage in the framework: one provider-neutral text-generation
 * call. Planning, memory, action dispatch and control flow live in the CoALA code.
 */
public final class SpringAiLanguageModel implements LanguageModel {
    private final ChatClient chatClient;

    public SpringAiLanguageModel(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @Override
    public String generate(String prompt) {
        try {
            String content = chatClient.prompt().user(prompt).call().content();
            if (content == null || content.isBlank()) {
                throw new LanguageModelException("Language-model provider returned an empty response.");
            }
            return content;
        } catch (LanguageModelException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new LanguageModelException("Language-model provider request failed.", ex);
        }
    }
}
