package com.tardistock.backend.controller;

import com.tardistock.backend.entity.Member;
import com.tardistock.backend.entity.Wallet;
import com.tardistock.backend.repository.MemberRepository;
import com.tardistock.backend.repository.WalletRepository;
import com.tardistock.backend.service.GoogleIdentityService;
import com.tardistock.backend.service.LedgerService;
import com.tardistock.backend.service.NotificationService;
import com.tardistock.backend.service.PasswordResetService;
import com.tardistock.backend.service.RefreshTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AccountTransferRangeTest {

    @Test
    void recipientOverflowLeavesBothWalletsUnchanged() {
        WalletRepository wallets = mock(WalletRepository.class);
        MemberRepository members = mock(MemberRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        NotificationService notifications = mock(NotificationService.class);
        LedgerService ledger = mock(LedgerService.class);
        RefreshTokenService refreshTokens = mock(RefreshTokenService.class);
        GoogleIdentityService google = mock(GoogleIdentityService.class);
        PasswordResetService passwordReset = mock(PasswordResetService.class);

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

        double senderBalance = 2_000_000_000.0;
        double receiverBalance = 99_999_999_500_000_000d;
        Wallet senderWallet = new Wallet(sender, senderBalance);
        Wallet receiverWallet = new Wallet(receiver, receiverBalance);

        when(wallets.findAllForUpdateByUsernames(anyList()))
                .thenReturn(List.of(senderWallet, receiverWallet));
        when(encoder.matches("1234", "encoded-pin"))
                .thenReturn(true);

        AccountController controller = new AccountController(
                wallets,
                members,
                encoder,
                notifications,
                ledger,
                refreshTokens,
                google,
                passwordReset
        );

        ResponseEntity<?> response = controller.transfer(
                Map.of(
                        "toUser", "bob",
                        "amount", 1_000_000_000.0,
                        "accountPassword", "1234"
                ),
                new UsernamePasswordAuthenticationToken(
                        "alice",
                        null,
                        List.of()
                )
        );

        assertEquals(400, response.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertEquals(
                "송금 후 잔액이 허용 범위를 초과합니다.",
                body.get("message")
        );
        assertEquals(senderBalance, senderWallet.getBalance(), 0.0);
        assertEquals(receiverBalance, receiverWallet.getBalance(), 0.0);

        verify(wallets, never()).save(any(Wallet.class));
        verifyNoInteractions(ledger, notifications);
    }
}
