package dev.coala.core.memory;

import dev.coala.core.WorkingMemoryPromptContextBuilder;

import java.util.Set;

/** Shared defense-in-depth sanitizer for explicit memory writes; it is not a substitute for data classification. */
public final class MemoryTextSanitizer {
    private static final WorkingMemoryPromptContextBuilder PROMPT_SANITIZER = new WorkingMemoryPromptContextBuilder();
    private static final WorkingMemoryPromptContextBuilder.Request REQUEST =
            new WorkingMemoryPromptContextBuilder.Request(Set.of(), Set.of(),
                    WorkingMemoryPromptContextBuilder.defaultRedactionPatterns(), 12_000, true);

    private MemoryTextSanitizer() { }

    public static String sanitize(String value, int maxCharacters) {
        if (maxCharacters < 1) throw new IllegalArgumentException("maxCharacters must be positive");
        String safe = PROMPT_SANITIZER.sanitizeText(value, REQUEST);
        // Prevent embedded terminal/control characters from creating misleading log or prompt lines.
        safe = safe.replaceAll("[\\p{Cc}&&[^\\n\\t]]", " ").strip();
        if (safe.length() > maxCharacters) safe = safe.substring(0, Math.max(0, maxCharacters - 3)) + "...";
        return safe;
    }
}
