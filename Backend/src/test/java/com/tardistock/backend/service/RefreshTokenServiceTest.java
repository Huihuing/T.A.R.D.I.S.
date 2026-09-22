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
    void secureProductionCookieUsesSameSiteLax() {
        RefreshTokenRepository repository =
                mock(RefreshTokenRepository.class);
        RefreshTokenService service =
                new RefreshTokenService(repository, 30, true);

        ResponseCookie cookie =
                service.buildCookie("sample-refresh-token");

        String value = cookie.toString();
        assertTrue(value.contains("HttpOnly"));
        assertTrue(value.contains("Secure"));
        assertTrue(value.contains("SameSite=Lax"));
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
    void clearCookieMatchesSameSitePolicy() {
        RefreshTokenRepository repository =
                mock(RefreshTokenRepository.class);
        RefreshTokenService service =
                new RefreshTokenService(repository, 30, true);

        String value = service.clearCookie().toString();

        assertTrue(value.contains("SameSite=Lax"));
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
    void issueTrimsOldSessionsBeyondTwenty() {
        RefreshTokenRepository repository =
                mock(RefreshTokenRepository.class);
        RefreshTokenService service =
                new RefreshTokenService(repository, 30, true);

        Member member = mock(Member.class);
        List<RefreshToken> sessions =
                java.util.stream.IntStream.range(0, 21)
                        .mapToObj(i -> mock(RefreshToken.class))
                        .toList();

        when(repository.findByMemberOrderByCreatedAtDesc(member))
                .thenReturn(sessions);

        service.issue(member);

        verify(repository).deleteAll(
                sessions.subList(20, 21)
        );
    }

    @Test
    void rotateUsesWriteLockedLookup() {
        RefreshTokenRepository repository =
                mock(RefreshTokenRepository.class);
        RefreshTokenService service =
                new RefreshTokenService(repository, 30, true);

        Member member = mock(Member.class);
        RefreshToken stored = mock(RefreshToken.class);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        when(stored.getMember()).thenReturn(member);
        when(stored.getExpiresAt()).thenReturn(now.plusDays(1));
        when(repository.findByTokenHashForUpdate(anyString()))
                .thenReturn(Optional.of(stored));

        service.rotate("current-refresh");

        verify(repository).findByTokenHashForUpdate(anyString());
        verify(repository).delete(stored);
        verify(repository, atLeastOnce()).save(any(RefreshToken.class));
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
        when(repository.findByTokenHashForUpdate(anyString()))
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
