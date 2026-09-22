package com.tardistock.backend.security;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import java.util.Date;

@Component
public class JwtTokenProvider {
    private static final long DEFAULT_TOKEN_VALID_TIME_MS = 1000L * 60 * 60 * 24;

    private final SecretKey key;
    private final JwtParser parser;
    private final long tokenValidTimeMs;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secretKey,
            @Value("${jwt.expiration-ms:" + DEFAULT_TOKEN_VALID_TIME_MS + "}") long tokenValidTimeMs) {
        if (secretKey == null || secretKey.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT secret must be configured and at least 32 bytes long.");
        }
        if (tokenValidTimeMs <= 0) {
            throw new IllegalStateException("JWT expiration must be greater than 0.");
        }

        this.key = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
        this.parser = Jwts.parser().verifyWith(key).build();
        this.tokenValidTimeMs = tokenValidTimeMs;
    }

    public String createToken(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("JWT subject must not be blank.");
        }

        Date now = new Date();
        return Jwts.builder()
                .subject(username)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + tokenValidTimeMs))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public String getUsername(String token) {
        return parser.parseSignedClaims(token).getPayload().getSubject();
    }

    public boolean validateToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }

        try {
            parser.parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
