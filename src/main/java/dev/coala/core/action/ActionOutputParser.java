package dev.coala.core.action;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import dev.coala.core.ActionCall;
import dev.coala.core.DecisionProcedureException;
import dev.coala.core.InvalidModelOutputException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Parses constrained JSON returned by reasoning actions; it never executes model-generated code. */
public final class ActionOutputParser {
    private final ObjectMapper mapper;

    public ActionOutputParser() {
        // Jackson 3 is Spring Boot 4's default. Reject duplicate object keys and
        // trailing JSON values so ambiguous model output fails closed.
        this.mapper = JsonMapper.builder()
                .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .build();
    }

    public ActionCall parseAction(String raw) {
        JsonNode root = parseJson(raw, "action proposal");
        requireObject(root, "Action JSON must be an object.");
        requireOnlyFields(root, Set.of("actionId", "arguments"), "action proposal");
        JsonNode actionId = root.get("actionId");
        if (actionId == null || !actionId.isTextual() || actionId.asText().isBlank()) {
            throw new InvalidModelOutputException("Action JSON must contain a non-empty string 'actionId'.");
        }
        return buildActionCall(actionId.asText(), parseArguments(root.get("arguments")));
    }

    /** Parses a set of at least two distinct alternatives. */
    public List<ActionCall> parseCandidates(String raw, int maxCandidates) {
        if (maxCandidates < 2) throw new IllegalArgumentException("maxCandidates must be at least 2");
        JsonNode root = parseJson(raw, "candidate proposal");
        requireObject(root, "Candidate JSON must be an object.");
        requireOnlyFields(root, Set.of("candidates"), "candidate proposal");
        JsonNode candidates = root.get("candidates");
        if (candidates == null || !candidates.isArray() || candidates.size() < 2) {
            throw new InvalidModelOutputException("Candidate JSON must contain at least two alternatives in a 'candidates' array.");
        }
        if (candidates.size() > maxCandidates) {
            throw new InvalidModelOutputException("Model proposed " + candidates.size()
                    + " candidates; configured limit is " + maxCandidates + ".");
        }
        List<ActionCall> parsed = new ArrayList<>();
        Set<ActionCall> seen = new HashSet<>();
        for (JsonNode candidate : candidates) {
            requireObject(candidate, "Every candidate must be a JSON object.");
            requireOnlyFields(candidate, Set.of("actionId", "arguments"), "candidate action");
            JsonNode id = candidate.get("actionId");
            if (id == null || !id.isTextual() || id.asText().isBlank()) {
                throw new InvalidModelOutputException("Every candidate must contain a non-empty 'actionId'.");
            }
            ActionCall call = buildActionCall(id.asText(), parseArguments(candidate.get("arguments")));
            if (!seen.add(call)) {
                throw new InvalidModelOutputException("Candidate set contains duplicate action calls; propose distinct alternatives.");
            }
            parsed.add(call);
        }
        return List.copyOf(parsed);
    }

    /**
     * Parses an explicit selection decision and one score per candidate. rejectAll=true
     * means no candidate is selected and planning returns to proposal.
     */
    public CandidateEvaluation parseEvaluation(String raw, int expectedCandidates) {
        if (expectedCandidates < 2) throw new IllegalArgumentException("expectedCandidates must be at least 2");
        JsonNode root = parseJson(raw, "candidate evaluation");
        requireObject(root, "Evaluation JSON must be an object.");
        requireOnlyFields(root, Set.of("rejectAll", "scores"), "candidate evaluation");
        JsonNode reject = root.get("rejectAll");
        if (reject == null || !reject.isBoolean()) {
            throw new InvalidModelOutputException("Evaluation JSON must contain boolean 'rejectAll'.");
        }
        JsonNode scoreNode = root.get("scores");
        if (scoreNode == null || !scoreNode.isArray() || scoreNode.isEmpty()) {
            throw new InvalidModelOutputException("Evaluation JSON must contain a non-empty 'scores' array.");
        }
        if (scoreNode.size() != expectedCandidates) {
            throw new InvalidModelOutputException("Evaluator returned " + scoreNode.size() + " score(s) for " + expectedCandidates + " candidate(s).");
        }
        List<Double> scores = new ArrayList<>();
        for (JsonNode score : scoreNode) {
            if (!score.isNumber()) throw new InvalidModelOutputException("Every candidate score must be numeric.");
            double value = score.asDouble();
            if (!Double.isFinite(value)) throw new InvalidModelOutputException("Candidate scores must be finite numbers.");
            scores.add(value);
        }
        return new CandidateEvaluation(reject.asBoolean(), scores);
    }


    /** Parses a bounded structured plan of executable action calls. */
    public List<ActionCall> parseActionPlan(String raw, int maxSteps) {
        if (maxSteps < 1 || maxSteps > 20) throw new IllegalArgumentException("maxSteps must be 1..20");
        JsonNode root = parseJson(raw, "action plan");
        requireObject(root, "Plan JSON must be an object.");
        requireOnlyFields(root, Set.of("steps"), "action plan");
        JsonNode steps = root.get("steps");
        if (steps == null || !steps.isArray() || steps.isEmpty() || steps.size() > maxSteps)
            throw new InvalidModelOutputException("Plan must contain between 1 and " + maxSteps + " steps.");
        List<ActionCall> parsed = new ArrayList<>();
        for (JsonNode candidate : steps) {
            requireObject(candidate, "Every plan step must be an object.");
            requireOnlyFields(candidate, Set.of("actionId", "arguments"), "plan step");
            JsonNode id = candidate.get("actionId");
            if (id == null || !id.isTextual() || id.asText().isBlank()) throw new InvalidModelOutputException("Each plan step requires a non-empty actionId.");
            parsed.add(buildActionCall(id.asText(), parseArguments(candidate.get("arguments"))));
        }
        return List.copyOf(parsed);
    }

    public ActionQualityEvaluation parseQualityEvaluation(String raw) {
        JsonNode root = parseJson(raw, "action quality evaluation");
        requireObject(root, "Quality evaluation JSON must be an object.");
        requireOnlyFields(root, Set.of("accepted", "score", "feedback"), "quality evaluation");
        JsonNode accepted = root.get("accepted"), score = root.get("score"), feedback = root.get("feedback");
        if (accepted == null || !accepted.isBoolean() || score == null || !score.isNumber() || feedback == null || !feedback.isTextual())
            throw new InvalidModelOutputException("Quality evaluation requires boolean accepted, numeric score and string feedback.");
        double value = score.asDouble();
        if (!Double.isFinite(value) || value < 0 || value > 1) throw new InvalidModelOutputException("Quality score must be finite and between 0 and 1.");
        String note = feedback.asText();
        if (note.length() > 1_000) note = note.substring(0, 997) + "...";
        return new ActionQualityEvaluation(accepted.asBoolean(), value, note);
    }

    /** Retained as a utility for tests and callers that only need score parsing. */
    public List<Double> parseScores(String raw) {
        JsonNode root = parseJson(raw, "candidate evaluation");
        requireObject(root, "Evaluation JSON must be an object.");
        requireOnlyFields(root, Set.of("scores"), "candidate score result");
        JsonNode scores = root.get("scores");
        if (scores == null || !scores.isArray() || scores.isEmpty()) {
            throw new InvalidModelOutputException("Evaluation JSON must contain a non-empty 'scores' array.");
        }
        List<Double> result = new ArrayList<>();
        for (JsonNode score : scores) {
            if (!score.isNumber() || !Double.isFinite(score.asDouble())) {
                throw new InvalidModelOutputException("Every candidate score must be a finite number.");
            }
            result.add(score.asDouble());
        }
        return List.copyOf(result);
    }

    private Map<String, String> parseArguments(JsonNode node) {
        if (node == null || node.isNull() || !node.isObject()) {
            throw new InvalidModelOutputException("'arguments' must be a JSON object.");
        }
        if (node.size() > ActionCall.MAX_ARGUMENTS) {
            throw new InvalidModelOutputException("Action JSON contains too many arguments.");
        }
        Map<String, String> args = new LinkedHashMap<>();
        node.properties().forEach(entry -> {
            JsonNode value = entry.getValue();
            if (!value.isTextual()) {
                throw new InvalidModelOutputException("Action argument '" + entry.getKey() + "' must be a string.");
            }
            args.put(entry.getKey(), value.asText());
        });
        return Map.copyOf(args);
    }


    private ActionCall buildActionCall(String actionId, Map<String, String> arguments) {
        try {
            return new ActionCall(actionId, arguments);
        } catch (IllegalArgumentException ex) {
            throw new InvalidModelOutputException("Action proposal violates the action-call data contract: " + ex.getMessage(), ex);
        }
    }

    private void requireOnlyFields(JsonNode node, Set<String> allowed, String description) {
        for (var entry : node.properties()) {
            String field = entry.getKey();
            if (!allowed.contains(field)) {
                throw new InvalidModelOutputException("Unexpected field '" + field + "' in " + description + ".");
            }
        }
    }

    private void requireObject(JsonNode node, String message) {
        if (node == null || !node.isObject()) throw new InvalidModelOutputException(message);
    }

    private JsonNode parseJson(String raw, String description) {
        if (raw == null || raw.isBlank()) throw new InvalidModelOutputException("Empty " + description + " output.");
        if (raw.length() > 64_000) throw new InvalidModelOutputException(description + " exceeds the maximum allowed size.");
        String text = raw.trim();
        if (text.startsWith("```")) {
            int firstNewline = text.indexOf('\n');
            int closingFence = firstNewline < 0 ? -1 : text.indexOf("```", firstNewline + 1);
            if (firstNewline < 0 || closingFence < 0 || !text.substring(closingFence + 3).isBlank()
                    || text.indexOf("```", closingFence + 3) >= 0) {
                throw new InvalidModelOutputException(description + " contains an invalid fenced JSON block.");
            }
            text = text.substring(firstNewline + 1, closingFence).trim();
        }
        try {
            return mapper.readTree(text);
        } catch (JacksonException ex) {
            throw new InvalidModelOutputException("Could not parse " + description + " as strict JSON. Check the model's structured-output contract.", ex);
        }
    }
}
