package com.tardistock.backend.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RefreshCookieOriginFilterTest {

    @Test
    void allowsConfiguredFrontendOrigin() throws Exception {
        RefreshCookieOriginFilter filter =
                new RefreshCookieOriginFilter(
                        "https://tardis-neon.vercel.app"
                );

        MockHttpServletRequest request =
                new MockHttpServletRequest(
                        "POST",
                        "/api/auth/refresh"
                );
        request.addHeader(
                "Origin",
                "https://tardis-neon.vercel.app"
        );
        MockHttpServletResponse response =
                new MockHttpServletResponse();

        filter.doFilter(
                request,
                response,
                (req, res) -> {}
        );

        assertEquals(200, response.getStatus());
    }

    @Test
    void rejectsUntrustedBrowserOrigin() throws Exception {
        RefreshCookieOriginFilter filter =
                new RefreshCookieOriginFilter(
                        "https://tardis-neon.vercel.app"
                );

        MockHttpServletRequest request =
                new MockHttpServletRequest(
                        "POST",
                        "/api/auth/logout"
                );
        request.addHeader(
                "Origin",
                "https://example.invalid"
        );
        MockHttpServletResponse response =
                new MockHttpServletResponse();

        filter.doFilter(
                request,
                response,
                (req, res) -> {}
        );

        assertEquals(403, response.getStatus());
    }

    @Test
    void rejectsUntrustedOriginForSessionRevocation() throws Exception {
        RefreshCookieOriginFilter filter =
                new RefreshCookieOriginFilter(
                        "https://tardis-neon.vercel.app"
                );

        MockHttpServletRequest request =
                new MockHttpServletRequest(
                        "POST",
                        "/api/auth/sessions/revoke-others"
                );
        request.addHeader(
                "Origin",
                "https://example.invalid"
        );
        MockHttpServletResponse response =
                new MockHttpServletResponse();

        filter.doFilter(
                request,
                response,
                (req, res) -> {}
        );

        assertEquals(403, response.getStatus());
    }

    @Test
    void allowsNonBrowserRequestWithoutOrigin() throws Exception {
        RefreshCookieOriginFilter filter =
                new RefreshCookieOriginFilter(
                        "https://tardis-neon.vercel.app"
                );

        MockHttpServletRequest request =
                new MockHttpServletRequest(
                        "POST",
                        "/api/auth/refresh"
                );
        MockHttpServletResponse response =
                new MockHttpServletResponse();

        filter.doFilter(
                request,
                response,
                (req, res) -> {}
        );

        assertEquals(200, response.getStatus());
    }
}
