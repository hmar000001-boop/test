package dev.coala.core.memory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Small durable JSON-independent file adapter for examples and single-process deployments; not a multi-process database. */
public final class FileMemoryRecordRepository implements MemoryRecordRepository {
    private final Path file;
    private final int maxRecords;
    public FileMemoryRecordRepository(Path file, int maxRecords) {
        if (file == null) throw new IllegalArgumentException("file path is required");
        if (maxRecords < 1 || maxRecords > 100_000) throw new IllegalArgumentException("maxRecords must be 1..100000");
        this.file = file.toAbsolutePath().normalize(); this.maxRecords = maxRecords;
    }
    @Override public synchronized List<MemoryRecord> findAll(int limit) {
        try {
            if (!Files.exists(file)) return List.of();
            if (Files.size(file) > 128L * 1024 * 1024) throw new IllegalStateException("Memory file exceeds 128 MiB safety limit");
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            List<MemoryRecord> records = new ArrayList<>();
            for (String line : lines) if (!line.isBlank()) records.add(decode(line));
            return records.stream().sorted(java.util.Comparator.comparing(MemoryRecord::createdAt).reversed())
                    .limit(Math.max(1, Math.min(limit, maxRecords))).toList();
        } catch (IOException ex) { throw new UncheckedIOException("Could not read memory repository", ex); }
    }
    @Override public synchronized void replaceAll(List<MemoryRecord> records) {
        if (records == null) throw new IllegalArgumentException("records are required");
        List<MemoryRecord> bounded = records.stream().sorted(java.util.Comparator.comparing(MemoryRecord::createdAt).reversed())
                .limit(maxRecords).toList();
        Path parent = file.getParent(); Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            if (parent != null) Files.createDirectories(parent);
            List<String> lines = bounded.stream().map(this::encode).toList();
            Files.write(temp, lines, StandardCharsets.UTF_8);
            try { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException ex) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
        } catch (IOException ex) { throw new UncheckedIOException("Could not persist memory repository", ex); }
        finally { try { Files.deleteIfExists(temp); } catch (IOException ignored) { } }
    }
    @Override public synchronized boolean delete(UUID id, MemoryNamespace namespace) {
        List<MemoryRecord> all = new ArrayList<>(findAll(maxRecords));
        boolean removed = all.removeIf(r -> r.id().equals(id) && namespace != null && namespace.sameAgentSession(r.namespace()));
        if (removed) replaceAll(all);
        return removed;
    }
    private String encode(MemoryRecord r) {
        List<String> fields = new ArrayList<>(List.of(r.id().toString(), r.type().name(), r.createdAt().toString(),
                r.namespace().agentId(), r.namespace().sessionId(), r.namespace().tenantId(), r.namespace().domain(),
                Double.toString(r.importance()), r.content(), Integer.toString(r.metadata().size())));
        r.metadata().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> { fields.add(e.getKey()); fields.add(e.getValue()); });
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        return "v1\t" + fields.stream().map(v -> encoder.encodeToString(v.getBytes(StandardCharsets.UTF_8))).collect(java.util.stream.Collectors.joining("\t"));
    }
    private MemoryRecord decode(String line) {
        String[] cols = line.split("\\t", -1);
        if (cols.length < 11 || !cols[0].equals("v1")) throw new IllegalStateException("Unsupported/corrupt memory row");
        try {
            Base64.Decoder decoder = Base64.getUrlDecoder();
            List<String> f = new ArrayList<>();
            for (int i = 1; i < cols.length; i++) f.add(new String(decoder.decode(cols[i]), StandardCharsets.UTF_8));
            int metadataCount = Integer.parseInt(f.get(9));
            if (metadataCount < 0 || f.size() != 10 + metadataCount * 2) throw new IllegalStateException("Corrupt memory metadata length");
            Map<String, String> metadata = new LinkedHashMap<>();
            for (int i = 0; i < metadataCount; i++) metadata.put(f.get(10 + i * 2), f.get(11 + i * 2));
            return new MemoryRecord(UUID.fromString(f.get(0)), MemoryType.valueOf(f.get(1)), f.get(8), Instant.parse(f.get(2)),
                    new MemoryNamespace(f.get(3), f.get(4), f.get(5), f.get(6)), metadata, Double.parseDouble(f.get(7)));
        } catch (RuntimeException ex) { throw new IllegalStateException("Corrupt memory row; refusing to silently discard persisted state", ex); }
    }
}
