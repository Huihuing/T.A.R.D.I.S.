package com.tardistock.backend.controller;

import com.tardistock.backend.entity.Member;
import com.tardistock.backend.repository.MemberRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PulseLinkProofControllerTest {
    private static final String SECRET = "Tardis-only-test-proof-secret-with-over-32-characters";
    private static final String CHALLENGE = "X".repeat(43);

    @Test void signsLegacyMemberIdWithExactAudienceAndShortExpiry() {
        MemberRepository repository = mock(MemberRepository.class);
        Member member = mock(Member.class);
        when(member.getId()).thenReturn(42L);
        when(repository.findByUsername("author")).thenReturn(Optional.of(member));
        var controller = new PulseLinkProofController(repository, SECRET);
        var auth = new UsernamePasswordAuthenticationToken("author", null, List.of());
        var response = controller.proof(auth, Map.of("challenge", CHALLENGE));

        assertEquals(200, response.getStatusCode().value());
        assertEquals("no-store", response.getHeaders().getFirst("Cache-Control"));
        @SuppressWarnings("unchecked")
        var result = (Map<String, Object>) response.getBody();
        var claims = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .build().parseSignedClaims((String) result.get("proof")).getPayload();
        assertEquals("pulse-world:tardis", claims.getIssuer());
        assertTrue(claims.getAudience().contains("pulse-id:account-link"));
        assertEquals("42", claims.getSubject());
        assertEquals("tardis", claims.get("world", String.class));
        assertEquals(CHALLENGE, claims.get("challenge", String.class));
        assertTrue(claims.getExpiration().getTime() - claims.getIssuedAt().getTime() <= 90_000);
    }

    @Test void disabledOrUnauthenticatedCannotIssueProof() {
        var repository = mock(MemberRepository.class);
        var controller = new PulseLinkProofController(repository, "");
        var auth = new UsernamePasswordAuthenticationToken("author", null, List.of());
        assertEquals(503, controller.proof(auth, Map.of("challenge", CHALLENGE)).getStatusCode().value());
        assertEquals(401, new PulseLinkProofController(repository, SECRET)
                .proof(null, Map.of("challenge", CHALLENGE)).getStatusCode().value());
        assertEquals(400, new PulseLinkProofController(repository, SECRET)
                .proof(auth, Map.of("challenge", "bad")).getStatusCode().value());
    }
}
