package dev.coala.actions.http;

import dev.coala.core.ActionArgumentDefinition;
import dev.coala.core.ActionCall;
import dev.coala.core.ActionResult;
import dev.coala.core.ActionRisk;
import dev.coala.core.AgentContext;
import dev.coala.core.GroundingAction;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/** Fixed-endpoint JSON HTTP adapter. The model can choose only the registered action and declared arguments, never a URL/host. */
public final class JsonHttpGroundingAction implements GroundingAction {
    public enum Method { GET, POST, PUT, PATCH, DELETE }
    private final String id, description;
    private final URI endpoint;
    private final Method method;
    private final List<ActionArgumentDefinition> argumentDefinitions;
    private final Set<String> required;
    private final ActionRisk risk;
    private final boolean idempotent;
    private final HttpClient client;
    private final Duration timeout;
    private final int maxResponseBytes;
    private final Supplier<Map<String, String>> headersSupplier;
    private final ObjectMapper mapper = new ObjectMapper();

    public JsonHttpGroundingAction(String id, String description, URI endpoint, Method method,
                                   List<ActionArgumentDefinition> arguments, ActionRisk risk, boolean idempotent,
                                   Duration timeout, int maxResponseBytes, Supplier<Map<String, String>> headersSupplier) {
        if (id == null || !id.matches("[A-Za-z][A-Za-z0-9_.-]{0,99}") || description == null || description.isBlank()) throw new IllegalArgumentException("id must be a safe action identifier and description is required");
        this.endpoint = validateEndpoint(endpoint); this.id = id; this.description = description; this.method = java.util.Objects.requireNonNull(method);
        this.argumentDefinitions = List.copyOf(arguments == null ? List.of() : arguments);
        this.required = this.argumentDefinitions.stream().filter(ActionArgumentDefinition::required).map(ActionArgumentDefinition::name).collect(java.util.stream.Collectors.toUnmodifiableSet());
        ActionRisk configuredRisk = risk == null ? (method == Method.GET ? ActionRisk.LOW : ActionRisk.HIGH) : risk;
        this.risk = method == Method.GET ? configuredRisk : (configuredRisk.ordinal() < ActionRisk.HIGH.ordinal() ? ActionRisk.HIGH : configuredRisk);
        this.idempotent = idempotent;
        if (timeout == null || timeout.isNegative() || timeout.isZero() || timeout.compareTo(Duration.ofMinutes(2)) > 0) throw new IllegalArgumentException("timeout must be positive and <= 2 minutes");
        if (maxResponseBytes < 256 || maxResponseBytes > 64_000) throw new IllegalArgumentException("maxResponseBytes must be 256..64000 so response storage remains bounded");
        this.timeout = timeout; this.maxResponseBytes = maxResponseBytes;
        this.headersSupplier = headersSupplier == null ? Map::of : headersSupplier;
        this.client = HttpClient.newBuilder().connectTimeout(timeout).followRedirects(HttpClient.Redirect.NEVER).build();
    }
    private static URI validateEndpoint(URI endpoint) {
        if (endpoint == null || !endpoint.isAbsolute() || endpoint.getHost() == null || endpoint.getUserInfo() != null || endpoint.getFragment() != null)
            throw new IllegalArgumentException("Endpoint must be an absolute URI with host and without user-info/fragment");
        String scheme = endpoint.getScheme().toLowerCase(java.util.Locale.ROOT);
        boolean loopback = endpoint.getHost().equalsIgnoreCase("localhost") || endpoint.getHost().equals("127.0.0.1") || endpoint.getHost().equals("::1");
        if (!scheme.equals("https") && !(scheme.equals("http") && loopback)) throw new IllegalArgumentException("HTTP adapter requires HTTPS; plain HTTP is permitted only for loopback test servers");
        return endpoint;
    }
    @Override public String id() { return id; }
    @Override public String description() { return description; }
    @Override public ActionRisk risk() { return risk; }
    @Override public boolean idempotent() { return idempotent; }
    @Override public Set<String> requiredArguments() { return required; }
    @Override public Set<String> optionalArguments() { return argumentDefinitions.stream().filter(a -> !a.required()).map(ActionArgumentDefinition::name).collect(java.util.stream.Collectors.toUnmodifiableSet()); }
    @Override public List<ActionArgumentDefinition> argumentDefinitions() { return argumentDefinitions; }
    @Override public ActionResult execute(ActionCall call, AgentContext context) {
        validate(call);
        try {
            URI target = method == Method.GET || method == Method.DELETE ? withQuery(endpoint, call.arguments()) : endpoint;
            HttpRequest.Builder builder = HttpRequest.newBuilder(target).timeout(timeout).header("Accept", "application/json");
            Map<String, String> supplied = headersSupplier.get();
            if (supplied != null) supplied.forEach((key, value) -> {
                if (key == null || value == null || key.equalsIgnoreCase("Host") || key.equalsIgnoreCase("Content-Length") || key.equalsIgnoreCase("Connection")) throw new IllegalArgumentException("Invalid configured HTTP header");
                builder.header(key, value);
            });
            switch (method) {
                case GET -> builder.GET();
                case DELETE -> builder.DELETE();
                case POST, PUT, PATCH -> {
                    builder.header("Content-Type", "application/json");
                    builder.method(method.name(), HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(call.arguments())));
                }
            }
            HttpResponse<InputStream> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream stream = response.body()) {
                byte[] bytes = stream.readNBytes(maxResponseBytes + 1);
                if (bytes.length > maxResponseBytes) throw new IllegalStateException("HTTP response exceeds configured size limit");
                String body = new String(bytes, StandardCharsets.UTF_8);
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    // Do not copy provider error bodies into traces/observations: they can contain secrets or PII.
                    throw new IllegalStateException("Configured HTTP endpoint returned status " + response.statusCode());
                }
                String contentType = response.headers().firstValue("content-type").orElse("");
                if (!contentType.toLowerCase(java.util.Locale.ROOT).contains("application/json")) throw new IllegalStateException("Configured HTTP endpoint did not return application/json");
                try { mapper.readTree(body); }
                catch (tools.jackson.core.JacksonException ex) { throw new IllegalStateException("Configured endpoint returned invalid JSON", ex); }
                context.workingMemory().put("http." + id + ".status", response.statusCode());
                context.workingMemory().put("http." + id + ".responseJson", body);
                return ActionResult.continueWith("HTTP action " + id + " succeeded; bounded JSON response stored in working memory under http." + id + ".responseJson (" + bytes.length + " bytes). The body is not copied into the execution trace.");
            }
        } catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException("HTTP action interrupted", ex); }
        catch (java.io.IOException ex) { throw new IllegalStateException("HTTP action I/O failure", ex); }
    }
    private URI withQuery(URI base, Map<String, String> args) {
        if (args.isEmpty()) return base;
        String params = args.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(e -> enc(e.getKey()) + "=" + enc(e.getValue())).collect(java.util.stream.Collectors.joining("&"));
        String uri = base.toString() + (base.getQuery() == null ? "?" : "&") + params;
        URI result = URI.create(uri);
        if (!result.getHost().equalsIgnoreCase(endpoint.getHost()) || !result.getScheme().equalsIgnoreCase(endpoint.getScheme())) throw new IllegalStateException("Endpoint origin changed");
        return result;
    }
    private static String enc(String v) { return URLEncoder.encode(v, StandardCharsets.UTF_8); }
}
