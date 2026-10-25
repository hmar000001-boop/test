package dev.coala.core.memory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;
import java.util.UUID;

/** Rule-based, in-process implementation for demos/tests; not durable or semantic search. */
public final class InMemoryLongTermMemory implements LongTermMemory {
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[\\p{L}\\p{N}]+");
    private static final Set<String> STOP_WORDS = Set.of(
            "the", "and", "for", "with", "from", "that", "this", "are", "was", "were",
            "you", "your", "into", "about", "before", "after", "has", "have", "not", "but",
            "can", "will", "then", "its", "our", "out", "all", "any", "how", "what", "when",
            "where", "who", "why", "which", "does", "did", "been", "being", "than", "there",
            "their", "they", "them", "his", "her", "she", "him", "some", "such", "more", "most");

    public static final int DEFAULT_MAX_RECORDS = 32;
    private final int maxRecords;
    private final CopyOnWriteArrayList<MemoryRecord> records = new CopyOnWriteArrayList<>();

    public InMemoryLongTermMemory() {
        this(DEFAULT_MAX_RECORDS);
    }

    public InMemoryLongTermMemory(int maxRecords) {
        if (maxRecords < 1 || maxRecords > 100_000) throw new IllegalArgumentException("maxRecords must be 1..100000");
        this.maxRecords = maxRecords;
    }

    @Override
    public List<MemoryRecord> retrieve(MemoryType type, String query, int limit) {
        if (type == null || limit < 1) return List.of();
        String normalizedQuery = query == null ? "" : query.toLowerCase(Locale.ROOT).trim();
        if (normalizedQuery.isBlank()) {
            return records.stream()
                    .filter(record -> record.type() == type)
                    .sorted(Comparator.comparing(MemoryRecord::createdAt).reversed())
                    .limit(limit)
                    .toList();
        }

        Set<String> queryTokens = meaningfulTokens(normalizedQuery);
        if (queryTokens.isEmpty()) return List.of();

        List<ScoredRecord> matches = new ArrayList<>();
        for (MemoryRecord record : records) {
            if (record.type() != type) continue;
            Set<String> recordTokens = tokens(record.content());
            long overlap = queryTokens.stream().filter(recordTokens::contains).count();
            if (overlap > 0) {
                boolean exactPhrase = record.content().toLowerCase(Locale.ROOT).contains(normalizedQuery);
                matches.add(new ScoredRecord(record, overlap, exactPhrase));
            }
        }
        matches.sort(Comparator
                .comparing(ScoredRecord::exactPhrase).reversed()
                .thenComparing(ScoredRecord::overlap, Comparator.reverseOrder())
                .thenComparing(scored -> scored.record().createdAt(), Comparator.reverseOrder()));
        return matches.stream().limit(limit).map(ScoredRecord::record).toList();
    }

    @Override
    public synchronized void store(MemoryRecord record) {
        if (record == null) throw new IllegalArgumentException("record must not be null");
        records.add(record);
        while (records.size() > maxRecords) records.remove(0);
    }

    @Override
    public List<MemoryRecord> retrieve(MemoryQuery query) {
        if (query == null) return List.of();
        List<MemoryRecord> scoped = records.stream()
                .filter(record -> query.types().contains(record.type()))
                .filter(record -> query.namespace() == null || query.namespace().sameAgentSession(record.namespace()))
                .filter(record -> query.createdAfter() == null || !record.createdAt().isBefore(query.createdAfter()))
                .filter(record -> query.createdBefore() == null || !record.createdAt().isAfter(query.createdBefore()))
                .toList();
        String normalizedQuery = query.text().toLowerCase(Locale.ROOT);
        if (normalizedQuery.isBlank()) {
            return scoped.stream().sorted(Comparator.comparing(MemoryRecord::createdAt).reversed()).limit(query.limit()).toList();
        }
        Set<String> queryTokens = meaningfulTokens(normalizedQuery);
        if (queryTokens.isEmpty()) return List.of();
        return scoped.stream().map(record -> new ScoredRecord(record,
                        queryTokens.stream().filter(tokens(record.content())::contains).count(),
                        record.content().toLowerCase(Locale.ROOT).contains(normalizedQuery)))
                .filter(scored -> scored.overlap() > 0)
                .sorted(Comparator.comparing(ScoredRecord::exactPhrase).reversed()
                        .thenComparing(ScoredRecord::overlap, Comparator.reverseOrder())
                        .thenComparing(scored -> scored.record().createdAt(), Comparator.reverseOrder()))
                .limit(query.limit()).map(ScoredRecord::record).toList();
    }

    @Override
    public List<MemoryRecord> list(MemoryNamespace namespace, int limit) {
        if (limit < 1) return List.of();
        return records.stream().filter(record -> namespace == null || namespace.sameAgentSession(record.namespace()))
                .sorted(Comparator.comparing(MemoryRecord::createdAt).reversed()).limit(limit).toList();
    }

    @Override
    public boolean delete(UUID id, MemoryNamespace namespace) {
        if (id == null || namespace == null) return false;
        return records.removeIf(record -> record.id().equals(id) && namespace.sameAgentSession(record.namespace()));
    }

    public List<MemoryRecord> allRecords() { return List.copyOf(records); }

    public int maxRecords() { return maxRecords; }

    private static Set<String> meaningfulTokens(String text) {
        Set<String> tokens = tokens(text);
        tokens.removeIf(STOP_WORDS::contains);
        return tokens;
    }

    private static Set<String> tokens(String text) {
        Set<String> result = new HashSet<>();
        var matcher = TOKEN_PATTERN.matcher(text.toLowerCase(Locale.ROOT));
        while (matcher.find()) result.add(matcher.group());
        return result;
    }

    private record ScoredRecord(MemoryRecord record, long overlap, boolean exactPhrase) {}
}
