package com.tardistock.backend.service;

import com.tardistock.backend.entity.Member;
import com.tardistock.backend.entity.RefreshToken;
import com.tardistock.backend.repository.RefreshTokenRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class RefreshTokenServiceTest {

    @Test
    void secureProductionCookieUsesSameSiteNone() {
        RefreshTokenRepository repository =
                mock(RefreshTokenRepository.class);
        RefreshTokenService service =
                new RefreshTokenService(repository, 30, true);

        ResponseCookie cookie =
                service.buildCookie("sample-refresh-token");

        String value = cookie.toString();
        assertTrue(value.contains("HttpOnly"));
        assertTrue(value.contains("Secure"));
        assertTrue(value.contains("SameSite=None"));
        assertTrue(value.contains("Path=/api/auth"));
    }

    @Test
    void localInsecureCookieFallsBackToSameSiteLax() {
        RefreshTokenRepository repository =
                mock(RefreshTokenRepository.class);
        RefreshTokenService service =
                new RefreshTokenService(repository, 30, false);

        ResponseCookie cookie =
                service.buildCookie("sample-refresh-token");

        String value = cookie.toString();
        assertTrue(value.contains("HttpOnly"));
        assertFalse(value.contains("Secure"));
        assertTrue(value.contains("SameSite=Lax"));
        assertTrue(value.contains("Path=/api/auth"));
    }

    @Test
    void clearCookieMatchesCrossSitePolicy() {
        RefreshTokenRepository repository =
                mock(RefreshTokenRepository.class);
        RefreshTokenService service =
                new RefreshTokenService(repository, 30, true);

        String value = service.clearCookie().toString();

        assertTrue(value.contains("SameSite=None"));
        assertTrue(value.contains("Secure"));
        assertTrue(value.contains("Max-Age=0"));
    }
    @Test
    void listsOnlyUnexpiredSessions() {
        RefreshTokenRepository repository =
                mock(RefreshTokenRepository.class);
        RefreshTokenService service =
                new RefreshTokenService(repository, 30, true);

        Member member = mock(Member.class);
        RefreshToken active = mock(RefreshToken.class);
        RefreshToken expired = mock(RefreshToken.class);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        when(active.getId()).thenReturn(1L);
        when(active.getCreatedAt()).thenReturn(now.minusHours(1));
        when(active.getExpiresAt()).thenReturn(now.plusDays(1));
        when(active.getTokenHash()).thenReturn("active-hash");

        when(expired.getId()).thenReturn(2L);
        when(expired.getCreatedAt()).thenReturn(now.minusDays(40));
        when(expired.getExpiresAt()).thenReturn(now.minusDays(1));
        when(expired.getTokenHash()).thenReturn("expired-hash");

        when(repository.findByMemberOrderByCreatedAtDesc(member))
                .thenReturn(List.of(active, expired));

        var sessions = service.sessions(member, null);

        assertEquals(1, sessions.size());
        assertEquals(1L, sessions.get(0).id());
        assertFalse(sessions.get(0).current());
    }

    @Test
    void revokeOtherSessionsKeepsCurrentRefreshToken() {
        RefreshTokenRepository repository =
                mock(RefreshTokenRepository.class);
        RefreshTokenService service =
                new RefreshTokenService(repository, 30, true);

        Member member = mock(Member.class);
        Member storedMember = mock(Member.class);
        RefreshToken current = mock(RefreshToken.class);

        when(member.getId()).thenReturn(10L);
        when(storedMember.getId()).thenReturn(10L);
        when(current.getMember()).thenReturn(storedMember);
        when(repository.findByTokenHash(anyString()))
                .thenReturn(Optional.of(current));
        when(repository.deleteByMemberAndTokenHashNot(
                eq(member),
                anyString()
        )).thenReturn(2L);

        long revoked =
                service.revokeOtherSessions(member, "current-refresh");

        assertEquals(2L, revoked);
        verify(repository).deleteByMemberAndTokenHashNot(
                eq(member),
                anyString()
        );
    }

}
