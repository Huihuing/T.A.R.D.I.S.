package com.tardistock.backend.controller;

import com.tardistock.backend.entity.LedgerEntry;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
                ledger,
                refreshTokens,
                google,
                passwordReset
        );

        ResponseEntity<?> response = controller.transfer(
                Map.of(
                        "toUser", "bob",
                        "amount", 750.0,
                        "accountPassword", "1234"
                ),
                auth("alice")
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

    @Test
    void settingsReturnsCredentialState() {
        WalletRepository wallets = mock(WalletRepository.class);
        MemberRepository members = mock(MemberRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        NotificationService notifications = mock(NotificationService.class);
        LedgerService ledger = mock(LedgerService.class);
        RefreshTokenService refreshTokens = mock(RefreshTokenService.class);
        GoogleIdentityService google = mock(GoogleIdentityService.class);
        PasswordResetService passwordReset = mock(PasswordResetService.class);

        Member member = new Member(
                "alice",
                "encoded-password",
                "Alice",
                "alice@example.test",
                "encoded-pin"
        );
        member.setEmailVerified(true);
        member.setSocialProvider("GOOGLE");
        member.setSocialSubject("google-subject");

        when(members.findByUsername("alice"))
                .thenReturn(Optional.of(member));

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

        ResponseEntity<?> response = controller.settings(auth("alice"));

        assertEquals(200, response.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, Object> body =
                (Map<String, Object>) response.getBody();
        assertEquals("alice", body.get("username"));
        assertEquals("alice@example.test", body.get("email"));
        assertEquals(true, body.get("emailVerified"));
        assertEquals(true, body.get("pinConfigured"));
        assertEquals(true, body.get("passwordLoginEnabled"));
        assertEquals(true, body.get("googleConnected"));
    }

    @Test
    void changePasswordRevokesRefreshSessions() {
        WalletRepository wallets = mock(WalletRepository.class);
        MemberRepository members = mock(MemberRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        NotificationService notifications = mock(NotificationService.class);
        LedgerService ledger = mock(LedgerService.class);
        RefreshTokenService refreshTokens = mock(RefreshTokenService.class);
        GoogleIdentityService google = mock(GoogleIdentityService.class);
        PasswordResetService passwordReset = mock(PasswordResetService.class);

        Member member = new Member(
                "alice",
                "encoded-old",
                "Alice",
                "alice@example.test",
                "encoded-pin"
        );

        when(members.findByUsernameForUpdate("alice"))
                .thenReturn(Optional.of(member));
        when(encoder.matches("old-password", "encoded-old"))
                .thenReturn(true);
        when(encoder.matches("new-password", "encoded-old"))
                .thenReturn(false);
        when(encoder.encode("new-password"))
                .thenReturn("encoded-new");

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

        ResponseEntity<?> response = controller.changePassword(
                Map.of(
                        "currentPassword", "old-password",
                        "newPassword", "new-password"
                ),
                auth("alice")
        );

        assertEquals(200, response.getStatusCode().value());
        assertEquals("encoded-new", member.getPassword());
        verify(members).save(member);
        verify(refreshTokens).revokeAll(member);
    }

    @Test
    void changePinRejectsWrongCurrentPin() {
        WalletRepository wallets = mock(WalletRepository.class);
        MemberRepository members = mock(MemberRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        NotificationService notifications = mock(NotificationService.class);
        LedgerService ledger = mock(LedgerService.class);
        RefreshTokenService refreshTokens = mock(RefreshTokenService.class);
        GoogleIdentityService google = mock(GoogleIdentityService.class);
        PasswordResetService passwordReset = mock(PasswordResetService.class);

        Member member = new Member(
                "alice",
                "encoded-password",
                "Alice",
                "alice@example.test",
                "encoded-pin"
        );

        when(members.findByUsernameForUpdate("alice"))
                .thenReturn(Optional.of(member));
        when(encoder.matches("0000", "encoded-pin"))
                .thenReturn(false);

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

        ResponseEntity<?> response = controller.changePin(
                Map.of(
                        "currentPin", "0000",
                        "newPin", "5678"
                ),
                auth("alice")
        );

        assertEquals(401, response.getStatusCode().value());
        verify(members, never()).save(any(Member.class));
    }

    @Test
    void googleLinkRejectsDifferentEmail() {
        WalletRepository wallets = mock(WalletRepository.class);
        MemberRepository members = mock(MemberRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        NotificationService notifications = mock(NotificationService.class);
        LedgerService ledger = mock(LedgerService.class);
        RefreshTokenService refreshTokens = mock(RefreshTokenService.class);
        GoogleIdentityService google = mock(GoogleIdentityService.class);
        PasswordResetService passwordReset = mock(PasswordResetService.class);

        Member member = new Member(
                "alice",
                "encoded-password",
                "Alice",
                "alice@example.test",
                "encoded-pin"
        );

        when(members.findByUsernameForUpdate("alice"))
                .thenReturn(Optional.of(member));
        when(google.verify("credential"))
                .thenReturn(new GoogleIdentityService.GoogleIdentity(
                        "google-subject",
                        "other@example.test",
                        "Other"
                ));

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

        ResponseEntity<?> response = controller.linkGoogle(
                Map.of("credential", "credential"),
                auth("alice")
        );

        assertEquals(409, response.getStatusCode().value());
        verify(members, never()).save(any(Member.class));
    }

    @Test
    void googleOnlyAccountCannotUnlinkGoogle() {
        WalletRepository wallets = mock(WalletRepository.class);
        MemberRepository members = mock(MemberRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        NotificationService notifications = mock(NotificationService.class);
        LedgerService ledger = mock(LedgerService.class);
        RefreshTokenService refreshTokens = mock(RefreshTokenService.class);
        GoogleIdentityService google = mock(GoogleIdentityService.class);
        PasswordResetService passwordReset = mock(PasswordResetService.class);

        Member member = new Member(
                "g_alice",
                "random-password",
                "Alice",
                "alice@example.test",
                "random-pin"
        );
        member.setSocialProvider("GOOGLE");
        member.setSocialSubject("google-subject");
        member.setPasswordLoginEnabled(false);

        when(members.findByUsernameForUpdate("g_alice"))
                .thenReturn(Optional.of(member));

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

        ResponseEntity<?> response = controller.unlinkGoogle(
                auth("g_alice")
        );

        assertEquals(409, response.getStatusCode().value());
        assertEquals("GOOGLE", member.getSocialProvider());
        verify(members, never()).save(any(Member.class));
    }

    @Test
    void resetPinUsesSecurityCodeAndUpdatesPin() {
        WalletRepository wallets = mock(WalletRepository.class);
        MemberRepository members = mock(MemberRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        NotificationService notifications = mock(NotificationService.class);
        LedgerService ledger = mock(LedgerService.class);
        RefreshTokenService refreshTokens = mock(RefreshTokenService.class);
        GoogleIdentityService google = mock(GoogleIdentityService.class);
        PasswordResetService passwordReset = mock(PasswordResetService.class);

        Member member = new Member(
                "alice",
                "encoded-password",
                "Alice",
                "alice@example.test",
                "encoded-pin"
        );

        when(members.findByUsernameForUpdate("alice"))
                .thenReturn(Optional.of(member));
        when(encoder.matches("5678", "encoded-pin"))
                .thenReturn(false);
        when(encoder.encode("5678"))
                .thenReturn("encoded-new-pin");

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

        ResponseEntity<?> response = controller.resetPin(
                Map.of(
                        "code", "123456",
                        "newPin", "5678"
                ),
                auth("alice")
        );

        assertEquals(200, response.getStatusCode().value());
        assertEquals("encoded-new-pin", member.getPin());
        assertTrue(member.isPinConfigured());
        verify(passwordReset).consumeSecurityCode(member, "123456");
        verify(notifications).create(
                member,
                "SECURITY",
                "이메일 인증을 통해 송금 PIN이 재설정되었습니다."
        );
    }

    @Test
    void googleOnlyAccountCanEnablePasswordWithSecurityCode() {
        WalletRepository wallets = mock(WalletRepository.class);
        MemberRepository members = mock(MemberRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        NotificationService notifications = mock(NotificationService.class);
        LedgerService ledger = mock(LedgerService.class);
        RefreshTokenService refreshTokens = mock(RefreshTokenService.class);
        GoogleIdentityService google = mock(GoogleIdentityService.class);
        PasswordResetService passwordReset = mock(PasswordResetService.class);

        Member member = new Member(
                "g_alice",
                "random-password",
                "Alice",
                "alice@example.test",
                "random-pin"
        );
        member.setSocialProvider("GOOGLE");
        member.setPasswordLoginEnabled(false);

        when(members.findByUsernameForUpdate("g_alice"))
                .thenReturn(Optional.of(member));
        when(encoder.encode("new-password"))
                .thenReturn("encoded-new-password");

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

        ResponseEntity<?> response = controller.enablePasswordLogin(
                Map.of(
                        "code", "654321",
                        "newPassword", "new-password"
                ),
                auth("g_alice")
        );

        assertEquals(200, response.getStatusCode().value());
        assertTrue(member.isPasswordLoginEnabled());
        assertEquals("encoded-new-password", member.getPassword());
        verify(passwordReset).consumeSecurityCode(member, "654321");
        verify(notifications).create(
                member,
                "SECURITY",
                "일반 비밀번호 로그인이 추가되었습니다."
        );
    }

    private static UsernamePasswordAuthenticationToken auth(
            String username) {
        return new UsernamePasswordAuthenticationToken(
                username,
                null,
                List.of()
        );
    }
}
