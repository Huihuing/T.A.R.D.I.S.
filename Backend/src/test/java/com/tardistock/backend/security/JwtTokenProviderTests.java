package com.tardistock.backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTests {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    @Test
    void createsAndValidatesTokenWithExpectedSubject() {
        JwtTokenProvider provider = new JwtTokenProvider(SECRET, 60_000L);

        String token = provider.createToken("alice");

        assertTrue(provider.validateToken(token));
        assertEquals("alice", provider.getUsername(token));
    }

    @Test
    void rejectsBlankSubject() {
        JwtTokenProvider provider = new JwtTokenProvider(SECRET, 60_000L);

        assertThrows(IllegalArgumentException.class, () -> provider.createToken(" "));
    }

    @Test
    void rejectsTamperedToken() {
        JwtTokenProvider provider = new JwtTokenProvider(SECRET, 60_000L);
        String token = provider.createToken("alice");

        String[] parts = token.split("\\.");
        byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
        String payload = new String(payloadBytes, StandardCharsets.UTF_8)
                .replace("\"alice\"", "\"mallory\"");
        parts[1] = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));

        String tampered = String.join(".", parts);

        assertFalse(provider.validateToken(tampered));
    }

    @Test
    void rejectsExpiredToken() throws InterruptedException {
        JwtTokenProvider provider = new JwtTokenProvider(SECRET, 5L);

        String token = provider.createToken("alice");
        Thread.sleep(20L);

        assertFalse(provider.validateToken(token));
    }

    @Test
    void expirationConfigurationIsApplied() {
        long configuredLifetimeMs = 30_000L;
        JwtTokenProvider provider = new JwtTokenProvider(SECRET, configuredLifetimeMs);

        String token = provider.createToken("alice");
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(SECRET.getBytes(StandardCharsets.UTF_8))
                .build()
                .parseClaimsJws(token)
                .getBody();

        long actualLifetimeMs = claims.getExpiration().getTime() - claims.getIssuedAt().getTime();

        assertEquals(configuredLifetimeMs, actualLifetimeMs);
    }
}
