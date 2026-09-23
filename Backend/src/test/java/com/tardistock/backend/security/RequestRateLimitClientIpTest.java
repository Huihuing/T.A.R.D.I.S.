package com.tardistock.backend.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RequestRateLimitClientIpTest {

    @Test
    void rateLimitsRepeatedLoginRequestsFromSameForwardedIpv4() throws Exception {
        RequestRateLimitFilter filter = new RequestRateLimitFilter();

        for (int i = 0; i < 10; i++) {
            assertEquals(
                    200,
                    invokeLogin(filter, "198.51.100.10", "10.0.0.10").getStatus()
            );
        }

        MockHttpServletResponse limited =
                invokeLogin(filter, "198.51.100.10", "10.0.0.10");

        assertEquals(429, limited.getStatus());
        assertEquals("60", limited.getHeader("Retry-After"));
    }

    @Test
    void keepsDifferentValidForwardedIpsInSeparateBuckets() throws Exception {
        RequestRateLimitFilter filter = new RequestRateLimitFilter();

        for (int i = 0; i < 10; i++) {
            assertEquals(
                    200,
                    invokeLogin(filter, "198.51.100.20", "10.0.0.10").getStatus()
            );
        }

        assertEquals(
                200,
                invokeLogin(filter, "198.51.100.21", "10.0.0.10").getStatus()
        );
    }

    @Test
    void invalidForwardedValuesFallBackToRemoteAddress() throws Exception {
        RequestRateLimitFilter filter = new RequestRateLimitFilter();

        for (int i = 0; i < 10; i++) {
            assertEquals(
                    200,
                    invokeLogin(
                            filter,
                            "not-an-ip-" + i,
                            "203.0.113.25"
                    ).getStatus()
            );
        }

        assertEquals(
                429,
                invokeLogin(
                        filter,
                        "still-not-an-ip",
                        "203.0.113.25"
                ).getStatus()
        );
    }

    @Test
    void oversizedForwardedValuesFallBackToRemoteAddress() throws Exception {
        RequestRateLimitFilter filter = new RequestRateLimitFilter();

        for (int i = 0; i < 10; i++) {
            assertEquals(
                    200,
                    invokeLogin(
                            filter,
                            "1".repeat(60) + i,
                            "203.0.113.26"
                    ).getStatus()
            );
        }

        assertEquals(
                429,
                invokeLogin(
                        filter,
                        "2".repeat(60),
                        "203.0.113.26"
                ).getStatus()
        );
    }

    @Test
    void equivalentIpv6FormsShareOneBucket() throws Exception {
        RequestRateLimitFilter filter = new RequestRateLimitFilter();

        for (int i = 0; i < 5; i++) {
            assertEquals(
                    200,
                    invokeLogin(
                            filter,
                            "2001:0db8:0:0:0:0:0:1",
                            "10.0.0.10"
                    ).getStatus()
            );
            assertEquals(
                    200,
                    invokeLogin(
                            filter,
                            "2001:db8::1",
                            "10.0.0.10"
                    ).getStatus()
            );
        }

        assertEquals(
                429,
                invokeLogin(filter, "2001:db8::1", "10.0.0.10").getStatus()
        );
    }

    @Test
    void usesFirstForwardedAddressFromProxyChain() throws Exception {
        RequestRateLimitFilter filter = new RequestRateLimitFilter();

        for (int i = 0; i < 10; i++) {
            assertEquals(
                    200,
                    invokeLogin(
                            filter,
                            "198.51.100.30, 192.0.2.10",
                            "10.0.0.10"
                    ).getStatus()
            );
        }

        assertEquals(
                429,
                invokeLogin(
                        filter,
                        "198.51.100.30, 192.0.2.11",
                        "10.0.0.10"
                ).getStatus()
        );
    }

    private MockHttpServletResponse invokeLogin(
            RequestRateLimitFilter filter,
            String forwardedFor,
            String remoteAddress) throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr(remoteAddress);
        if (forwardedFor != null) {
            request.addHeader("X-Forwarded-For", forwardedFor);
        }

        MockHttpServletResponse response =
                new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> {});
        return response;
    }
}
