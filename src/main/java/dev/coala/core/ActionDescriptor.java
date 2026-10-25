package dev.coala.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Public, non-executable description of a registered action. */
public record ActionDescriptor(
        String id,
        ActionKind kind,
        String description,
        List<String> requiredArguments,
        List<String> optionalArguments,
        List<ActionArgumentDefinition> argumentDefinitions,
        ActionRisk risk,
        boolean idempotent) {

    public ActionDescriptor {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id must not be blank");
        if (kind == null) throw new IllegalArgumentException("kind is required");
        if (description == null || description.isBlank()) throw new IllegalArgumentException("description must not be blank");
        requiredArguments = requiredArguments == null ? List.of() : List.copyOf(requiredArguments);
        optionalArguments = optionalArguments == null ? List.of() : List.copyOf(optionalArguments);
        risk = risk == null ? ActionRisk.MEDIUM : risk;
        validateNames(requiredArguments, "required");
        validateNames(optionalArguments, "optional");
        if (requiredArguments.stream().anyMatch(optionalArguments::contains)) throw new IllegalArgumentException("An argument cannot be both required and optional");
        argumentDefinitions = argumentDefinitions == null ? List.of() : List.copyOf(argumentDefinitions);
        if (argumentDefinitions.isEmpty()) argumentDefinitions = legacyDefinitions(requiredArguments, optionalArguments);
        Set<String> names = new HashSet<>();
        Set<String> definedRequired = new HashSet<>();
        Set<String> definedOptional = new HashSet<>();
        for (ActionArgumentDefinition definition : argumentDefinitions) {
            if (!names.add(definition.name())) throw new IllegalArgumentException("Duplicate argument definition: " + definition.name());
            (definition.required() ? definedRequired : definedOptional).add(definition.name());
        }
        if (!definedRequired.equals(Set.copyOf(requiredArguments)) || !definedOptional.equals(Set.copyOf(optionalArguments))) {
            throw new IllegalArgumentException("Typed argument definitions must match required/optional argument lists");
        }
    }

    public ActionDescriptor(String id, ActionKind kind, String description,
                            List<String> requiredArguments, List<String> optionalArguments) {
        this(id, kind, description, requiredArguments, optionalArguments, List.of(), ActionRisk.MEDIUM, false);
    }
    public ActionDescriptor(String id, ActionKind kind, String description) {
        this(id, kind, description, List.of(), List.of(), List.of(), ActionRisk.MEDIUM, false);
    }

    private static List<ActionArgumentDefinition> legacyDefinitions(List<String> required, List<String> optional) {
        List<ActionArgumentDefinition> result = new ArrayList<>();
        required.forEach(name -> result.add(ActionArgumentDefinition.requiredString(name, "")));
        optional.forEach(name -> result.add(ActionArgumentDefinition.optionalString(name, "")));
        return List.copyOf(result);
    }
    private static void validateNames(List<String> names, String kind) {
        Set<String> unique = new HashSet<>();
        for (String name : names) {
            if (name == null || name.isBlank() || name.length() > ActionCall.MAX_ARGUMENT_KEY_LENGTH)
                throw new IllegalArgumentException("Invalid " + kind + " argument name");
            if (!unique.add(name)) throw new IllegalArgumentException("Duplicate " + kind + " argument: " + name);
        }
    }
}
