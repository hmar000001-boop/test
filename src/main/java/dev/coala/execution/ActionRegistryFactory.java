package dev.coala.execution;

import dev.coala.core.ActionRegistry;
import dev.coala.core.CoalaAction;

import java.util.Collection;
import java.util.List;

/** Explicitly constructs an allow-listed action registry and optionally wraps every action in execution decorators. */
public final class ActionRegistryFactory {
    private final List<ActionExecutionDecorator> decorators;
    public ActionRegistryFactory(List<ActionExecutionDecorator> decorators) { this.decorators = decorators == null ? List.of() : List.copyOf(decorators); }
    public ActionRegistry create(Collection<? extends CoalaAction> actions) {
        List<CoalaAction> wrapped = actions.stream().map(action -> {
            CoalaAction current = action;
            for (ActionExecutionDecorator decorator : decorators) current = decorator.decorate(current);
            return current;
        }).toList();
        return new ActionRegistry(wrapped);
    }
}
