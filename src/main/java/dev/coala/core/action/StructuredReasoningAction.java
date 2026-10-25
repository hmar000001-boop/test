package dev.coala.core.action;

import dev.coala.core.AgentContext;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

/** Typed reasoning helper: provider output is parsed, validated, then projected into working memory. */
public final class StructuredReasoningAction<T> {
    private final ReasoningAction reasoning;
    private final String operation;
    private final Function<AgentContext, String> instruction;
    private final Function<String, T> parser;
    private final Predicate<T> validator;
    private final Function<T, Object> workingMemoryProjection;
    private final String destinationKey;

    public StructuredReasoningAction(ReasoningAction reasoning, String operation,
                                     Function<AgentContext, String> instruction,
                                     Function<String, T> parser, Predicate<T> validator,
                                     Function<T, Object> workingMemoryProjection, String destinationKey) {
        this.reasoning = Objects.requireNonNull(reasoning); this.operation = requireText(operation, "operation");
        this.instruction = Objects.requireNonNull(instruction); this.parser = Objects.requireNonNull(parser);
        this.validator = validator == null ? ignored -> true : validator;
        this.workingMemoryProjection = Objects.requireNonNull(workingMemoryProjection);
        this.destinationKey = requireText(destinationKey, "destinationKey");
    }
    public T execute(AgentContext context) {
        String raw = reasoning.reason(operation, instruction.apply(context), context);
        final T parsed;
        try { parsed = parser.apply(raw); }
        catch (RuntimeException ex) { throw new IllegalArgumentException("Structured reasoning output could not be parsed for operation " + operation, ex); }
        if (parsed == null || !validator.test(parsed)) throw new IllegalArgumentException("Structured reasoning output failed validation for operation " + operation);
        context.workingMemory().put(destinationKey, workingMemoryProjection.apply(parsed));
        context.workingMemory().put("reasoning.latest", null);
        return parsed;
    }
    private static String requireText(String value, String name) { if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required"); return value; }
}
