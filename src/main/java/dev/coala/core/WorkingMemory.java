package dev.coala.core;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Current, active information shared between reasoning, retrieval and grounding.
 * Values are intended to be small textual/symbolic state, not a durable store.
 */
public final class WorkingMemory {
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final LinkedHashMap<String, Object> values = new LinkedHashMap<>();

    public void put(String key, Object value) {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("key must not be blank");
        Object safeValue = value == null ? null : freeze(value);
        lock.writeLock().lock();
        try {
            if (safeValue == null) values.remove(key);
            else values.put(key, safeValue);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Working memory is symbolic state. Defensively freeze supported collection
     * types so snapshots/getters cannot be mutated through nested object references.
     */
    private Object freeze(Object value) {
        if (value instanceof String || value instanceof Boolean || value instanceof Byte
                || value instanceof Short || value instanceof Integer || value instanceof Long
                || value instanceof Float || value instanceof Double || value instanceof BigInteger
                || value instanceof BigDecimal || value instanceof Enum<?>) {
            return value;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> frozen = new LinkedHashMap<>();
            map.forEach((key, nestedValue) -> {
                if (!(key instanceof String textKey) || textKey.isBlank()) {
                    throw new IllegalArgumentException("Working-memory map keys must be non-blank strings");
                }
                if (nestedValue == null) throw new IllegalArgumentException("Nested working-memory values must not be null");
                frozen.put(textKey, freeze(nestedValue));
            });
            return Collections.unmodifiableMap(frozen);
        }
        if (value instanceof List<?> list) {
            List<Object> frozen = new ArrayList<>(list.size());
            for (Object item : list) {
                if (item == null) throw new IllegalArgumentException("Nested working-memory values must not be null");
                frozen.add(freeze(item));
            }
            return Collections.unmodifiableList(frozen);
        }
        if (value instanceof Set<?> set) {
            Set<Object> frozen = new LinkedHashSet<>();
            for (Object item : set) {
                if (item == null) throw new IllegalArgumentException("Nested working-memory values must not be null");
                frozen.add(freeze(item));
            }
            return Collections.unmodifiableSet(frozen);
        }
        throw new IllegalArgumentException("Unsupported working-memory value type: " + value.getClass().getName());
    }

    public Optional<Object> get(String key) {
        lock.readLock().lock();
        try {
            return Optional.ofNullable(values.get(key));
        } finally {
            lock.readLock().unlock();
        }
    }

    public String getString(String key, String fallback) {
        return get(key).map(Object::toString).orElse(fallback);
    }

    public Map<String, Object> snapshot() {
        lock.readLock().lock();
        try {
            return Collections.unmodifiableMap(new LinkedHashMap<>(values));
        } finally {
            lock.readLock().unlock();
        }
    }

    public String renderForPrompt() {
        // Values are frozen on entry; format a detached snapshot without holding the lock.
        Map<String, Object> detached = snapshot();
        StringBuilder out = new StringBuilder();
        detached.forEach((key, value) -> out.append(key).append(" = ").append(value).append('\n'));
        return out.toString();
    }
}
