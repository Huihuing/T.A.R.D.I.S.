package com.tardistock.backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {

    private static final String SECRET =
            "test-secret-key-that-is-at-least-thirty-two-bytes-long";

    @Test
    void createsAndValidatesSignedToken() {
        JwtTokenProvider provider = new JwtTokenProvider(SECRET, 60_000);

        String token = provider.createToken("alice");

        assertTrue(provider.validateToken(token));
        assertEquals("alice", provider.getUsername(token));
    }

    @Test
    void rejectsTokenSignedWithDifferentSecret() {
        JwtTokenProvider issuer = new JwtTokenProvider(SECRET, 60_000);
        JwtTokenProvider verifier = new JwtTokenProvider(
                "different-test-secret-key-at-least-thirty-two-bytes", 60_000);

        String token = issuer.createToken("alice");

        assertFalse(verifier.validateToken(token));
    }

    @Test
    void rejectsTamperedToken() {
        JwtTokenProvider provider = new JwtTokenProvider(SECRET, 60_000);
        String token = provider.createToken("alice");

        String[] parts = token.split("\\.");
        String payload = new String(
                Base64.getUrlDecoder().decode(parts[1]),
                StandardCharsets.UTF_8
        ).replace("\"alice\"", "\"mallory\"");

        parts[1] = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));

        assertFalse(provider.validateToken(String.join(".", parts)));
    }

    @Test
    void rejectsExpiredToken() throws InterruptedException {
        JwtTokenProvider provider = new JwtTokenProvider(SECRET, 5);

        String token = provider.createToken("alice");
        Thread.sleep(20);

        assertFalse(provider.validateToken(token));
    }

    @Test
    void appliesConfiguredExpiration() {
        long lifetimeMs = 30_000L;
        JwtTokenProvider provider = new JwtTokenProvider(SECRET, lifetimeMs);

        String token = provider.createToken("alice");
        Claims claims = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(
                        SECRET.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertEquals(
                lifetimeMs,
                claims.getExpiration().getTime() - claims.getIssuedAt().getTime()
        );
    }

    @Test
    void rejectsShortSecretBlankSubjectAndInvalidExpiration() {
        assertThrows(IllegalStateException.class, () ->
                new JwtTokenProvider("too-short", 60_000));

        assertThrows(IllegalStateException.class, () ->
                new JwtTokenProvider(SECRET, 0));

        JwtTokenProvider provider = new JwtTokenProvider(SECRET, 60_000);
        assertThrows(IllegalArgumentException.class, () ->
                provider.createToken(" "));
    }
}
