package dev.coala.skills;

import dev.coala.core.ActionArgumentDefinition;

import java.util.List;

/** Reviewed declarative skill entry pointing to registered source-coded action IDs; never contains executable code. */
public record SkillDefinition(String id, String version, String description, String actionId,
                              List<ActionArgumentDefinition> arguments, List<String> tags) {
    public SkillDefinition {
        if (id == null || !id.matches("[A-Za-z][A-Za-z0-9_.-]{0,99}")) throw new IllegalArgumentException("Invalid skill id");
        if (version == null || version.isBlank() || version.length() > 40) throw new IllegalArgumentException("version is required");
        if (description == null || description.isBlank() || description.length() > 1_000) throw new IllegalArgumentException("description is required and bounded");
        if (actionId == null || !actionId.matches("[A-Za-z][A-Za-z0-9_.-]{0,99}")) throw new IllegalArgumentException("Skill must reference a registered action ID");
        arguments = arguments == null ? List.of() : List.copyOf(arguments);
        tags = tags == null ? List.of() : tags.stream().filter(t -> t != null && !t.isBlank()).limit(20).map(t -> t.length() > 80 ? t.substring(0,80) : t).toList();
    }
}
