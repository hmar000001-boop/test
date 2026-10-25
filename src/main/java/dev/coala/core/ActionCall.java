package dev.coala.core;

import java.util.Map;
import java.util.Objects;

/** A selected action and its arguments. It is data, never executable code. */
public record ActionCall(String actionId, Map<String, String> arguments) {
    public static final int MAX_ARGUMENTS = 32;
    public static final int MAX_ARGUMENT_KEY_LENGTH = 80;
    public static final int MAX_ARGUMENT_VALUE_LENGTH = 4_000;
    public static final int MAX_TOTAL_ARGUMENT_CHARACTERS = 32_000;

    public ActionCall {
        if (actionId == null || actionId.isBlank()) {
            throw new IllegalArgumentException("actionId must not be blank");
        }
        if (actionId.length() > 100) throw new IllegalArgumentException("actionId is too long");
        arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
        if (arguments.size() > MAX_ARGUMENTS) throw new IllegalArgumentException("Too many action arguments");
        int totalCharacters = 0;
        for (Map.Entry<String, String> entry : arguments.entrySet()) {
            String key = Objects.requireNonNull(entry.getKey(), "argument key");
            String value = Objects.requireNonNull(entry.getValue(), "argument value");
            if (key.isBlank() || key.length() > MAX_ARGUMENT_KEY_LENGTH) {
                throw new IllegalArgumentException("Invalid action argument key length");
            }
            if (value.length() > MAX_ARGUMENT_VALUE_LENGTH) {
                throw new IllegalArgumentException("Action argument value exceeds the configured limit");
            }
            totalCharacters += key.length() + value.length();
        }
        if (totalCharacters > MAX_TOTAL_ARGUMENT_CHARACTERS) {
            throw new IllegalArgumentException("Total action argument characters exceed the configured limit");
        }
    }

    public String argument(String name, String fallback) {
        return arguments.getOrDefault(name, fallback);
    }
}
