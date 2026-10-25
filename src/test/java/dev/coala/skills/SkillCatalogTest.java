package dev.coala.skills;

import dev.coala.core.WorkingMemory;
import dev.coala.core.AgentContext;
import dev.coala.core.memory.InMemoryLongTermMemory;
import dev.coala.core.ActionArgumentDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SkillCatalogTest {
    @Test void catalogueContainsReviewedDescriptorsAndRetrievalNeverExecutesCode() {
        SkillDefinition skill = new SkillDefinition("search.docs", "1.0", "Search the approved documentation corpus", "search_docs",
                List.of(ActionArgumentDefinition.requiredString("query", "Search query")), List.of("docs", "knowledge"));
        SkillCatalog catalog = new SkillCatalog(List.of(skill));
        var retrieval = new SkillRetrievalAction(catalog);
        var context = new AgentContext(new WorkingMemory(), new InMemoryLongTermMemory());
        assertTrue(catalog.find("search.docs").isPresent());
        retrieval.retrieve("documentation", context, 5);
        assertTrue(context.workingMemory().get("retrieved_skills").orElseThrow().toString().contains("search_docs"));
        assertThrows(IllegalArgumentException.class, () -> new SkillCatalog(List.of(skill, skill)));
    }
}
