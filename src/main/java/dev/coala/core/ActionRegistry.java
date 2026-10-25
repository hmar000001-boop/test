package dev.coala.core;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Explicit allow-list of executable actions available to one decision procedure. */
public final class ActionRegistry {
    private final Map<String, CoalaAction> actions;

    public ActionRegistry(Collection<? extends CoalaAction> actionList) {
        Objects.requireNonNull(actionList, "actionList");
        Map<String, CoalaAction> found = new LinkedHashMap<>();
        for (CoalaAction action : actionList) {
            if (action == null) throw new IllegalArgumentException("Action list contains null");
            if (action.id() == null || action.id().isBlank()) throw new IllegalArgumentException("Action id must not be blank");
            ActionDescriptor descriptor = action.descriptor();
            if (!action.id().equals(descriptor.id())) {
                throw new IllegalArgumentException("Action descriptor id does not match action id: " + action.id());
            }
            if (found.putIfAbsent(action.id(), action) != null) {
                throw new IllegalArgumentException("Duplicate action id: " + action.id());
            }
        }
        if (found.isEmpty()) throw new IllegalArgumentException("At least one action is required");
        this.actions = Collections.unmodifiableMap(new LinkedHashMap<>(found));
    }

    public CoalaAction require(String id) {
        CoalaAction action = actions.get(id);
        if (action == null) throw new DecisionProcedureException("Unknown action id proposed by the model: " + id);
        return action;
    }

    /** Resolve the action against the allow-list and enforce its declared argument contract. */
    public CoalaAction validate(ActionCall call) {
        CoalaAction action = require(call.actionId());
        action.validate(call);
        return action;
    }

    public List<ActionDescriptor> descriptors() {
        return actions.values().stream().map(CoalaAction::descriptor).toList();
    }

    public Collection<CoalaAction> all() {
        return actions.values();
    }
}
