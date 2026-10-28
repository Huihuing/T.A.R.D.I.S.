package com.tardistock.backend.controller;

import com.tardistock.backend.entity.Member;
import com.tardistock.backend.repository.MemberRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Legacy Tardis JWT -> short-lived PULSE account-ownership assertion.
 * This endpoint remains behind the existing authenticated API filter.
 */
@RestController
@RequestMapping("/api/auth/pulse-link")
public class PulseLinkProofController {
    private static final Pattern CHALLENGE = Pattern.compile("^[A-Za-z0-9_-]{43}$");
    private final MemberRepository members;
    private final String secret;

    public PulseLinkProofController(MemberRepository members,
                                    @Value("${PULSE_LINK_PROOF_SECRET:}") String secret) {
        this.members = members;
        this.secret = secret == null ? "" : secret;
    }

    @PostMapping("/proof")
    public ResponseEntity<?> proof(Authentication authentication,
                                   @RequestBody Map<String, String> request) {
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32 || secret.startsWith("REPLACE_WITH_")) {
            return ResponseEntity.status(503).body(Map.of("error", "PULSE_LINK_DISABLED"));
        }
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body(Map.of("error", "AUTH_REQUIRED"));
        }
        String challenge = request == null ? null : request.get("challenge");
        if (challenge == null || !CHALLENGE.matcher(challenge).matches()) {
            return ResponseEntity.badRequest().body(Map.of("error", "INVALID_CHALLENGE"));
        }
        Member member = members.findByUsername(authentication.getName()).orElse(null);
        if (member == null) return ResponseEntity.status(401).body(Map.of("error", "AUTH_REQUIRED"));

        Instant now = Instant.now();
        String proof = Jwts.builder()
                .issuer("pulse-world:tardis")
                .audience().add("pulse-id:account-link").and()
                .subject(Long.toString(member.getId()))
                .claim("world", "tardis")
                .claim("challenge", challenge)
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(90)))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                .compact();
        return ResponseEntity.ok().header("Cache-Control", "no-store")
                .body(Map.of("proof", proof, "expiresIn", 90));
    }
}
