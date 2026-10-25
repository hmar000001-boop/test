package dev.coala.core.memory.retrieval;

import dev.coala.core.memory.LongTermMemory;
import dev.coala.core.memory.MemoryQuery;
import dev.coala.core.memory.MemoryRecord;
import dev.coala.core.memory.ScoredMemory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Deterministic lexical overlap baseline. It is not semantic search. */
public final class LexicalMemoryRetriever implements MemoryRetriever {
    private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{N}]+");
    @Override public List<ScoredMemory> retrieve(MemoryQuery query, LongTermMemory memory) {
        if (query == null || memory == null) throw new IllegalArgumentException("query and memory are required");
        List<MemoryRecord> records = memory.retrieve(query);
        Set<String> terms = tokens(query.text());
        List<ScoredMemory> scores = new ArrayList<>();
        for (MemoryRecord record : records) {
            if (terms.isEmpty()) {
                scores.add(new ScoredMemory(record, record.importance(), "blank query; recency/store order"));
                continue;
            }
            Set<String> contentTerms = tokens(record.content());
            long overlap = terms.stream().filter(contentTerms::contains).count();
            if (overlap == 0) continue;
            double relevance = (double) overlap / terms.size();
            boolean phrase = record.content().toLowerCase(Locale.ROOT).contains(query.text().toLowerCase(Locale.ROOT));
            double score = Math.min(1.0, relevance + (phrase ? 0.25 : 0.0));
            scores.add(new ScoredMemory(record, score, phrase ? "exact phrase and token overlap" : "token overlap"));
        }
        return scores.stream().sorted(Comparator.comparingDouble(ScoredMemory::score).reversed()
                .thenComparing(s -> s.record().createdAt(), Comparator.reverseOrder()))
                .limit(query.limit()).toList();
    }
    static Set<String> tokens(String value) {
        if (value == null || value.isBlank()) return Set.of();
        return TOKEN.matcher(value.toLowerCase(Locale.ROOT)).results().map(m -> m.group())
                .filter(s -> s.length() > 1).collect(Collectors.toSet());
    }
}
