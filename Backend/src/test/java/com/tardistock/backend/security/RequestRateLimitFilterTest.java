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
    void limitsRepeatedGoogleLoginRequests() throws Exception {
        RequestRateLimitFilter filter = new RequestRateLimitFilter();

        for (int i = 0; i < 20; i++) {
            MockHttpServletRequest request =
                    new MockHttpServletRequest("POST", "/api/auth/google");
            request.setRemoteAddr("203.0.113.20");
            MockHttpServletResponse response =
                    new MockHttpServletResponse();

            filter.doFilter(request, response, (req, res) -> {});
            assertEquals(200, response.getStatus());
        }

        MockHttpServletRequest blocked =
                new MockHttpServletRequest("POST", "/api/auth/google");
        blocked.setRemoteAddr("203.0.113.20");
        MockHttpServletResponse blockedResponse =
                new MockHttpServletResponse();

        filter.doFilter(blocked, blockedResponse, (req, res) -> {});
        assertEquals(429, blockedResponse.getStatus());
    }

    @Test
    void limitsRepeatedRefreshRequests() throws Exception {
        RequestRateLimitFilter filter = new RequestRateLimitFilter();

        for (int i = 0; i < 60; i++) {
            MockHttpServletRequest request =
                    new MockHttpServletRequest("POST", "/api/auth/refresh");
            request.setRemoteAddr("203.0.113.21");
            MockHttpServletResponse response =
                    new MockHttpServletResponse();

            filter.doFilter(request, response, (req, res) -> {});
            assertEquals(200, response.getStatus());
        }

        MockHttpServletRequest blocked =
                new MockHttpServletRequest("POST", "/api/auth/refresh");
        blocked.setRemoteAddr("203.0.113.21");
        MockHttpServletResponse blockedResponse =
                new MockHttpServletResponse();

        filter.doFilter(blocked, blockedResponse, (req, res) -> {});
        assertEquals(429, blockedResponse.getStatus());
    }

    @Test
    void limitsRepeatedCommunityReports() throws Exception {
        RequestRateLimitFilter filter = new RequestRateLimitFilter();

        for (int i = 0; i < 10; i++) {
            MockHttpServletRequest request =
                    new MockHttpServletRequest("POST", "/api/board/reports");
            request.setRemoteAddr("203.0.113.30");
            MockHttpServletResponse response =
                    new MockHttpServletResponse();

            filter.doFilter(request, response, (req, res) -> {});
            assertEquals(200, response.getStatus());
        }

        MockHttpServletRequest blocked =
                new MockHttpServletRequest("POST", "/api/board/reports");
        blocked.setRemoteAddr("203.0.113.30");
        MockHttpServletResponse blockedResponse =
                new MockHttpServletResponse();

        filter.doFilter(blocked, blockedResponse, (req, res) -> {});

        assertEquals(429, blockedResponse.getStatus());
    }

    @Test
    void limitsRepeatedAccountPasswordChanges() throws Exception {
        RequestRateLimitFilter filter = new RequestRateLimitFilter();

        for (int i = 0; i < 10; i++) {
            MockHttpServletRequest request =
                    new MockHttpServletRequest(
                            "POST",
                            "/api/account/password/change"
                    );
            request.setRemoteAddr("203.0.113.40");
            MockHttpServletResponse response =
                    new MockHttpServletResponse();

            filter.doFilter(request, response, (req, res) -> {});
            assertEquals(200, response.getStatus());
        }

        MockHttpServletRequest blocked =
                new MockHttpServletRequest(
                        "POST",
                        "/api/account/password/change"
                );
        blocked.setRemoteAddr("203.0.113.40");
        MockHttpServletResponse blockedResponse =
                new MockHttpServletResponse();

        filter.doFilter(blocked, blockedResponse, (req, res) -> {});

        assertEquals(429, blockedResponse.getStatus());
    }

    @Test
    void limitsRepeatedSecurityCodeRequests() throws Exception {
        RequestRateLimitFilter filter = new RequestRateLimitFilter();

        for (int i = 0; i < 5; i++) {
            MockHttpServletRequest request =
                    new MockHttpServletRequest(
                            "POST",
                            "/api/account/security-code/send"
                    );
            request.setRemoteAddr("203.0.113.50");
            MockHttpServletResponse response =
                    new MockHttpServletResponse();

            filter.doFilter(request, response, (req, res) -> {});
            assertEquals(200, response.getStatus());
        }

        MockHttpServletRequest blocked =
                new MockHttpServletRequest(
                        "POST",
                        "/api/account/security-code/send"
                );
        blocked.setRemoteAddr("203.0.113.50");
        MockHttpServletResponse blockedResponse =
                new MockHttpServletResponse();

        filter.doFilter(blocked, blockedResponse, (req, res) -> {});

        assertEquals(429, blockedResponse.getStatus());
    }

    @Test
    void limitsRepeatedPinResetRequests() throws Exception {
        RequestRateLimitFilter filter = new RequestRateLimitFilter();

        for (int i = 0; i < 5; i++) {
            MockHttpServletRequest request =
                    new MockHttpServletRequest(
                            "POST",
                            "/api/account/pin/reset"
                    );
            request.setRemoteAddr("203.0.113.51");
            MockHttpServletResponse response =
                    new MockHttpServletResponse();

            filter.doFilter(request, response, (req, res) -> {});
            assertEquals(200, response.getStatus());
        }

        MockHttpServletRequest blocked =
                new MockHttpServletRequest(
                        "POST",
                        "/api/account/pin/reset"
                );
        blocked.setRemoteAddr("203.0.113.51");
        MockHttpServletResponse blockedResponse =
                new MockHttpServletResponse();

        filter.doFilter(blocked, blockedResponse, (req, res) -> {});

        assertEquals(429, blockedResponse.getStatus());
    }

    @Test
    void limitsRepeatedSessionRevocationRequests() throws Exception {
        RequestRateLimitFilter filter = new RequestRateLimitFilter();

        for (int i = 0; i < 5; i++) {
            MockHttpServletRequest request =
                    new MockHttpServletRequest(
                            "POST",
                            "/api/auth/sessions/revoke-others"
                    );
            request.setRemoteAddr("203.0.113.52");
            MockHttpServletResponse response =
                    new MockHttpServletResponse();

            filter.doFilter(request, response, (req, res) -> {});
            assertEquals(200, response.getStatus());
        }

        MockHttpServletRequest blocked =
                new MockHttpServletRequest(
                        "POST",
                        "/api/auth/sessions/revoke-others"
                );
        blocked.setRemoteAddr("203.0.113.52");
        MockHttpServletResponse blockedResponse =
                new MockHttpServletResponse();

        filter.doFilter(blocked, blockedResponse, (req, res) -> {});

        assertEquals(429, blockedResponse.getStatus());
    }

    @Test
    void limitsRepeatedCspReports() throws Exception {
        RequestRateLimitFilter filter = new RequestRateLimitFilter();

        for (int i = 0; i < 30; i++) {
            MockHttpServletRequest request =
                    new MockHttpServletRequest(
                            "POST",
                            "/api/security/csp-report"
                    );
            request.setRemoteAddr("203.0.113.60");
            MockHttpServletResponse response =
                    new MockHttpServletResponse();

            filter.doFilter(request, response, (req, res) -> {});
            assertEquals(200, response.getStatus());
        }

        MockHttpServletRequest blocked =
                new MockHttpServletRequest(
                        "POST",
                        "/api/security/csp-report"
                );
        blocked.setRemoteAddr("203.0.113.60");
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
