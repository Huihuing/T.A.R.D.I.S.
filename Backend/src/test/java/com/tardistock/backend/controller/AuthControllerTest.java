package com.tardistock.backend.controller;

import com.tardistock.backend.entity.Member;
import com.tardistock.backend.repository.MemberRepository;
import com.tardistock.backend.repository.WalletRepository;
import com.tardistock.backend.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private MemberRepository memberRepository;
    @Mock
    private WalletRepository walletRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtTokenProvider jwtTokenProvider;

    private AuthController authController;

    @BeforeEach
    void setUp() {
        authController = new AuthController(
                memberRepository,
                walletRepository,
                passwordEncoder,
                jwtTokenProvider
        );
    }

    @Test
    void loginIssuesJwtAfterPasswordVerification() {
        Member member = new Member("alice", "encoded-password", "Alice", "alice@example.test", "encoded-pin");
        member.setLastLoginDate(LocalDate.now());

        when(memberRepository.findByUsername("alice")).thenReturn(Optional.of(member));
        when(passwordEncoder.matches("correct-password", "encoded-password")).thenReturn(true);
        when(jwtTokenProvider.createToken("alice")).thenReturn("signed.jwt.token");

        ResponseEntity<?> response = authController.login(Map.of(
                "username", " alice ",
                "password", "correct-password"
        ));

        assertEquals(200, response.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertEquals("signed.jwt.token", body.get("token"));
        assertEquals("alice", body.get("username"));
        assertEquals(false, body.get("dailyReward"));
        verify(jwtTokenProvider).createToken("alice");
    }

    @Test
    void loginDoesNotIssueJwtForWrongPassword() {
        Member member = new Member("alice", "encoded-password", "Alice", "alice@example.test", "encoded-pin");

        when(memberRepository.findByUsername("alice")).thenReturn(Optional.of(member));
        when(passwordEncoder.matches("wrong-password", "encoded-password")).thenReturn(false);

        ResponseEntity<?> response = authController.login(Map.of(
                "username", "alice",
                "password", "wrong-password"
        ));

        assertEquals(401, response.getStatusCode().value());
        verify(jwtTokenProvider, never()).createToken(anyString());
    }

    @Test
    void loginRejectsBlankCredentialsBeforeRepositoryLookup() {
        ResponseEntity<?> response = authController.login(Map.of(
                "username", " ",
                "password", " "
        ));

        assertEquals(400, response.getStatusCode().value());
        verifyNoInteractions(memberRepository, jwtTokenProvider);
    }
}
