package com.tardistock.backend.controller;

import com.tardistock.backend.entity.Member;
import com.tardistock.backend.repository.MemberRepository;
import com.tardistock.backend.repository.WalletRepository;
import com.tardistock.backend.security.JwtTokenProvider;
import com.tardistock.backend.service.EmailVerificationService;
import com.tardistock.backend.service.GoogleIdentityService;
import com.tardistock.backend.service.LedgerService;
import com.tardistock.backend.service.NotificationService;
import com.tardistock.backend.service.RefreshTokenService;
import jakarta.persistence.Column;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthRegistrationEmailUniquenessTest {

    @Mock MemberRepository memberRepository;
    @Mock WalletRepository walletRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtTokenProvider jwtTokenProvider;
    @Mock LedgerService ledgerService;
    @Mock EmailVerificationService emailVerificationService;
    @Mock GoogleIdentityService googleIdentityService;
    @Mock RefreshTokenService refreshTokenService;
    @Mock NotificationService notificationService;

    private AuthController controller;

    @BeforeEach
    void setUp() {
        controller = new AuthController(
                memberRepository,
                walletRepository,
                passwordEncoder,
                jwtTokenProvider,
                ledgerService,
                emailVerificationService,
                googleIdentityService,
                refreshTokenService,
                notificationService
        );
    }

    @Test
    void registerRejectsExistingEmailCaseInsensitivelyBeforeConsume() {
        when(memberRepository.findByUsername("alice"))
                .thenReturn(Optional.empty());
        when(memberRepository.existsByEmailIgnoreCase("alice@example.com"))
                .thenReturn(true);

        ResponseEntity<?> response = controller.register(Map.of(
                "username", "alice",
                "password", "password123",
                "name", "Alice",
                "email", " Alice@Example.COM ",
                "pin", "1234"
        ));

        assertEquals(400, response.getStatusCode().value());
        verify(emailVerificationService, never())
                .consumeVerified("alice@example.com");
        verify(memberRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(walletRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void memberEmailDeclaresUniqueColumnContract() throws Exception {
        Column column = Member.class
                .getDeclaredField("email")
                .getAnnotation(Column.class);

        assertTrue(column.unique());
    }
}
