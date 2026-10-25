package dev.coala.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Small application-shell guard for the demo API. The CoALA core does not depend
 * on HTTP authentication or rate-limiting concerns.
 */
@Component
public final class ApiGuardFilter extends OncePerRequestFilter {
    private static final long WINDOW_NANOS = 60_000_000_000L;
    private static final int MAX_RATE_LIMIT_BUCKETS = 10_000;

    private final byte[] configuredToken;
    private final int requestsPerMinute;
    private final int maxRequestBytes;
    private final ConcurrentHashMap<String, RateBucket> buckets = new ConcurrentHashMap<>();
    private final AtomicLong requestCounter = new AtomicLong();

    public ApiGuardFilter(
            @Value("${coala.api.token:}") String token,
            @Value("${server.address:127.0.0.1}") String serverAddress,
            @Value("${coala.api.rate-limit.requests-per-minute:30}") int requestsPerMinute,
            @Value("${coala.api.max-request-bytes:32768}") int maxRequestBytes) {
        if (requestsPerMinute < 1) throw new IllegalArgumentException("API rate limit must be positive");
        if (maxRequestBytes < 1 || maxRequestBytes > 1_048_576) throw new IllegalArgumentException("API maximum request size must be between 1 byte and 1 MiB");
        String normalizedAddress = serverAddress == null ? "127.0.0.1" : serverAddress.trim().toLowerCase();
        boolean loopback = normalizedAddress.equals("127.0.0.1")
                || normalizedAddress.equals("localhost")
                || normalizedAddress.equals("::1")
                || normalizedAddress.equals("0:0:0:0:0:0:0:1");
        if (!loopback && (token == null || token.isBlank())) {
            throw new IllegalStateException(
                    "Set COALA_API_TOKEN when binding the server to a non-loopback address (SERVER_ADDRESS).");
        }
        this.configuredToken = token == null || token.isBlank() ? new byte[0] : token.trim().getBytes(StandardCharsets.UTF_8);
        this.requestsPerMinute = requestsPerMinute;
        this.maxRequestBytes = maxRequestBytes;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String apiPrefix = request.getContextPath() + "/api/";
        return !request.getRequestURI().startsWith(apiPrefix);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!allowRate(request.getRemoteAddr())) {
            writeProblem(response, HttpServletResponse.SC_TOO_MANY_REQUESTS,
                    "Rate limit exceeded", "Too many API requests from this client. Retry after the current minute window.");
            return;
        }

        // Authenticate before consuming a request body. A rejected caller should not
        // be able to force the application to buffer up to the request-size limit.
        if (configuredToken.length > 0 && !validBearerToken(request.getHeader("Authorization"))) {
            response.setHeader("WWW-Authenticate", "Bearer");
            writeProblem(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "Unauthorized", "A valid bearer token is required for this API.");
            return;
        }

        HttpServletRequest boundedRequest;
        try {
            boundedRequest = enforceRequestSize(request, response);
        } catch (IOException ex) {
            writeProblem(response, HttpServletResponse.SC_BAD_REQUEST,
                    "Invalid request body", "The request body could not be read.");
            return;
        }
        if (boundedRequest == null) return;
        filterChain.doFilter(boundedRequest, response);
    }

    private boolean validBearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) return false;
        byte[] supplied = authorization.substring(7).trim().getBytes(StandardCharsets.UTF_8);
        return supplied.length > 0 && MessageDigest.isEqual(configuredToken, supplied);
    }

    private boolean allowRate(String remoteAddress) {
        String key = remoteAddress == null || remoteAddress.isBlank() ? "unknown" : remoteAddress;
        long now = System.nanoTime();
        AtomicBoolean accepted = new AtomicBoolean(false);
        buckets.compute(key, (ignored, current) -> {
            if (current == null && buckets.size() >= MAX_RATE_LIMIT_BUCKETS) {
                accepted.set(false);
                return null;
            }
            if (current == null || now - current.windowStartedAt >= WINDOW_NANOS) {
                accepted.set(true);
                return new RateBucket(now, 1);
            }
            if (current.requests >= requestsPerMinute) return current;
            accepted.set(true);
            return new RateBucket(current.windowStartedAt, current.requests + 1);
        });

        if (requestCounter.incrementAndGet() % 256 == 0 || buckets.size() > MAX_RATE_LIMIT_BUCKETS) {
            pruneRateBuckets(now);
        }
        return accepted.get();
    }

    private HttpServletRequest enforceRequestSize(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String method = request.getMethod();
        boolean mayHaveBody = "POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)
                || "PATCH".equalsIgnoreCase(method);
        if (!mayHaveBody) return request;
        if (request.getContentLengthLong() > maxRequestBytes) {
            writeProblem(response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                    "Request too large", "The request body exceeds the configured size limit.");
            return null;
        }
        byte[] body = request.getInputStream().readNBytes(maxRequestBytes + 1);
        if (body.length > maxRequestBytes) {
            writeProblem(response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                    "Request too large", "The request body exceeds the configured size limit.");
            return null;
        }
        return new CachedBodyRequestWrapper(request, body);
    }

    private void pruneRateBuckets(long now) {
        Iterator<Map.Entry<String, RateBucket>> iterator = buckets.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, RateBucket> entry = iterator.next();
            if (now - entry.getValue().windowStartedAt >= WINDOW_NANOS) {
                buckets.remove(entry.getKey(), entry.getValue());
            }
        }
    }

    private void writeProblem(HttpServletResponse response, int status, String title, String detail) throws IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"type\":\"about:blank\",\"status\":" + status
                + ",\"title\":\"" + title + "\",\"detail\":\"" + detail + "\"}");
    }

    private record RateBucket(long windowStartedAt, int requests) {}

    private static final class CachedBodyRequestWrapper extends HttpServletRequestWrapper {
        private final byte[] body;

        private CachedBodyRequestWrapper(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override public int read() { return input.read(); }
                @Override public boolean isFinished() { return input.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener readListener) {
                    throw new IllegalStateException("Asynchronous request-body reading is not supported by this wrapper.");
                }
            };
        }

        @Override
        public BufferedReader getReader() throws IOException {
            String encoding = getCharacterEncoding();
            return new BufferedReader(new InputStreamReader(getInputStream(),
                    encoding == null ? StandardCharsets.UTF_8 : java.nio.charset.Charset.forName(encoding)));
        }
    }
}
