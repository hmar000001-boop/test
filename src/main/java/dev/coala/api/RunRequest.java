package dev.coala.api;

public record RunRequest(String goal, Integer maxCycles, String sessionId) {
    public RunRequest {
        if (goal == null || goal.isBlank()) throw new IllegalArgumentException("goal must not be blank");
        if (goal.length() > 4_000) throw new IllegalArgumentException("goal must be no more than 4000 characters");
        if (maxCycles != null && (maxCycles < 1 || maxCycles > 100)) {
            throw new IllegalArgumentException("maxCycles must be between 1 and 100");
        }
        if (sessionId != null && !sessionId.isBlank()
                && !sessionId.trim().matches("[A-Za-z0-9._-]{1,64}")) {
            throw new IllegalArgumentException("sessionId must be 1-64 characters using letters, digits, '.', '_' or '-'.");
        }
    }
}
