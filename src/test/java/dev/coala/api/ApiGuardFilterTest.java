package dev.coala.api;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

class ApiGuardFilterTest {
    @Test
    void tokenIsRequiredWhenConfigured() throws Exception {
        ApiGuardFilter filter = new ApiGuardFilter("secret", "127.0.0.1", 10, 1024);
        MockHttpServletRequest request = request();
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        assertEquals(401, response.getStatus());
        assertEquals("Bearer", response.getHeader("WWW-Authenticate"));
    }

    @Test
    void validTokenAllowsRequest() throws Exception {
        ApiGuardFilter filter = new ApiGuardFilter("secret", "127.0.0.1", 10, 1024);
        MockHttpServletRequest request = request();
        request.addHeader("Authorization", "Bearer secret");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        assertEquals(200, response.getStatus());
    }

    @Test
    void rateLimitReturns429() throws Exception {
        ApiGuardFilter filter = new ApiGuardFilter("", "127.0.0.1", 1, 1024);
        MockHttpServletResponse firstResponse = new MockHttpServletResponse();
        filter.doFilter(request(), firstResponse, new MockFilterChain());
        assertEquals(200, firstResponse.getStatus());

        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        filter.doFilter(request(), secondResponse, new MockFilterChain());
        assertEquals(429, secondResponse.getStatus());
    }

    @Test
    void publicBindRequiresApiToken() {
        assertThrows(IllegalStateException.class,
                () -> new ApiGuardFilter("", "0.0.0.0", 30, 32768));
    }

    @Test
    void oversizedRequestIsRejected() throws Exception {
        ApiGuardFilter filter = new ApiGuardFilter("", "127.0.0.1", 10, 4);
        MockHttpServletRequest request = request();
        request.setMethod("POST");
        request.setContent("12345".getBytes());
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        assertEquals(413, response.getStatus());
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/use-cases");
        request.setMethod("GET");
        request.setRemoteAddr("127.0.0.1");
        return request;
    }
}
