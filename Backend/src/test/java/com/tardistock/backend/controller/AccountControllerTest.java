package com.tardistock.backend.controller;

import com.tardistock.backend.entity.LedgerEntry;
import com.tardistock.backend.entity.Member;
import com.tardistock.backend.entity.Wallet;
import com.tardistock.backend.repository.MemberRepository;
import com.tardistock.backend.repository.WalletRepository;
import com.tardistock.backend.service.LedgerService;
import com.tardistock.backend.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AccountControllerTest {

    @Test
    void transferReturnsReceiptAndUpdatesBothWallets() {
        WalletRepository wallets = mock(WalletRepository.class);
        MemberRepository members = mock(MemberRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        NotificationService notifications = mock(NotificationService.class);
        LedgerService ledger = mock(LedgerService.class);

        Member sender = new Member(
                "alice",
                "pw",
                "Alice",
                "alice@example.test",
                "encoded-pin"
        );
        Member receiver = new Member(
                "bob",
                "pw",
                "Bob",
                "bob@example.test",
                "encoded-pin"
        );
        Wallet senderWallet = new Wallet(sender, 5000.0);
        Wallet receiverWallet = new Wallet(receiver, 1000.0);

        when(wallets.findAllForUpdateByUsernames(anyList()))
                .thenReturn(List.of(senderWallet, receiverWallet));
        when(encoder.matches("1234", "encoded-pin"))
                .thenReturn(true);

        LedgerEntry outgoing = mock(LedgerEntry.class);
        when(outgoing.getId()).thenReturn(42L);
        when(outgoing.getCreatedAt()).thenReturn(
                LocalDateTime.of(2026, 9, 22, 10, 30)
        );

        when(ledger.record(
                eq(sender),
                eq("TRANSFER_OUT"),
                eq(-750.0),
                eq(4250.0),
                eq("bob"),
                isNull(),
                anyString()
        )).thenReturn(outgoing);

        AccountController controller = new AccountController(
                wallets,
                members,
                encoder,
                notifications,
                ledger
        );

        ResponseEntity<?> response = controller.transfer(
                Map.of(
                        "toUser", "bob",
                        "amount", 750.0,
                        "accountPassword", "1234"
                ),
                new UsernamePasswordAuthenticationToken(
                        "alice",
                        null,
                        List.of()
                )
        );

        assertEquals(200, response.getStatusCode().value());
        assertEquals(4250.0, senderWallet.getBalance());
        assertEquals(1750.0, receiverWallet.getBalance());

        @SuppressWarnings("unchecked")
        Map<String, Object> body =
                (Map<String, Object>) response.getBody();

        assertEquals("SUCCESS", body.get("status"));
        assertEquals(42L, body.get("transactionId"));
        assertEquals("alice", body.get("fromUser"));
        assertEquals("bob", body.get("toUser"));
        assertEquals(750.0, body.get("amount"));
        assertEquals("2026-09-22T10:30", body.get("transferredAt"));
        assertEquals(4250.0, body.get("newBalance"));

        verify(wallets).save(senderWallet);
        verify(wallets).save(receiverWallet);
        verify(notifications).create(
                eq(receiver),
                eq("TRANSFER"),
                contains("$750.00")
        );
    }
}
