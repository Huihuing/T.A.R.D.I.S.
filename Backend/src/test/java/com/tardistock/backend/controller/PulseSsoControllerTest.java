package com.tardistock.backend.controller;

import com.tardistock.backend.service.PulseSsoRedemptionClient;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PulseSsoControllerTest {
    @Test void verifiedTicketResolvesOnlyBackendReturnedLegacyId() {
        var client = mock(PulseSsoRedemptionClient.class);
        var legacyAuth = mock(AuthController.class);
        var controller = new PulseSsoController(client, legacyAuth);
        String ticket = "A".repeat(43);
        when(client.isConfigured()).thenReturn(true);
        when(client.redeem("tardis", ticket)).thenReturn(Optional.of("42"));
        when(legacyAuth.completePulseLoginById(42L))
                .thenReturn(ResponseEntity.ok(Map.of("token", "native-jwt")));
        ResponseEntity<?> response = controller.login(Map.of("ticket", ticket));
        assertEquals(200, response.getStatusCode().value());
        verify(legacyAuth).completePulseLoginById(42L);
        verifyNoMoreInteractions(legacyAuth);
    }

    @Test void missingKeyAndInvalidOrUnlinkedTicketsCannotLogIn() {
        var client = mock(PulseSsoRedemptionClient.class);
        var auth = mock(AuthController.class);
        var controller = new PulseSsoController(client, auth);
        assertEquals(503, controller.login(Map.of("ticket", "anything")).getStatusCode().value());
        when(client.isConfigured()).thenReturn(true);
        when(client.redeem(eq("tardis"), anyString())).thenReturn(Optional.empty());
        assertEquals(401, controller.login(Map.of("ticket", "bad")).getStatusCode().value());
        when(client.redeem(eq("tardis"), anyString()))
                .thenReturn(Optional.of("9223372036854775808"));
        assertEquals(401, controller.login(Map.of("ticket", "A".repeat(43))).getStatusCode().value());
        verifyNoInteractions(auth);
    }
}
