package com.tardistock.backend.service;

import com.tardistock.backend.entity.Member;
import com.tardistock.backend.entity.RefreshToken;
import com.tardistock.backend.repository.RefreshTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class RefreshTokenService {

    public static final String COOKIE_NAME = "refresh_token";

    private final RefreshTokenRepository refreshTokenRepository;
    private final SecureRandom secureRandom = new SecureRandom();
    private final long ttlDays;
    private final boolean cookieSecure;

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            @Value("${refresh-token.ttl-days:30}") long ttlDays,
            @Value("${refresh-token.cookie-secure:true}") boolean cookieSecure) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.ttlDays = Math.max(1, Math.min(ttlDays, 90));
        this.cookieSecure = cookieSecure;
    }

    @Transactional
    public String issue(Member member) {
        String raw = generateToken();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        refreshTokenRepository.save(new RefreshToken(
                member,
                hash(raw),
                now,
                now.plusDays(ttlDays)
        ));

        cleanupExpired(now);
        return raw;
    }

    @Transactional
    public RotatedSession rotate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException(
                    "refresh token이 없습니다."
            );
        }

        String tokenHash = hash(rawToken);
        RefreshToken stored = refreshTokenRepository
                .findByTokenHash(tokenHash)
                .orElseThrow(() -> new IllegalArgumentException(
                        "유효하지 않은 refresh token입니다."
                ));

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (!stored.getExpiresAt().isAfter(now)) {
            refreshTokenRepository.delete(stored);
            throw new IllegalArgumentException(
                    "refresh token이 만료되었습니다."
            );
        }

        Member member = stored.getMember();
        refreshTokenRepository.delete(stored);

        String replacement = generateToken();
        refreshTokenRepository.save(new RefreshToken(
                member,
                hash(replacement),
                now,
                now.plusDays(ttlDays)
        ));

        cleanupExpired(now);
        return new RotatedSession(member, replacement);
    }

    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return;
        refreshTokenRepository.deleteByTokenHash(hash(rawToken));
    }

    public ResponseCookie buildCookie(String rawToken) {
        return ResponseCookie.from(COOKIE_NAME, rawToken)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/api/auth")
                .maxAge(Duration.ofDays(ttlDays))
                .build();
    }

    public ResponseCookie clearCookie() {
        return ResponseCookie.from(COOKIE_NAME, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/api/auth")
                .maxAge(Duration.ZERO)
                .build();
    }

    private void cleanupExpired(LocalDateTime now) {
        refreshTokenRepository.deleteByExpiresAtBefore(now);
    }

    private String generateToken() {
        byte[] bytes = new byte[48];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }

    private String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "refresh token을 처리할 수 없습니다.",
                    e
            );
        }
    }

    public record RotatedSession(
            Member member,
            String rawToken
    ) {}
}
