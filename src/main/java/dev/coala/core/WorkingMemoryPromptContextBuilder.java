package dev.coala.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Builds a bounded, operation-specific view of working memory; never dumps memory without selection. */
public final class WorkingMemoryPromptContextBuilder {
    private static final List<Pattern> SENSITIVE_KEY_PATTERNS = List.of(
            Pattern.compile("(?i).*(password|secret|api.?key|access.?token|refresh.?token|authorization|credential).*") );
    private static final List<Pattern> DEFAULT_REDACTION_PATTERNS = List.of(
            Pattern.compile("(?i)(api[_ -]?key|password|secret|authorization|access[_ -]?token|refresh[_ -]?token)[\"']?\\s*[:=]\\s*[\"']?[^,;\"'}]+"),
            Pattern.compile("(?i)\\bbearer\\s+[A-Za-z0-9._~+/-]{8,}=?"),
            Pattern.compile("\\bsk-[A-Za-z0-9_-]{16,}\\b"),
            Pattern.compile("\\bAKIA[0-9A-Z]{16}\\b")
    );

    public record Request(Set<String> allowedKeys, Set<String> deniedKeys, List<Pattern> redactionPatterns,
                          int maxCharacters, boolean includeKeysInSummary) {
        public Request {
            allowedKeys = allowedKeys == null ? Set.of() : Set.copyOf(allowedKeys);
            deniedKeys = deniedKeys == null ? Set.of() : Set.copyOf(deniedKeys);
            redactionPatterns = redactionPatterns == null ? List.of() : List.copyOf(redactionPatterns);
            if (maxCharacters < 256 || maxCharacters > 64_000) throw new IllegalArgumentException("maxCharacters must be 256..64000");
        }
        public static Request defaults() {
            return new Request(Set.of("goal", "latest_observation", "latest_action", "retrieved_memories",
                    "plan.candidates", "plan.evaluations", "plan.last_rejection", "plan.previousRejectedCandidates",
                    "plan.first_steps", "reasoning.call_count", "reasoning.last_operation", "interaction.pending",
                    "interaction.answer", "reflection.latest"), Set.of(), DEFAULT_REDACTION_PATTERNS, 12_000, true);
        }
    }

    public static List<Pattern> defaultRedactionPatterns() { return DEFAULT_REDACTION_PATTERNS; }

    public record PromptContext(String rendered, List<String> includedKeys, int originalCharacters,
                                boolean truncated, int redactionsApplied) { }

    public PromptContext build(WorkingMemory memory) {
        if (memory == null) throw new IllegalArgumentException("memory is required");
        Request defaults = Request.defaults();
        java.util.Set<String> allowed = new java.util.LinkedHashSet<>(defaults.allowedKeys());
        memory.snapshot().keySet().stream()
                .filter(key -> key.startsWith("http.") || key.startsWith("workspace.") || key.startsWith("web_search.")
                        || key.startsWith("research.") || key.startsWith("travel.") || key.startsWith("support."))
                .forEach(allowed::add);
        return build(memory, new Request(allowed, defaults.deniedKeys(), defaults.redactionPatterns(), defaults.maxCharacters(), defaults.includeKeysInSummary()));
    }

    /** Applies the configured redaction patterns to a standalone prompt field (for example, the goal line). */
    public String sanitizeText(String value, Request request) {
        String sanitized = value == null ? "" : value;
        if (request == null) throw new IllegalArgumentException("request is required");
        for (Pattern pattern : request.redactionPatterns()) sanitized = pattern.matcher(sanitized).replaceAll("[REDACTED]");
        return sanitized;
    }

    public PromptContext build(WorkingMemory memory, Request request) {
        if (memory == null || request == null) throw new IllegalArgumentException("memory and request are required");
        Map<String, Object> snapshot = memory.snapshot();
        Map<String, String> selected = new LinkedHashMap<>();
        int redactions = 0;
        for (Map.Entry<String, Object> entry : snapshot.entrySet()) {
            String key = entry.getKey();
            if (!request.allowedKeys().isEmpty() && !request.allowedKeys().contains(key)) continue;
            if (request.deniedKeys().contains(key) || SENSITIVE_KEY_PATTERNS.stream().anyMatch(p -> p.matcher(key).matches())) continue;
            String value = render(entry.getValue());
            for (Pattern pattern : request.redactionPatterns()) {
                var matcher = pattern.matcher(value);
                if (matcher.find()) { value = matcher.replaceAll("[REDACTED]"); redactions++; }
            }
            selected.put(key, value);
        }
        String rendered = renderEntries(selected, request.includeKeysInSummary());
        int originalLength = rendered.length();
        boolean truncated = originalLength > request.maxCharacters();
        if (truncated) rendered = truncateAtBoundary(rendered, request.maxCharacters());
        return new PromptContext(rendered, List.copyOf(selected.keySet()), originalLength, truncated, redactions);
    }

    private static String renderEntries(Map<String, String> values, boolean includeKeys) {
        StringBuilder out = new StringBuilder();
        values.forEach((key, value) -> {
            if (out.length() > 0) out.append('\n');
            if (includeKeys) out.append(key).append(" = ");
            out.append(value.replace("\r", "\\r").replace("\n", "\\n"));
        });
        return out.toString();
    }
    private static String render(Object value) {
        if (value == null) return "null";
        if (value instanceof Map<?, ?> map) return map.toString();
        if (value instanceof List<?> list) return list.toString();
        return String.valueOf(value);
    }
    private static String truncateAtBoundary(String value, int max) {
        if (value.length() <= max) return value;
        int cutoff = Math.max(0, max - 40);
        int newline = value.lastIndexOf('\n', cutoff);
        if (newline > max / 2) cutoff = newline;
        return value.substring(0, cutoff) + "\n[WORKING MEMORY VIEW TRUNCATED]";
    }
}
