package com.tardistock.backend.controller;

import com.tardistock.backend.entity.Member;
import com.tardistock.backend.repository.MemberRepository;
import com.tardistock.backend.service.RefreshTokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Explicit all-device logout from the trusted central PULSE server only.
 * Revokes native JWTs by bumping member.sessionGeneration and all refresh
 * sessions. Password/Google sessions are also revoked intentionally.
 *
 * Unauthorized browsers never receive a user ID or revocation capability.
 */
@RestController
@RequestMapping("/api/auth/pulse-sso")
public class PulseSsoRevokeController {
    private static final Pattern BIGINT = Pattern.compile("^[1-9][0-9]{0,18}$");
    private final String serviceKey;
    private final MemberRepository members;
    private final RefreshTokenService refreshTokens;

    public PulseSsoRevokeController(
            MemberRepository members,
            RefreshTokenService refreshTokens,
            @Value("${PULSE_SSO_SERVICE_KEY:}") String serviceKey) {
        this.members = members;
        this.refreshTokens = refreshTokens;
        this.serviceKey = serviceKey == null ? "" : serviceKey;
    }

    private boolean configured() {
        return serviceKey.getBytes(StandardCharsets.UTF_8).length >= 32 &&
                !serviceKey.startsWith("REPLACE_WITH_");
    }

    private boolean authorized(String authHeader) {
        if (!configured() || authHeader == null ||
                !authHeader.startsWith("Bearer ") || authHeader.length() > 520) return false;
        String presented = authHeader.substring(7);
        if (presented.isEmpty()) return false;
        byte[] expected = digest(serviceKey);
        byte[] received = digest(presented);
        return MessageDigest.isEqual(expected, received);
    }

    private static byte[] digest(String value) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA256_UNAVAILABLE");
        }
    }

    @PostMapping("/revoke")
    @Transactional
    public ResponseEntity<?> revoke(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
                    String authorization,
            @RequestBody Map<String, String> request) {
        if (!configured()) {
            return ResponseEntity.status(503).body(Map.of("error", "PULSE_SSO_DISABLED"));
        }
        if (!authorized(authorization)) {
            return ResponseEntity.status(401).body(Map.of("error", "SERVICE_AUTH_REQUIRED"));
        }
        String world = request == null ? null : request.get("world");
        String rawId = request == null ? null : request.get("legacyUserId");
        if (!"tardis".equals(world) || rawId == null || !BIGINT.matcher(rawId).matches()) {
            return ResponseEntity.badRequest().body(Map.of("error", "INVALID_REVOKE_REQUEST"));
        }

        final long id;
        try { id = Long.parseLong(rawId); }
        catch (NumberFormatException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", "INVALID_REVOKE_REQUEST"));
        }
        Optional<Member> member = members.findByIdForUpdate(id);
        if (member.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "ACCOUNT_UNAVAILABLE"));
        }

        Member target = member.get();
        target.incrementSessionGeneration();
        members.save(target);
        refreshTokens.revokeAll(target);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(Map.of("ok", true));
    }
}
