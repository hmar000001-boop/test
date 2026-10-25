package dev.coala.core.memory;

import java.time.Instant;

/** Explicit rule deciding whether a stored record should be deleted; no automatic deletion by default. */
@FunctionalInterface
public interface MemoryDeletionPolicy {
    boolean shouldDelete(MemoryRecord record, Instant now);
}
