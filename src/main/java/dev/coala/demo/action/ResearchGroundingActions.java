package dev.coala.demo.action;

import dev.coala.core.ActionArgumentDefinition;
import dev.coala.core.ActionCall;
import dev.coala.core.ActionResult;
import dev.coala.core.ActionRisk;
import dev.coala.core.AgentContext;
import dev.coala.core.GroundingAction;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Deterministic report-formatting actions around the built-in web-search adapter. */
public final class ResearchGroundingActions {
    private ResearchGroundingActions() { }

    public static GroundingAction formatEvidenceBrief() {
        return new GroundingAction() {
            @Override public String id() { return "format_evidence_brief"; }
            @Override public String description() { return "Format source-attributed search results into a bounded evidence brief; do not infer that demo fixtures are real evidence."; }
            @Override public Set<String> requiredArguments() { return Set.of("topic"); }
            @Override public List<ActionArgumentDefinition> argumentDefinitions() {
                return List.of(ActionArgumentDefinition.requiredString("topic", "Research question or topic"));
            }
            @Override public ActionRisk risk() { return ActionRisk.LOW; }
            @Override public boolean idempotent() { return true; }
            @Override public ActionResult execute(ActionCall call, AgentContext context) {
                Object raw = context.workingMemory().get("web_search.results").orElse(List.of());
                List<Map<?, ?>> results = new ArrayList<>();
                if (raw instanceof List<?> list) {
                    for (Object item : list) if (item instanceof Map<?, ?> map) results.add(map);
                }
                String topic = call.argument("topic", context.workingMemory().getString("goal", "research topic"));
                StringBuilder brief = new StringBuilder("Research brief: ").append(topic)
                        .append("\n\nEvidence inventory (synthetic demo results; not live sources):\n");
                int index = 1;
                for (Map<?, ?> result : results) {
                    brief.append(index++).append(". ")
                            .append(value(result, "title")).append("\n")
                            .append("   Source: ").append(value(result, "source")).append(" | ")
                            .append(value(result, "url")).append("\n")
                            .append("   Snippet: ").append(value(result, "snippet")).append("\n\n");
                }
                if (results.isEmpty()) brief.append("No source-attributed results were returned. Do not infer a conclusion from missing evidence.\n\n");
                brief.append("Assessment: the fixture demonstrates source attribution and limitations; it does not substantiate the topic's factual claims.\n")
                        .append("Next step: replace SimulatedWebSearchProvider with a real search-provider adapter and verify each source before publishing.");
                String output = brief.length() > 12_000 ? brief.substring(0, 11_997) + "..." : brief.toString();
                context.workingMemory().put("research.topic", topic);
                context.workingMemory().put("research.brief", output);
                context.workingMemory().put("research.source_count", results.size());
                return ActionResult.continueWith("Formatted " + results.size() + " source-attributed search result(s) into the research evidence brief.");
            }
        };
    }

    public static GroundingAction deliverResearchBrief() {
        return new GroundingAction() {
            @Override public String id() { return "deliver_research_brief"; }
            @Override public String description() { return "Return the prepared evidence brief as the final research outcome."; }
            @Override public ActionRisk risk() { return ActionRisk.LOW; }
            @Override public boolean idempotent() { return true; }
            @Override public ActionResult execute(ActionCall call, AgentContext context) {
                String brief = context.workingMemory().getString("research.brief", "No evidence brief has been prepared. The run should not claim that research is complete.");
                context.workingMemory().put("research.final", brief);
                return ActionResult.finish(brief);
            }
        };
    }

    private static String value(Map<?, ?> map, String key) {
        Object value = map.get(key);
        return value == null ? "(not provided)" : String.valueOf(value);
    }
}
