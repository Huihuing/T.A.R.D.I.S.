package com.tardistock.backend.security;

import org.junit.jupiter.api.Test;

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
    void rejectsExpiredToken() throws InterruptedException {
        JwtTokenProvider provider = new JwtTokenProvider(SECRET, 5);

        String token = provider.createToken("alice");
        Thread.sleep(20);

        assertFalse(provider.validateToken(token));
    }

    @Test
    void rejectsShortSecretAndBlankSubject() {
        assertThrows(IllegalStateException.class, () -> new JwtTokenProvider("too-short", 60_000));

        JwtTokenProvider provider = new JwtTokenProvider(SECRET, 60_000);
        assertThrows(IllegalArgumentException.class, () -> provider.createToken(" "));
    }
}
