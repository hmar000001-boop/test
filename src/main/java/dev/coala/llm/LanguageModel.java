package dev.coala.llm;

/** Small provider-neutral boundary. Spring AI is isolated behind its adapter. */
public interface LanguageModel {
    String generate(String prompt);
}
