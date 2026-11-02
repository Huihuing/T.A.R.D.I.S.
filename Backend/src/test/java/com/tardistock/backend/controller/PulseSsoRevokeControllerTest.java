package com.tardistock.backend.controller;

import com.tardistock.backend.entity.Member;
import com.tardistock.backend.repository.MemberRepository;
import com.tardistock.backend.service.RefreshTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import java.util.List;

import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PulseSsoRevokeControllerTest {
    private static final String KEY = "only-test-server-logout-secret-at-least-32-bytes";
    private static final String REQUEST = "11111111-2222-4333-8444-555555555555";

    @Test void onlyValidWorldServerCanRevokeAllNativeSessions() {
        MemberRepository repo = mock(MemberRepository.class);
        RefreshTokenService tokens = mock(RefreshTokenService.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        Member member = new Member("test", "password", "Test", "test@local.invalid", "pin");
        when(repo.findByIdForUpdate(42L)).thenReturn(Optional.of(member));
        var controller = new PulseSsoRevokeController(repo, tokens, jdbc, KEY);

        var denied = controller.revoke("Bearer wrong",
                Map.of("world", "tardis", "legacyUserId", "42", "operationId", REQUEST));
        assertEquals(401, denied.getStatusCode().value());
        verifyNoInteractions(repo, tokens);

        var rejectedWorld = controller.revoke("Bearer " + KEY,
                Map.of("world", "interactive_brain", "legacyUserId", "42", "operationId", REQUEST));
        assertEquals(400, rejectedWorld.getStatusCode().value());

        doReturn(List.of()).when(jdbc).query(
                anyString(), any(RowMapper.class), eq(REQUEST));
        var accepted = controller.revoke("Bearer " + KEY,
                Map.of("world", "tardis", "legacyUserId", "42", "operationId", REQUEST));
        assertEquals(200, accepted.getStatusCode().value());
        assertEquals(1L, member.getSessionGeneration());
        verify(tokens).revokeAll(member);
        verify(repo).save(member);
        verify(jdbc).update(anyString(), eq(REQUEST), eq(42L));
        doReturn(List.of(42L)).when(jdbc).query(
                anyString(), any(RowMapper.class), eq(REQUEST));
        var replay = controller.revoke("Bearer " + KEY,
                Map.of("world", "tardis", "legacyUserId", "42", "operationId", REQUEST));
        assertEquals(200, replay.getStatusCode().value());
        assertEquals(1L, member.getSessionGeneration(), "Operation retry may not bump JWT generation");
        verify(tokens, times(1)).revokeAll(member);
        verify(repo, times(1)).save(member);
    }

    @Test void unsafeIdsMissingSecretAndUnlinkedMembersFailClosed() {
        MemberRepository repo = mock(MemberRepository.class);
        RefreshTokenService tokens = mock(RefreshTokenService.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        assertEquals(503, new PulseSsoRevokeController(repo, tokens, jdbc, "")
                .revoke(null, Map.of("world", "tardis", "legacyUserId", "42", "operationId", REQUEST))
                .getStatusCode().value());

        var controller = new PulseSsoRevokeController(repo, tokens, jdbc, KEY);
        assertEquals(400, controller.revoke("Bearer " + KEY,
                Map.of("world", "tardis", "legacyUserId", "9223372036854775808", "operationId", REQUEST))
                .getStatusCode().value());
        when(repo.findByIdForUpdate(42L)).thenReturn(Optional.empty());
        assertEquals(404, controller.revoke("Bearer " + KEY,
                Map.of("world", "tardis", "legacyUserId", "42", "operationId", REQUEST))
                .getStatusCode().value());
        verifyNoInteractions(tokens);
    }
}
