package com.tardistock.backend.controller;

import com.tardistock.backend.service.PulseSsoRedemptionClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.Optional;

/**
 * Browser posts only a short-lived opaque ticket. Verified legacy user IDs
 * come exclusively from the authenticated server-to-server central exchange.
 */
@RestController
@RequestMapping("/api/auth")
public class PulseSsoController {
    private final PulseSsoRedemptionClient redemptionClient;
    private final AuthController existingAuth;

    public PulseSsoController(PulseSsoRedemptionClient client, AuthController auth) {
        this.redemptionClient = client;
        this.existingAuth = auth;
    }

    @PostMapping("/pulse-sso")
    public ResponseEntity<?> login(@RequestBody Map<String, String> request) {
        if (!redemptionClient.isConfigured()) {
            return ResponseEntity.status(503).body(Map.of("error", "PULSE_SSO_DISABLED"));
        }
        String ticket = request == null ? null : request.get("ticket");
        try {
            Optional<String> legacyId = redemptionClient.redeem("tardis", ticket);
            if (legacyId.isEmpty() || !legacyId.get().matches("^[1-9][0-9]{0,18}$")) {
                return ResponseEntity.status(401).body(Map.of("error", "INVALID_SSO_TICKET"));
            }
            long memberId = Long.parseLong(legacyId.get());
            return existingAuth.completePulseLoginById(memberId);
        } catch (NumberFormatException exception) {
            return ResponseEntity.status(401).body(Map.of("error", "INVALID_SSO_TICKET"));
        } catch (IllegalStateException exception) {
            return ResponseEntity.status(503).body(Map.of("error", "PULSE_SSO_UNAVAILABLE"));
        }
    }
}
