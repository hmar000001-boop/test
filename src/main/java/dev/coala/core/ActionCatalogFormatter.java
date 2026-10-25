package dev.coala.core;

import java.util.Collection;
import java.util.Comparator;
import java.util.stream.Collectors;

/** Single prompt-view formatter for the authoritative action registry; it is not the registry itself. */
public final class ActionCatalogFormatter {
    public String format(Collection<ActionDescriptor> descriptors) {
        if (descriptors == null || descriptors.isEmpty()) return "(no actions registered)";
        return descriptors.stream().sorted(Comparator.comparing(ActionDescriptor::id)).map(descriptor -> {
            String args = descriptor.argumentDefinitions().stream().map(argument -> argument.name() + ":" + argument.type()
                    + (argument.required() ? " required" : " optional")
                    + (argument.description().isBlank() ? "" : " - " + argument.description())
                    + " maxLength=" + argument.maxLength()
                    + (argument.allowedValues().isEmpty() ? "" : " values=" + argument.allowedValues()))
                    .collect(Collectors.joining(", "));
            return descriptor.id() + " [" + descriptor.kind() + ", risk=" + descriptor.risk() + ", idempotent=" + descriptor.idempotent()
                    + "]: " + descriptor.description() + "; arguments={" + args + "}";
        }).collect(Collectors.joining("\n"));
    }
}
