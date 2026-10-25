package dev.coala.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Code implementing a selected grounding or learning action. */
public interface CoalaAction {
    String id();
    String description();
    ActionKind kind();
    ActionResult execute(ActionCall call, AgentContext context);
    default Set<String> requiredArguments() { return Set.of(); }
    default Set<String> optionalArguments() { return Set.of(); }
    default ActionRisk risk() { return kind() == ActionKind.LEARNING ? ActionRisk.LOW : ActionRisk.MEDIUM; }
    /** Must only be true when repeating the same call cannot duplicate or compound its effect. */
    default boolean idempotent() { return false; }

    default List<ActionArgumentDefinition> argumentDefinitions() {
        List<ActionArgumentDefinition> definitions = new ArrayList<>();
        requiredArguments().stream().sorted().forEach(name -> definitions.add(ActionArgumentDefinition.requiredString(name, "")));
        optionalArguments().stream().sorted().forEach(name -> definitions.add(ActionArgumentDefinition.optionalString(name, "")));
        return List.copyOf(definitions);
    }

    default void validate(ActionCall call) {
        Objects.requireNonNull(call, "call");
        List<ActionArgumentDefinition> definitions = argumentDefinitions();
        if (definitions == null) throw new IllegalStateException("Action argument contract must not be null: " + id());
        Set<String> allowed = new HashSet<>();
        Set<String> required = new HashSet<>();
        for (ActionArgumentDefinition definition : definitions) {
            if (definition == null || !allowed.add(definition.name())) throw new IllegalStateException("Duplicate/null argument definition for action " + id());
            if (definition.required()) required.add(definition.name());
        }
        Set<String> unknown = call.arguments().keySet().stream().filter(key -> !allowed.contains(key)).collect(Collectors.toSet());
        if (!unknown.isEmpty()) throw new DecisionProcedureException("Action '" + id() + "' received unsupported argument(s): " + unknown);
        for (ActionArgumentDefinition definition : definitions) {
            String value = call.arguments().get(definition.name());
            if (value == null && definition.required()) throw new DecisionProcedureException("Action '" + id() + "' requires argument '" + definition.name() + "'.");
            if (value != null) definition.validate(value);
        }
        if (!required.equals(requiredArguments()) || !allowed.stream().filter(k -> !required.contains(k)).collect(Collectors.toSet()).equals(optionalArguments())) {
            throw new IllegalStateException("Action argumentDefinitions must match requiredArguments/optionalArguments for action " + id());
        }
    }

    default ActionDescriptor descriptor() {
        return new ActionDescriptor(id(), kind(), description(), requiredArguments().stream().sorted().toList(),
                optionalArguments().stream().sorted().toList(), argumentDefinitions(), risk(), idempotent());
    }
}
