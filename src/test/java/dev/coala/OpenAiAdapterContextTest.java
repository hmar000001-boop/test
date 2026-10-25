package dev.coala;

import dev.coala.llm.LanguageModel;
import dev.coala.llm.SpringAiLanguageModel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "coala.llm.provider=openai",
        "spring.ai.model.chat=openai",
        "spring.ai.openai.api-key=test-key",
        "coala.api.token="
})
class OpenAiAdapterContextTest {
    @Autowired LanguageModel languageModel;

    @Test
    void openAiAdapterIsWiredWithoutMakingProviderCall() {
        assertNotNull(languageModel);
        assertInstanceOf(SpringAiLanguageModel.class, languageModel);
    }
}
