package dev.coala.llm;

/** Indicates a failure calling the configured language-model provider/adapter. */
public final class LanguageModelException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public LanguageModelException(String message) { super(message); }
    public LanguageModelException(String message, Throwable cause) { super(message, cause); }
}
