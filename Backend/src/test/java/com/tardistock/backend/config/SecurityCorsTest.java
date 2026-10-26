package com.tardistock.backend.config;

import com.tardistock.backend.security.JwtAuthenticationFilter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class SecurityCorsTest {
    @Test
    void configuredFrontendCanSendIdempotencyKeyWithCredentials() {
        var source = new SecurityConfig(mock(JwtAuthenticationFilter.class),
                "https://tardis-neon.vercel.app").corsConfigurationSource();
        var cors = source.getCorsConfiguration(new MockHttpServletRequest("OPTIONS", "/api/trade/buy"));
        assertNotNull(cors);
        assertEquals("https://tardis-neon.vercel.app", cors.checkOrigin("https://tardis-neon.vercel.app"));
        assertEquals(List.of("Authorization", "Idempotency-Key"),
                cors.checkHeaders(List.of("Authorization", "Idempotency-Key")));
        assertEquals(Boolean.TRUE, cors.getAllowCredentials());
        assertNull(cors.checkOrigin("https://untrusted.example"));
    }
}
