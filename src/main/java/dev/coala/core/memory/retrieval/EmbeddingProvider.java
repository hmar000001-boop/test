package dev.coala.core.memory.retrieval;

/** Port for text embedding; provider/model selection is explicitly left to the application. */
@FunctionalInterface
public interface EmbeddingProvider {
    float[] embed(String text);
}
