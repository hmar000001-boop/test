package dev.coala.core.memory;

import java.util.List;
import java.util.UUID;

/** Persistence port for a durable memory adapter; database/vendor APIs stay outside the CoALA core. */
public interface MemoryRecordRepository {
    List<MemoryRecord> findAll(int limit);
    void replaceAll(List<MemoryRecord> records);
    boolean delete(UUID id, MemoryNamespace namespace);
}
