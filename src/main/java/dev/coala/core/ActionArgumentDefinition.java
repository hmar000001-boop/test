package dev.coala.core;

import java.math.BigDecimal;
import java.net.URI;
import java.util.LinkedHashSet;
import java.util.Set;

/** Typed input contract for a registered action. This is an implementation-level validation aid. */
public record ActionArgumentDefinition(
        String name,
        ValueType type,
        String description,
        boolean required,
        Set<String> allowedValues,
        int maxLength) {

    public enum ValueType { STRING, INTEGER, DECIMAL, BOOLEAN, ENUM, URI }

    public ActionArgumentDefinition {
        if (name == null || name.isBlank() || name.length() > ActionCall.MAX_ARGUMENT_KEY_LENGTH)
            throw new IllegalArgumentException("Argument name must be non-blank and at most " + ActionCall.MAX_ARGUMENT_KEY_LENGTH + " characters");
        if (type == null) throw new IllegalArgumentException("Argument type is required");
        description = description == null ? "" : description;
        allowedValues = allowedValues == null ? Set.of() : Set.copyOf(new LinkedHashSet<>(allowedValues));
        if (maxLength < 1 || maxLength > ActionCall.MAX_ARGUMENT_VALUE_LENGTH)
            throw new IllegalArgumentException("maxLength must be between 1 and " + ActionCall.MAX_ARGUMENT_VALUE_LENGTH);
        if (type == ValueType.ENUM && allowedValues.isEmpty()) throw new IllegalArgumentException("ENUM arguments must declare allowedValues");
        if (type != ValueType.ENUM && !allowedValues.isEmpty()) throw new IllegalArgumentException("Only ENUM arguments may declare allowedValues");
    }

    public static ActionArgumentDefinition requiredString(String name, String description) {
        return new ActionArgumentDefinition(name, ValueType.STRING, description, true, Set.of(), 4_000);
    }
    public static ActionArgumentDefinition optionalString(String name, String description) {
        return new ActionArgumentDefinition(name, ValueType.STRING, description, false, Set.of(), 4_000);
    }

    public void validate(String value) {
        if (value == null) throw new DecisionProcedureException("Argument '" + name + "' must not be null.");
        if (value.length() > maxLength) throw new DecisionProcedureException("Argument '" + name + "' exceeds its maximum length of " + maxLength + ".");
        if (required && value.isBlank()) throw new DecisionProcedureException("Required argument '" + name + "' must not be blank.");
        if (value.isBlank() && !required) return;
        try {
            switch (type) {
                case STRING -> { }
                case INTEGER -> Long.parseLong(value.trim());
                case DECIMAL -> {
                    BigDecimal decimal = new BigDecimal(value.trim());
                    if (decimal.precision() > 100 || Math.abs(decimal.scale()) > 100) throw new NumberFormatException("numeric value outside supported precision");
                }
                case BOOLEAN -> {
                    if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) throw new IllegalArgumentException("must be true or false");
                }
                case ENUM -> {
                    if (!allowedValues.contains(value)) throw new IllegalArgumentException("must be one of " + allowedValues);
                }
                case URI -> {
                    URI uri = URI.create(value.trim());
                    if (!uri.isAbsolute()) throw new IllegalArgumentException("must be an absolute URI");
                }
            }
        } catch (RuntimeException ex) {
            throw new DecisionProcedureException("Argument '" + name + "' is not a valid " + type + " value: " + ex.getMessage(), ex);
        }
    }
}
