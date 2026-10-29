package com.tardistock.backend.controller;

import com.tardistock.backend.entity.Member;
import com.tardistock.backend.repository.MemberRepository;
import com.tardistock.backend.service.RefreshTokenService;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PulseSsoRevokeControllerTest {
    private static final String KEY = "only-test-server-logout-secret-at-least-32-bytes";

    @Test void onlyValidWorldServerCanRevokeAllNativeSessions() {
        MemberRepository repo = mock(MemberRepository.class);
        RefreshTokenService tokens = mock(RefreshTokenService.class);
        Member member = new Member("test", "password", "Test", "test@local.invalid", "pin");
        when(repo.findByIdForUpdate(42L)).thenReturn(Optional.of(member));
        var controller = new PulseSsoRevokeController(repo, tokens, KEY);

        var denied = controller.revoke("Bearer wrong",
                Map.of("world", "tardis", "legacyUserId", "42"));
        assertEquals(401, denied.getStatusCode().value());
        verifyNoInteractions(repo, tokens);

        var rejectedWorld = controller.revoke("Bearer " + KEY,
                Map.of("world", "interactive_brain", "legacyUserId", "42"));
        assertEquals(400, rejectedWorld.getStatusCode().value());

        var accepted = controller.revoke("Bearer " + KEY,
                Map.of("world", "tardis", "legacyUserId", "42"));
        assertEquals(200, accepted.getStatusCode().value());
        assertEquals(1L, member.getSessionGeneration());
        verify(tokens).revokeAll(member);
        verify(repo).save(member);
    }

    @Test void unsafeIdsMissingSecretAndUnlinkedMembersFailClosed() {
        MemberRepository repo = mock(MemberRepository.class);
        RefreshTokenService tokens = mock(RefreshTokenService.class);
        assertEquals(503, new PulseSsoRevokeController(repo, tokens, "")
                .revoke(null, Map.of("world", "tardis", "legacyUserId", "42"))
                .getStatusCode().value());

        var controller = new PulseSsoRevokeController(repo, tokens, KEY);
        assertEquals(400, controller.revoke("Bearer " + KEY,
                Map.of("world", "tardis", "legacyUserId", "9223372036854775808"))
                .getStatusCode().value());
        when(repo.findByIdForUpdate(42L)).thenReturn(Optional.empty());
        assertEquals(404, controller.revoke("Bearer " + KEY,
                Map.of("world", "tardis", "legacyUserId", "42"))
                .getStatusCode().value());
        verifyNoInteractions(tokens);
    }
}
