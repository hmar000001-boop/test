package dev.coala.llm;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic offline adapter for demos/tests. It is not an LLM and does not
 * attempt general reasoning. It returns fixtures selected by operation and the
 * registered action catalogue. Activate the 'openai' profile for actual LLM calls.
 */
public final class ScriptedLanguageModel implements LanguageModel {
    private static final Pattern CYCLE = Pattern.compile("CYCLE=(\\d+)");
    private static final Pattern GOAL = Pattern.compile("(?m)^GOAL=(.*)$");
    private static final Pattern DESTINATION = Pattern.compile("(?i)\\b(?:trip|travel|flight|holiday|vacation)\\s+to\\s+([A-Za-z][A-Za-z -]{0,35})");
    private static final Pattern BUDGET = Pattern.compile("(?i)(?:CAD\\s*|\\$\\s*)([0-9]{2,6})");

    @Override
    public String generate(String prompt) {
        String operation = extractOperation(prompt);
        int cycle = extractCycle(prompt);
        return switch (operation) {
            case "DIRECT_ACTION" -> "{\"actionId\":\"respond\",\"arguments\":{\"text\":\"This is the direct-action demo response. The request was handled by one selected dialogue grounding action; enable the openai profile to use an actual LLM.\"}}";
            case "PLAN_FIRST" -> planFirst(prompt);
            case "ITERATIVE_NEXT_ACTION" -> iterativeAction(cycle, prompt);
            case "PROPOSE_CANDIDATES" -> prompt.contains("format_evidence_brief") ? researchCandidates(cycle, prompt) : puzzleCandidates(cycle);
            case "EVALUATE_CANDIDATES" -> prompt.contains("format_evidence_brief") ? researchScores(cycle) : puzzleScores(cycle);
            default -> throw new IllegalArgumentException("Scripted adapter has no fixture for operation=" + operation);
        };
    }

    private String extractOperation(String prompt) {
        Matcher matcher = Pattern.compile("OPERATION=([A-Z_]+)").matcher(prompt);
        if (!matcher.find()) throw new IllegalArgumentException("Prompt has no OPERATION marker.");
        return matcher.group(1);
    }

    private int extractCycle(String prompt) {
        Matcher matcher = CYCLE.matcher(prompt);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : 1;
    }

    private String extractGoal(String prompt) {
        Matcher matcher = GOAL.matcher(prompt);
        return matcher.find() ? matcher.group(1).trim() : "the requested topic";
    }

    private String extractDestination(String prompt) {
        Matcher matcher = DESTINATION.matcher(extractGoal(prompt));
        if (!matcher.find()) return "Paris";
        String result = matcher.group(1).trim();
        // Fixture-only parser stops at common temporal/prepositional phrases.
        result = result.replaceFirst("(?i)\\s+(next|this|on|for|within|with|from|during)\\b.*$", "").trim();
        if (result.isBlank()) return "Paris";
        return result.length() > 40 ? result.substring(0, 40) : result;
    }

    private String planFirst(String prompt) {
        if (!prompt.contains("check_calendar") || !prompt.contains("draft_itinerary")) {
            throw new IllegalArgumentException("Scripted adapter has no PLAN_FIRST fixture for this action catalogue.");
        }
        String destination = jsonEscape(extractDestination(prompt));
        String budget = extractBudget(prompt);
        return "{\"steps\":["
                + "{\"actionId\":\"check_calendar\",\"arguments\":{}},"
                + "{\"actionId\":\"search_flights\",\"arguments\":{\"destination\":\"" + destination + "\",\"origin\":\"YUL\"}},"
                + "{\"actionId\":\"search_hotels\",\"arguments\":{\"destination\":\"" + destination + "\",\"checkIn\":\"2026-11-05\",\"nights\":\"3\"}},"
                + "{\"actionId\":\"draft_itinerary\",\"arguments\":{\"budgetCad\":\"" + budget + "\"}},"
                + "{\"actionId\":\"learn_episode\",\"arguments\":{\"content\":\"Travel workflow lesson: check the calendar before inventory searches and never book from mock results.\",\"success\":\"true\",\"importance\":\"0.6\",\"source\":\"travel-demo\"}},"
                + "{\"actionId\":\"present_itinerary\",\"arguments\":{}}]}";
    }

    private String iterativeAction(int cycle, String prompt) {
        if (prompt.contains("check_calendar") && prompt.contains("draft_itinerary")) return travelAction(cycle, prompt);
        if (prompt.contains("classify_support_case") && prompt.contains("escalate_case")) return supportAction(cycle, prompt);
        return iterativeCodingAction(cycle);
    }

    private String travelAction(int cycle, String prompt) {
        String destination = jsonEscape(extractDestination(prompt));
        return switch (cycle) {
            case 1 -> "{\"actionId\":\"check_calendar\",\"arguments\":{}}";
            case 2 -> "{\"actionId\":\"search_flights\",\"arguments\":{\"destination\":\"" + destination + "\",\"origin\":\"YUL\"}}";
            case 3 -> "{\"actionId\":\"search_hotels\",\"arguments\":{\"destination\":\"" + destination + "\",\"checkIn\":\"2026-11-05\",\"nights\":\"3\"}}";
            case 4 -> "{\"actionId\":\"draft_itinerary\",\"arguments\":{}}";
            case 5 -> "{\"actionId\":\"learn_episode\",\"arguments\":{\"content\":\"Travel workflow lesson: check the calendar before inventory searches and never book from mock results.\",\"success\":\"true\",\"importance\":\"0.6\",\"source\":\"travel-demo\"}}";
            default -> "{\"actionId\":\"present_itinerary\",\"arguments\":{}}";
        };
    }

    private String extractBudget(String prompt) {
        Matcher matcher = BUDGET.matcher(extractGoal(prompt));
        return matcher.find() ? matcher.group(1) : "1400";
    }

    private String supportAction(int cycle, String prompt) {
        if (cycle == 1) return "{\"actionId\":\"classify_support_case\",\"arguments\":{}}";
        String goal = extractGoal(prompt).toLowerCase(Locale.ROOT);
        boolean elevated = containsAny(goal, "locked", "compromised", "fraud", "unauthorized", "payment", "security");
        return elevated
                ? "{\"actionId\":\"escalate_case\",\"arguments\":{}}"
                : "{\"actionId\":\"suggest_safe_steps\",\"arguments\":{}}";
    }

    private String iterativeCodingAction(int cycle) {
        return switch (cycle) {
            case 1 -> "{\"actionId\":\"inspect_project\",\"arguments\":{}}";
            case 2 -> "{\"actionId\":\"apply_patch\",\"arguments\":{\"change\":\"fix the off-by-one boundary described by inspection\"}}";
            case 3 -> "{\"actionId\":\"run_tests\",\"arguments\":{}}";
            case 4 -> "{\"actionId\":\"remember_experience\",\"arguments\":{\"content\":\"Demo coding cycle: inspect the repository, record a patch, and verify with the test fixture.\"}}";
            default -> "{\"actionId\":\"respond\",\"arguments\":{\"text\":\"The mock coding workflow completed: the patch was recorded and the predefined fixture reported 12/12 passing. No project files were modified and no arbitrary code was executed.\"}}";
        };
    }

    private String researchCandidates(int cycle, String prompt) {
        String query = jsonEscape(extractGoal(prompt));
        return switch (cycle) {
            case 1 -> "{\"candidates\":[{\"actionId\":\"web_search\",\"arguments\":{\"query\":\"" + query + "\",\"maxResults\":\"3\"}},{\"actionId\":\"deliver_research_brief\",\"arguments\":{}}]}";
            case 2 -> "{\"candidates\":[{\"actionId\":\"format_evidence_brief\",\"arguments\":{\"topic\":\"" + query + "\"}},{\"actionId\":\"web_search\",\"arguments\":{\"query\":\"" + query + "\",\"maxResults\":\"3\"}}]}";
            case 3 -> "{\"candidates\":[{\"actionId\":\"learn_episode\",\"arguments\":{\"content\":\"Research lesson: preserve source attribution and label synthetic or unverified evidence.\",\"success\":\"true\",\"importance\":\"0.6\",\"source\":\"research-demo\"}},{\"actionId\":\"deliver_research_brief\",\"arguments\":{}}]}";
            default -> "{\"candidates\":[{\"actionId\":\"deliver_research_brief\",\"arguments\":{}},{\"actionId\":\"format_evidence_brief\",\"arguments\":{\"topic\":\"" + query + "\"}}]}";
        };
    }

    private String researchScores(int cycle) {
        return switch (cycle) {
            case 1 -> "{\"rejectAll\":false,\"scores\":[0.98,0.10]}";
            case 2 -> "{\"rejectAll\":false,\"scores\":[0.96,0.20]}";
            case 3 -> "{\"rejectAll\":false,\"scores\":[0.95,0.25]}";
            default -> "{\"rejectAll\":false,\"scores\":[0.99,0.20]}";
        };
    }

    private String puzzleCandidates(int cycle) {
        if (cycle <= 1) {
            return "{\"candidates\":["
                    + "{\"actionId\":\"inspect_clues\",\"arguments\":{}},"
                    + "{\"actionId\":\"check_hypothesis\",\"arguments\":{\"answer\":\"red\"}}]}";
        }
        return "{\"candidates\":["
                + "{\"actionId\":\"check_hypothesis\",\"arguments\":{\"answer\":\"blue\"}},"
                + "{\"actionId\":\"submit_answer\",\"arguments\":{\"answer\":\"blue\"}}]}";
    }

    private String puzzleScores(int cycle) {
        return cycle <= 1 ? "{\"rejectAll\":false,\"scores\":[0.95,0.15]}" : "{\"rejectAll\":false,\"scores\":[0.55,0.98]}";
    }

    private static boolean containsAny(String text, String... values) {
        for (String value : values) if (text.contains(value)) return true;
        return false;
    }

    private static String jsonEscape(String value) {
        String text = value == null ? "" : value;
        StringBuilder out = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> { if (c < 0x20) out.append(String.format("\\u%04x", (int) c)); else out.append(c); }
            }
        }
        return out.toString();
    }
}
