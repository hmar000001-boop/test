package dev.coala.actions.workspace;

import dev.coala.core.ActionArgumentDefinition;
import dev.coala.core.ActionCall;
import dev.coala.core.ActionResult;
import dev.coala.core.ActionRisk;
import dev.coala.core.AgentContext;
import dev.coala.core.GroundingAction;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/** Read-only text search constrained to a configured root directory. No shell or file mutation is available. */
public final class ReadOnlyWorkspaceGroundingAction implements GroundingAction {
    private static final int MAX_FILES_SCANNED = 5_000;
    private static final long MAX_TOTAL_BYTES_SCANNED = 50_000_000L;
    private final Path root;
    private final int maxDepth, maxFileBytes, maxResults;
    public ReadOnlyWorkspaceGroundingAction(Path root, int maxDepth, int maxFileBytes, int maxResults) {
        if (root == null) throw new IllegalArgumentException("workspace root required");
        this.root = root.toAbsolutePath().normalize();
        if (!Files.isDirectory(this.root) || !Files.isReadable(this.root)) throw new IllegalArgumentException("workspace root must be an existing readable directory");
        if (maxDepth < 1 || maxDepth > 32 || maxFileBytes < 1 || maxFileBytes > 2_000_000 || maxResults < 1 || maxResults > 100)
            throw new IllegalArgumentException("Invalid workspace safety bounds");
        this.maxDepth = maxDepth; this.maxFileBytes = maxFileBytes; this.maxResults = maxResults;
    }
    @Override public String id() { return "search_workspace"; }
    @Override public String description() { return "Search text files beneath the configured workspace root (read-only; no shell execution)."; }
    @Override public Set<String> requiredArguments() { return Set.of("query"); }
    @Override public Set<String> optionalArguments() { return Set.of("extension", "maxResults"); }
    @Override public List<ActionArgumentDefinition> argumentDefinitions() {
        return List.of(ActionArgumentDefinition.requiredString("query", "Case-insensitive search text"),
                ActionArgumentDefinition.optionalString("extension", "Optional extension such as .java or .md"),
                new ActionArgumentDefinition("maxResults", ActionArgumentDefinition.ValueType.INTEGER, "Maximum matching lines", false, Set.of(), 3));
    }
    @Override public ActionRisk risk() { return ActionRisk.LOW; }
    @Override public boolean idempotent() { return true; }
    @Override public ActionResult execute(ActionCall call, AgentContext context) {
        String query = call.argument("query", "").toLowerCase(java.util.Locale.ROOT);
        if (query.isBlank() || query.length() > 500) throw new IllegalArgumentException("query must be 1..500 characters");
        String extension = call.argument("extension", "");
        if (!extension.isBlank() && !extension.matches("\\.[A-Za-z0-9]{1,12}")) throw new IllegalArgumentException("Invalid extension");
        int requested = Integer.parseInt(call.argument("maxResults", Integer.toString(maxResults)));
        if (requested < 1 || requested > maxResults) throw new IllegalArgumentException("maxResults must be 1.." + maxResults);
        int limit = requested;
        List<String> matches = new ArrayList<>();
        int filesScanned = 0;
        long bytesScanned = 0;
        try (Stream<Path> paths = Files.walk(root, maxDepth)) {
            var iterator = paths.filter(Files::isRegularFile).filter(p -> !Files.isSymbolicLink(p))
                    .filter(p -> extension.isBlank() || p.getFileName().toString().endsWith(extension)).iterator();
            while (iterator.hasNext() && matches.size() < limit && filesScanned < MAX_FILES_SCANNED
                    && bytesScanned < MAX_TOTAL_BYTES_SCANNED) {
                Path file = iterator.next().toAbsolutePath().normalize();
                filesScanned++;
                if (!file.startsWith(root) || Files.isSymbolicLink(file)) continue;
                long reportedSize = Files.size(file);
                if (reportedSize > maxFileBytes || reportedSize > MAX_TOTAL_BYTES_SCANNED - bytesScanned) continue;
                // Read only the remaining total budget: size checks alone can race with file changes.
                long remainingBytes = MAX_TOTAL_BYTES_SCANNED - bytesScanned;
                int readLimit = (int) Math.min((long) maxFileBytes + 1L, remainingBytes);
                if (readLimit <= 0) break;
                byte[] bytes;
                try (var input = Files.newInputStream(file)) { bytes = input.readNBytes(readLimit); }
                // Count every byte consumed, including binary or over-limit files, to enforce a real scan budget.
                bytesScanned += bytes.length;
                if (bytes.length > maxFileBytes) continue;
                boolean binary = false;
                for (byte value : bytes) if (value == 0) { binary = true; break; }
                if (binary) continue;
                String fileText = new String(bytes, StandardCharsets.UTF_8);
                String[] lines = fileText.split("\\R", -1);
                for (int i = 0; i < lines.length && matches.size() < limit; i++) {
                    if (lines[i].toLowerCase(java.util.Locale.ROOT).contains(query)) {
                        String relative = root.relativize(file).toString().replace('\\', '/');
                        matches.add(relative + ":" + (i + 1) + ": " + truncate(lines[i], 500));
                    }
                }
            }
        } catch (IOException ex) { throw new IllegalStateException("Could not read configured workspace", ex); }
        context.workingMemory().put("workspace.searchResults", matches);
        return ActionResult.continueWith("Workspace search found " + matches.size() + " matching line(s) under the configured read-only root.");
    }
    private static String truncate(String text, int max) { return text.length() <= max ? text : text.substring(0, max - 3) + "..."; }
}
