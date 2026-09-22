package com.tardistock.backend.controller;

import com.tardistock.backend.entity.Member;
import com.tardistock.backend.entity.Wallet;
import com.tardistock.backend.repository.MemberRepository;
import com.tardistock.backend.repository.WalletRepository;
import com.tardistock.backend.security.JwtTokenProvider;
import com.tardistock.backend.service.LedgerService;
import com.tardistock.backend.service.EmailVerificationService;
import com.tardistock.backend.service.GoogleIdentityService;
import com.tardistock.backend.service.NotificationService;
import com.tardistock.backend.service.RefreshTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import com.tardistock.backend.security.JwtAuthenticationFilter;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
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
    @Mock
    private LedgerService ledgerService;
    @Mock
    private EmailVerificationService emailVerificationService;
    @Mock
    private GoogleIdentityService googleIdentityService;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private NotificationService notificationService;

    private AuthController authController;

    @BeforeEach
    void setUp() {
        authController = new AuthController(
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

        lenient().when(refreshTokenService.issue(any(Member.class)))
                .thenReturn("test-refresh-token");
        lenient().when(refreshTokenService.buildCookie("test-refresh-token"))
                .thenReturn(ResponseCookie.from(
                        RefreshTokenService.COOKIE_NAME,
                        "test-refresh-token"
                ).httpOnly(true).path("/api/auth").build());
    }

    @Test
    void loginIssuesJwtAfterPasswordVerification() {
        Member member = new Member(
                "alice",
                "encoded-password",
                "Alice",
                "alice@example.test",
                "encoded-pin"
        );
        member.setLastLoginDate(LocalDate.now(ZoneId.of("Asia/Seoul")));

        when(memberRepository.findByUsernameForUpdate("alice"))
                .thenReturn(Optional.of(member));
        when(passwordEncoder.matches(
                "correct-password", "encoded-password"))
                .thenReturn(true);
        when(jwtTokenProvider.createToken("alice"))
                .thenReturn("signed.jwt.token");

        ResponseEntity<?> response = authController.login(Map.of(
                "username", " alice ",
                "password", "correct-password"
        ));

        assertEquals(200, response.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, Object> body =
                (Map<String, Object>) response.getBody();
        assertEquals("signed.jwt.token", body.get("token"));
        assertEquals("alice", body.get("username"));
        assertEquals(false, body.get("dailyReward"));
        verify(jwtTokenProvider).createToken("alice");
    }

    @Test
    void loginDailyRewardUsesLockedWalletAndOnlyOncePerDate() {
        Member member = new Member(
                "alice",
                "encoded-password",
                "Alice",
                "alice@example.test",
                "encoded-pin"
        );
        member.setLastLoginDate(
                LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(1));
        Wallet wallet = new Wallet(member, 1000.0);

        when(memberRepository.findByUsernameForUpdate("alice"))
                .thenReturn(Optional.of(member));
        when(passwordEncoder.matches(
                "correct-password", "encoded-password"))
                .thenReturn(true);
        when(jwtTokenProvider.createToken("alice"))
                .thenReturn("signed.jwt.token");
        when(walletRepository.findForUpdateByMember(member))
                .thenReturn(Optional.of(wallet));

        ResponseEntity<?> response = authController.login(Map.of(
                "username", "alice",
                "password", "correct-password"
        ));

        assertEquals(200, response.getStatusCode().value());
        assertEquals(1500.0, wallet.getBalance());
        assertEquals(
                LocalDate.now(ZoneId.of("Asia/Seoul")),
                member.getLastLoginDate()
        );
        verify(walletRepository).findForUpdateByMember(member);
        verify(walletRepository).save(wallet);
        verify(ledgerService).record(
                eq(member),
                eq("DAILY_LOGIN_REWARD"),
                eq(500.0),
                eq(1500.0),
                eq("일일 로그인 보상")
        );
    }

    @Test
    void loginDoesNotIssueJwtForWrongPassword() {
        Member member = new Member(
                "alice",
                "encoded-password",
                "Alice",
                "alice@example.test",
                "encoded-pin"
        );

        when(memberRepository.findByUsernameForUpdate("alice"))
                .thenReturn(Optional.of(member));
        when(passwordEncoder.matches(
                "wrong-password", "encoded-password"))
                .thenReturn(false);

        ResponseEntity<?> response = authController.login(Map.of(
                "username", "alice",
                "password", "wrong-password"
        ));

        assertEquals(401, response.getStatusCode().value());
        verify(jwtTokenProvider, never()).createToken(anyString());
    }

    @Test
    void issuedJwtAuthenticatesSubsequentBearerRequest() throws Exception {
        MemberRepository members = mock(MemberRepository.class);
        WalletRepository wallets = mock(WalletRepository.class);
        PasswordEncoder encoder = new BCryptPasswordEncoder();
        JwtTokenProvider provider = new JwtTokenProvider(
                "integration-test-secret-key-that-is-at-least-thirty-two-bytes",
                60_000
        );

        Member member = new Member(
                "alice",
                encoder.encode("correct-password"),
                "Alice",
                "alice@example.test",
                encoder.encode("1234")
        );
        member.setLastLoginDate(LocalDate.now());

        when(members.findByUsernameForUpdate("alice")).thenReturn(Optional.of(member));

        RefreshTokenService refreshService =
                mock(RefreshTokenService.class);
        when(refreshService.issue(any(Member.class)))
                .thenReturn("integration-refresh-token");
        when(refreshService.buildCookie("integration-refresh-token"))
                .thenReturn(ResponseCookie.from(
                        RefreshTokenService.COOKIE_NAME,
                        "integration-refresh-token"
                ).httpOnly(true).path("/api/auth").build());

        AuthController controller = new AuthController(
                members,
                wallets,
                encoder,
                provider,
                mock(LedgerService.class),
                mock(EmailVerificationService.class),
                mock(GoogleIdentityService.class),
                refreshService,
                mock(NotificationService.class)
        );

        ResponseEntity<?> loginResponse = controller.login(Map.of(
                "username", "alice",
                "password", "correct-password"
        ));

        assertEquals(200, loginResponse.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) loginResponse.getBody();
        String token = (String) body.get("token");

        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(provider);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        try {
            filter.doFilter(request, response, (req, res) -> {
                assertEquals(
                        "alice",
                        SecurityContextHolder.getContext()
                                .getAuthentication()
                                .getName()
                );
            });
        } finally {
            SecurityContextHolder.clearContext();
        }
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

    @Test
    void registerNormalizesAndValidatesFields() {
        when(memberRepository.findByUsername("alice"))
                .thenReturn(Optional.empty());
        when(memberRepository.existsByEmailIgnoreCase("alice@example.com"))
                .thenReturn(false);
        when(passwordEncoder.encode("password123"))
                .thenReturn("encoded-password");
        when(passwordEncoder.encode("1234"))
                .thenReturn("encoded-pin");
        when(emailVerificationService.consumeVerified("alice@example.com"))
                .thenReturn(true);

        ResponseEntity<?> response = authController.register(Map.of(
                "username", " alice ",
                "password", "password123",
                "name", " Alice ",
                "email", " Alice@Example.COM ",
                "pin", "1234"
        ));

        assertEquals(200, response.getStatusCode().value());

        ArgumentCaptor<Member> memberCaptor =
                ArgumentCaptor.forClass(Member.class);
        verify(memberRepository).save(memberCaptor.capture());
        Member saved = memberCaptor.getValue();
        assertEquals("alice", saved.getUsername());
        assertEquals("alice@example.com", saved.getEmail());
        assertEquals("Alice", saved.getName());
        assertTrue(saved.isEmailVerified());
        verify(walletRepository).save(any(Wallet.class));
        verify(emailVerificationService)
                .consumeVerified("alice@example.com");
    }

    @Test
    void registerRejectsWhenEmailWasNotVerified() {
        when(memberRepository.findByUsername("alice"))
                .thenReturn(Optional.empty());
        when(memberRepository.existsByEmailIgnoreCase(
                "alice@example.com"
        )).thenReturn(false);
        when(emailVerificationService.consumeVerified(
                "alice@example.com"
        )).thenReturn(false);

        ResponseEntity<?> response = authController.register(Map.of(
                "username", "alice",
                "password", "password123",
                "name", "Alice",
                "email", "alice@example.com",
                "pin", "1234"
        ));

        assertEquals(400, response.getStatusCode().value());
        verify(memberRepository, never()).save(any());
        verify(walletRepository, never()).save(any());
    }

    @Test
    void registerRejectsInvalidPinAndWeakPassword() {
        ResponseEntity<?> weakPassword = authController.register(Map.of(
                "username", "alice",
                "password", "short",
                "name", "Alice",
                "email", "alice@example.com",
                "pin", "1234"
        ));
        assertEquals(400, weakPassword.getStatusCode().value());

        ResponseEntity<?> invalidPin = authController.register(Map.of(
                "username", "alice",
                "password", "password123",
                "name", "Alice",
                "email", "alice@example.com",
                "pin", "12ab"
        ));
        assertEquals(400, invalidPin.getStatusCode().value());

        verify(memberRepository, never()).save(any());
    }
    @Test
    void googleLoginCreatesVerifiedSocialAccountWithoutEmailCode() {
        GoogleIdentityService.GoogleIdentity identity =
                new GoogleIdentityService.GoogleIdentity(
                        "google-subject-123",
                        "google@example.com",
                        "Google User"
                );

        when(googleIdentityService.verify("google-id-token"))
                .thenReturn(identity);
        when(memberRepository.findBySocialProviderAndSocialSubject(
                "GOOGLE", "google-subject-123"))
                .thenReturn(Optional.empty());
        when(memberRepository.findByEmailIgnoreCase(
                "google@example.com"))
                .thenReturn(Optional.empty());
        when(memberRepository.findByUsername(anyString()))
                .thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString()))
                .thenReturn("encoded-random-value");
        java.util.concurrent.atomic.AtomicReference<Member> created =
                new java.util.concurrent.atomic.AtomicReference<>();
        when(memberRepository.save(any(Member.class)))
                .thenAnswer(invocation -> {
                    Member saved = invocation.getArgument(0);
                    created.set(saved);
                    return saved;
                });
        when(memberRepository.findByUsernameForUpdate(anyString()))
                .thenAnswer(invocation ->
                        Optional.ofNullable(created.get()));
        when(walletRepository.findForUpdateByMember(any(Member.class)))
                .thenAnswer(invocation ->
                        Optional.of(new Wallet(
                                invocation.getArgument(0),
                                10000.0
                        )));
        when(jwtTokenProvider.createToken(anyString()))
                .thenReturn("google.jwt.token");

        ResponseEntity<?> response = authController.googleLogin(
                Map.of("credential", "google-id-token"));

        assertEquals(200, response.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, Object> body =
                (Map<String, Object>) response.getBody();

        assertEquals("google.jwt.token", body.get("token"));
        assertEquals(true, body.get("needsPinSetup"));
        assertTrue(body.get("username").toString().startsWith("g_"));
        assertEquals("GOOGLE", created.get().getSocialProvider());
        assertEquals(
                "google-subject-123",
                created.get().getSocialSubject()
        );
        assertTrue(created.get().isEmailVerified());
        assertFalse(created.get().isPinConfigured());
        verifyNoInteractions(emailVerificationService);
        verify(walletRepository, times(2)).save(any(Wallet.class));
    }

    @Test
    void refreshFailureDoesNotClearPotentiallyRotatedCookie() {
        when(refreshTokenService.rotate("stale-refresh"))
                .thenThrow(new IllegalArgumentException(
                        "유효하지 않은 refresh token입니다."
                ));

        ResponseEntity<?> response =
                authController.refresh("stale-refresh");

        assertEquals(401, response.getStatusCode().value());
        assertFalse(
                response.getHeaders()
                        .containsKey(org.springframework.http.HttpHeaders.SET_COOKIE)
        );
        verify(refreshTokenService, never()).clearCookie();
    }

    @Test
    void revokeOtherSessionsCreatesSecurityNotification() {
        Member member = new Member(
                "alice",
                "encoded-password",
                "Alice",
                "alice@example.test",
                "encoded-pin"
        );

        when(memberRepository.findByUsernameForUpdate("alice"))
                .thenReturn(Optional.of(member));
        when(refreshTokenService.revokeOtherSessions(
                member,
                "current-refresh"
        )).thenReturn(2L);

        var authentication =
                new org.springframework.security.authentication
                        .UsernamePasswordAuthenticationToken(
                                "alice",
                                null,
                                java.util.List.of()
                        );

        ResponseEntity<?> response =
                authController.revokeOtherSessions(
                        "current-refresh",
                        authentication
                );

        assertEquals(200, response.getStatusCode().value());
        verify(notificationService).create(
                member,
                "SECURITY",
                "다른 로그인 세션 2개가 종료되었습니다."
        );
    }

}
