package com.tardistock.backend.service;

import com.tardistock.backend.repository.RefreshTokenRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class RefreshTokenServiceTest {

    @Test
    void secureProductionCookieUsesSameSiteNone() {
        RefreshTokenRepository repository =
                mock(RefreshTokenRepository.class);
        RefreshTokenService service =
                new RefreshTokenService(repository, 30, true);

        ResponseCookie cookie =
                service.buildCookie("sample-refresh-token");

        String value = cookie.toString();
        assertTrue(value.contains("HttpOnly"));
        assertTrue(value.contains("Secure"));
        assertTrue(value.contains("SameSite=None"));
        assertTrue(value.contains("Path=/api/auth"));
    }

    @Test
    void localInsecureCookieFallsBackToSameSiteLax() {
        RefreshTokenRepository repository =
                mock(RefreshTokenRepository.class);
        RefreshTokenService service =
                new RefreshTokenService(repository, 30, false);

        ResponseCookie cookie =
                service.buildCookie("sample-refresh-token");

        String value = cookie.toString();
        assertTrue(value.contains("HttpOnly"));
        assertFalse(value.contains("Secure"));
        assertTrue(value.contains("SameSite=Lax"));
        assertTrue(value.contains("Path=/api/auth"));
    }

    @Test
    void clearCookieMatchesCrossSitePolicy() {
        RefreshTokenRepository repository =
                mock(RefreshTokenRepository.class);
        RefreshTokenService service =
                new RefreshTokenService(repository, 30, true);

        String value = service.clearCookie().toString();

        assertTrue(value.contains("SameSite=None"));
        assertTrue(value.contains("Secure"));
        assertTrue(value.contains("Max-Age=0"));
    }
}
