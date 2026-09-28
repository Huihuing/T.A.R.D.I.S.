package com.tardistock.backend.security;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TrustedProxyHeaderFilterTest {

    @Test
    void trustedProxyUsesVercelSanitizedForwardedIp() throws Exception {
        TrustedProxyHeaderFilter filter =
                new TrustedProxyHeaderFilter("shared-secret");
        MockHttpServletRequest request = request();
        request.addHeader(
                TrustedProxyHeaderFilter.PROXY_SECRET_HEADER,
                "shared-secret"
        );
        request.addHeader(
                TrustedProxyHeaderFilter.FORWARDED_HEADER,
                "203.0.113.9, 10.0.0.1"
        );
        request.addHeader(
                TrustedProxyHeaderFilter.CLOUDFLARE_IP_HEADER,
                "198.51.100.7"
        );

        HttpServletRequest sanitized = run(filter, request);

        assertEquals(
                "203.0.113.9",
                sanitized.getHeader(
                        TrustedProxyHeaderFilter.FORWARDED_HEADER)
        );
    }

    @Test
    void untrustedDirectRequestIgnoresSpoofedForwardedIp() throws Exception {
        TrustedProxyHeaderFilter filter =
                new TrustedProxyHeaderFilter("shared-secret");
        MockHttpServletRequest request = request();
        request.addHeader(
                TrustedProxyHeaderFilter.PROXY_SECRET_HEADER,
                "wrong-secret"
        );
        request.addHeader(
                TrustedProxyHeaderFilter.FORWARDED_HEADER,
                "203.0.113.9"
        );
        request.addHeader(
                TrustedProxyHeaderFilter.CLOUDFLARE_IP_HEADER,
                "198.51.100.7"
        );

        HttpServletRequest sanitized = run(filter, request);

        assertEquals(
                "198.51.100.7",
                sanitized.getHeader(
                        TrustedProxyHeaderFilter.FORWARDED_HEADER)
        );
    }

    @Test
    void directRequestFallsBackToSocketPeerWhenCloudflareIpInvalid()
            throws Exception {
        TrustedProxyHeaderFilter filter =
                new TrustedProxyHeaderFilter("shared-secret");
        MockHttpServletRequest request = request();
        request.addHeader(
                TrustedProxyHeaderFilter.FORWARDED_HEADER,
                "203.0.113.9"
        );
        request.addHeader(
                TrustedProxyHeaderFilter.CLOUDFLARE_IP_HEADER,
                "not-an-ip"
        );

        HttpServletRequest sanitized = run(filter, request);

        assertEquals(
                "192.0.2.44",
                sanitized.getHeader(
                        TrustedProxyHeaderFilter.FORWARDED_HEADER)
        );
    }

    @Test
    void missingConfiguredSecretLeavesExistingTrafficUntouched()
            throws Exception {
        TrustedProxyHeaderFilter filter =
                new TrustedProxyHeaderFilter("");
        MockHttpServletRequest request = request();
        request.addHeader(
                TrustedProxyHeaderFilter.FORWARDED_HEADER,
                "203.0.113.9"
        );

        HttpServletRequest passedThrough = run(filter, request);

        assertEquals(
                "203.0.113.9",
                passedThrough.getHeader(
                        TrustedProxyHeaderFilter.FORWARDED_HEADER)
        );
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.0.2.44");
        return request;
    }

    private HttpServletRequest run(
            TrustedProxyHeaderFilter filter,
            MockHttpServletRequest request) throws Exception {
        AtomicReference<HttpServletRequest> captured =
                new AtomicReference<>();

        filter.doFilter(
                request,
                new MockHttpServletResponse(),
                (servletRequest, servletResponse) ->
                        captured.set((HttpServletRequest) servletRequest)
        );

        return captured.get();
    }
}
