package com.tardistock.backend.service;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GoogleIdentityServiceTest {

    private static final String CLIENT_ID = "client-id.apps.googleusercontent.com";
    private static final long NOW = 2_000_000_000L;

    @Test
    void acceptsValidVerifiedGoogleIdentity() {
        GoogleIdentityService service = new GoogleIdentityService(CLIENT_ID);

        GoogleIdentityService.GoogleIdentity identity =
                service.validatePayload(validPayload(), NOW);

        assertEquals("google-subject", identity.subject());
        assertEquals("alice@example.com", identity.email());
        assertEquals("Alice", identity.name());
    }

    @Test
    void rejectsWrongAudience() {
        GoogleIdentityService service = new GoogleIdentityService(CLIENT_ID);
        Map<String, Object> payload = validPayload();
        payload.put("aud", "other-client");

        assertThrows(
                IllegalArgumentException.class,
                () -> service.validatePayload(payload, NOW)
        );
    }

    @Test
    void rejectsUnverifiedEmail() {
        GoogleIdentityService service = new GoogleIdentityService(CLIENT_ID);
        Map<String, Object> payload = validPayload();
        payload.put("email_verified", "false");

        assertThrows(
                IllegalArgumentException.class,
                () -> service.validatePayload(payload, NOW)
        );
    }

    @Test
    void rejectsExpiredToken() {
        GoogleIdentityService service = new GoogleIdentityService(CLIENT_ID);
        Map<String, Object> payload = validPayload();
        payload.put("exp", Long.toString(NOW));

        assertThrows(
                IllegalArgumentException.class,
                () -> service.validatePayload(payload, NOW)
        );
    }

    @Test
    void rejectsUnexpectedIssuer() {
        GoogleIdentityService service = new GoogleIdentityService(CLIENT_ID);
        Map<String, Object> payload = validPayload();
        payload.put("iss", "https://example.com");

        assertThrows(
                IllegalArgumentException.class,
                () -> service.validatePayload(payload, NOW)
        );
    }

    private Map<String, Object> validPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("aud", CLIENT_ID);
        payload.put("iss", "https://accounts.google.com");
        payload.put("sub", "google-subject");
        payload.put("email", "Alice@Example.COM");
        payload.put("name", "Alice");
        payload.put("email_verified", "true");
        payload.put("exp", Long.toString(NOW + 600));
        return payload;
    }
}
