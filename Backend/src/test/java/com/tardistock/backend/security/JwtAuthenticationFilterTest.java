package com.tardistock.backend.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class JwtAuthenticationFilterTest {

    private static final String SECRET =
            "filter-test-secret-key-that-is-at-least-thirty-two-bytes";

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validBearerTokenPopulatesSecurityContext() throws Exception {
        JwtTokenProvider provider = new JwtTokenProvider(SECRET, 60_000);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(provider);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + provider.createToken("alice"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.doFilter(request, response, (req, res) -> {
            chainCalled.set(true);
            assertNotNull(SecurityContextHolder.getContext().getAuthentication());
            assertEquals("alice", SecurityContextHolder.getContext().getAuthentication().getName());
            assertTrue(SecurityContextHolder.getContext().getAuthentication().isAuthenticated());
        });

        assertTrue(chainCalled.get());
    }

    @Test
    void invalidBearerTokenDoesNotAuthenticateRequest() throws Exception {
        JwtTokenProvider provider = new JwtTokenProvider(SECRET, 60_000);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(provider);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer invalid.token.value");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.doFilter(request, response, (req, res) -> {
            chainCalled.set(true);
            assertNull(SecurityContextHolder.getContext().getAuthentication());
        });

        assertTrue(chainCalled.get());
    }

    @Test
    void requestWithoutBearerTokenRemainsAnonymousAtThisFilter() throws Exception {
        JwtTokenProvider provider = new JwtTokenProvider(SECRET, 60_000);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(provider);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) ->
                assertNull(SecurityContextHolder.getContext().getAuthentication()));

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
