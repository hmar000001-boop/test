package dev.coala.skills;

import dev.coala.core.AgentContext;
import dev.coala.core.memory.MemoryRecord;
import dev.coala.core.action.RetrievalAction;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Retrieves reviewed skill descriptions and action references for reasoning; does not execute skill text. */
public final class SkillRetrievalAction implements RetrievalAction {
    private final SkillCatalog catalog;
    public SkillRetrievalAction(SkillCatalog catalog) { this.catalog = java.util.Objects.requireNonNull(catalog); }
    @Override public List<MemoryRecord> retrieve(String query, AgentContext context, int limit) {
        String q = query == null ? "" : query.toLowerCase(Locale.ROOT);
        List<java.util.Map<String,Object>> matches = catalog.all().stream()
                .filter(skill -> q.isBlank() || (skill.id() + " " + skill.description() + " " + String.join(" ", skill.tags())).toLowerCase(Locale.ROOT).contains(q))
                .limit(Math.max(1, Math.min(limit, 100))).map(skill -> java.util.Map.<String,Object>of(
                        "id", skill.id(), "version", skill.version(), "description", skill.description(),
                        "actionId", skill.actionId(), "tags", skill.tags(),
                        "arguments", skill.arguments().stream().map(a -> java.util.Map.of("name", a.name(), "type", a.type().name(), "required", a.required(), "description", a.description())).toList()))
                .toList();
        context.workingMemory().put("retrieved_skills", matches);
        // This is procedural-catalog retrieval, not episodic/semantic long-term memory. Keep the return type for interop.
        return List.of();
    }
}
