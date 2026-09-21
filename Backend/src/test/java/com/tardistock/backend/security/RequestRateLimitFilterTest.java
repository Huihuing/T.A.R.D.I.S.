package com.tardistock.backend.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RequestRateLimitFilterTest {

    @Test
    void limitsRepeatedLoginRequestsFromSameIp() throws Exception {
        RequestRateLimitFilter filter = new RequestRateLimitFilter();

        for (int i = 0; i < 10; i++) {
            MockHttpServletRequest request =
                    new MockHttpServletRequest("POST", "/api/auth/login");
            request.setRemoteAddr("203.0.113.10");
            MockHttpServletResponse response =
                    new MockHttpServletResponse();

            filter.doFilter(request, response, (req, res) -> {});
            assertEquals(200, response.getStatus());
        }

        MockHttpServletRequest blocked =
                new MockHttpServletRequest("POST", "/api/auth/login");
        blocked.setRemoteAddr("203.0.113.10");
        MockHttpServletResponse blockedResponse =
                new MockHttpServletResponse();

        filter.doFilter(blocked, blockedResponse, (req, res) -> {});

        assertEquals(429, blockedResponse.getStatus());
    }

    @Test
    void countersAreSeparatedByClientIp() throws Exception {
        RequestRateLimitFilter filter = new RequestRateLimitFilter();

        for (int i = 0; i < 10; i++) {
            MockHttpServletRequest request =
                    new MockHttpServletRequest("POST", "/api/auth/login");
            request.setRemoteAddr("203.0.113.11");
            filter.doFilter(
                    request,
                    new MockHttpServletResponse(),
                    (req, res) -> {}
            );
        }

        MockHttpServletRequest otherClient =
                new MockHttpServletRequest("POST", "/api/auth/login");
        otherClient.setRemoteAddr("203.0.113.12");
        MockHttpServletResponse response =
                new MockHttpServletResponse();

        filter.doFilter(otherClient, response, (req, res) -> {});

        assertEquals(200, response.getStatus());
    }
}
