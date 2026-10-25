package dev.coala.skills;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Versioned, explicitly reviewed catalogue of source-coded skills. */
public final class SkillCatalog {
    private final Map<String, SkillDefinition> skills;
    public SkillCatalog(Collection<SkillDefinition> definitions) {
        Map<String, SkillDefinition> found = new LinkedHashMap<>();
        if (definitions != null) for (SkillDefinition d : definitions) {
            if (d == null || found.putIfAbsent(d.id(), d) != null) throw new IllegalArgumentException("Null or duplicate skill definition");
        }
        this.skills = Map.copyOf(found);
    }
    public List<SkillDefinition> all() { return skills.values().stream().sorted(java.util.Comparator.comparing(SkillDefinition::id)).toList(); }
    public java.util.Optional<SkillDefinition> find(String id) { return java.util.Optional.ofNullable(skills.get(id)); }
}
