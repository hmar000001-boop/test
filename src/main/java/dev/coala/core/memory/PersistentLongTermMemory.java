package dev.coala.core.memory;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** LongTermMemory adapter over a chosen persistent repository. File adapter is supplied; DB/vector products are not assumed. */
public final class PersistentLongTermMemory implements LongTermMemory {
    private static final Pattern WORDS = Pattern.compile("[\\p{L}\\p{N}]+");
    private final MemoryRecordRepository repository;
    private final int maxRecords;
    public PersistentLongTermMemory(MemoryRecordRepository repository, int maxRecords) {
        this.repository = Objects.requireNonNull(repository, "repository");
        if (maxRecords < 1 || maxRecords > 100_000) throw new IllegalArgumentException("maxRecords must be 1..100000");
        this.maxRecords = maxRecords;
    }
    @Override public synchronized List<MemoryRecord> retrieve(MemoryType type, String query, int limit) {
        if (type == null || limit < 1) return List.of();
        return retrieve(new MemoryQuery(query, Set.of(type), Math.min(500, Math.min(limit, maxRecords)), null, null, null));
    }
    @Override public synchronized List<MemoryRecord> retrieve(MemoryQuery query) {
        List<MemoryRecord> scoped = repository.findAll(maxRecords).stream()
                .filter(r -> query.types().contains(r.type()))
                .filter(r -> query.namespace() == null || query.namespace().sameAgentSession(r.namespace()))
                .filter(r -> query.createdAfter() == null || !r.createdAt().isBefore(query.createdAfter()))
                .filter(r -> query.createdBefore() == null || !r.createdAt().isAfter(query.createdBefore())).toList();
        if (query.text().isBlank()) return scoped.stream().sorted(Comparator.comparing(MemoryRecord::createdAt).reversed()).limit(query.limit()).toList();
        Set<String> terms = words(query.text());
        if (terms.isEmpty()) return List.of();
        record Scored(MemoryRecord record, int overlap, boolean phrase) { }
        return scoped.stream().map(r -> new Scored(r, (int) terms.stream().filter(words(r.content())::contains).count(),
                        Normalizer.normalize(r.content(), Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).contains(query.text().toLowerCase(Locale.ROOT))))
                .filter(s -> s.overlap() > 0).sorted(Comparator.comparing(Scored::phrase).reversed()
                        .thenComparing(Scored::overlap, Comparator.reverseOrder())
                        .thenComparing(s -> s.record().createdAt(), Comparator.reverseOrder()))
                .limit(query.limit()).map(Scored::record).toList();
    }
    @Override public synchronized void store(MemoryRecord record) {
        if (record == null) throw new IllegalArgumentException("record is required");
        List<MemoryRecord> all = new ArrayList<>(repository.findAll(maxRecords));
        all.removeIf(existing -> existing.id().equals(record.id())); all.add(record);
        repository.replaceAll(all);
    }
    @Override public synchronized List<MemoryRecord> list(MemoryNamespace namespace, int limit) {
        if (limit < 1) return List.of();
        return repository.findAll(maxRecords).stream()
                .filter(r -> namespace == null || namespace.sameAgentSession(r.namespace()))
                .limit(Math.min(maxRecords, limit)).toList();
    }
    @Override public synchronized boolean delete(UUID id, MemoryNamespace namespace) { return repository.delete(id, namespace); }
    private static Set<String> words(String text) {
        var matcher = WORDS.matcher(Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT));
        java.util.HashSet<String> out = new java.util.HashSet<>(); while (matcher.find()) if (matcher.group().length() > 1) out.add(matcher.group()); return out;
    }
}
