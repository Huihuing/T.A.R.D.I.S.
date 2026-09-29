package com.tardistock.backend.service;

import com.tardistock.backend.entity.EmailVerification;
import com.tardistock.backend.entity.Member;
import com.tardistock.backend.entity.PasswordResetCode;
import com.tardistock.backend.repository.EmailVerificationRepository;
import com.tardistock.backend.repository.MemberRepository;
import com.tardistock.backend.repository.PasswordResetCodeRepository;
import com.tardistock.backend.support.RecordingTransactionManager;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * A wrong code must persist the failed-attempt counter. If the surrounding
 * transaction rolled back, the counter would never reach the 5-attempt limit
 * and a 6-digit code could be brute-forced.
 */
class VerificationCodeAttemptTransactionTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final String EMAIL = "alice@example.test";

    private final PasswordResetCodeRepository resetRepository =
            mock(PasswordResetCodeRepository.class);
    private final MemberRepository memberRepository =
            mock(MemberRepository.class);
    private final PasswordEncoder passwordEncoder =
            mock(PasswordEncoder.class);
    private final RecordingTransactionManager transactions =
            new RecordingTransactionManager();

    @Test
    void wrongSecurityCodeCommitsIncrementedAttempts() {
        PasswordResetCode reset = activeResetCode();
        when(resetRepository.findByEmailForUpdate(EMAIL))
                .thenReturn(Optional.of(reset));
        when(passwordEncoder.matches(anyString(), anyString()))
                .thenReturn(false);

        assertThrows(
                VerificationCodeRejectedException.class,
                () -> passwordResetService()
                        .consumeSecurityCode(member(), "000000")
        );

        assertEquals(1, reset.getAttempts());
        verify(resetRepository).save(reset);
        assertCommitted();
    }

    @Test
    void expiredSecurityCodeCommitsCleanup() {
        PasswordResetCode reset = activeResetCode();
        reset.setExpiresAt(LocalDateTime.now(KST).minusMinutes(1));
        when(resetRepository.findByEmailForUpdate(EMAIL))
                .thenReturn(Optional.of(reset));

        assertThrows(
                VerificationCodeExpiredException.class,
                () -> passwordResetService()
                        .consumeSecurityCode(member(), "000000")
        );

        verify(resetRepository).delete(reset);
        assertCommitted();
    }

    @Test
    void wrongPasswordResetCodeCommitsIncrementedAttempts() {
        PasswordResetCode reset = activeResetCode();
        when(memberRepository.findByEmailIgnoreCase(EMAIL))
                .thenReturn(Optional.of(member()));
        when(resetRepository.findByEmailForUpdate(EMAIL))
                .thenReturn(Optional.of(reset));
        when(passwordEncoder.matches(anyString(), anyString()))
                .thenReturn(false);

        assertThrows(
                VerificationCodeRejectedException.class,
                () -> passwordResetService().resetPassword(
                        EMAIL,
                        "000000",
                        "new-password-123"
                )
        );

        assertEquals(1, reset.getAttempts());
        verify(resetRepository).save(reset);
        assertCommitted();
    }

    @Test
    void otherPasswordResetFailuresStillRollBack() {
        assertThrows(
                IllegalArgumentException.class,
                () -> passwordResetService().resetPassword(
                        EMAIL,
                        "123456",
                        "short"
                )
        );

        assertEquals(0, transactions.commits());
        assertEquals(1, transactions.rollbacks());
    }

    @Test
    void wrongEmailVerificationCodeCommitsIncrementedAttempts() {
        EmailVerificationRepository verificationRepository =
                mock(EmailVerificationRepository.class);
        EmailVerification verification = new EmailVerification(EMAIL);
        verification.setCodeHash("stored-hash");
        verification.setExpiresAt(LocalDateTime.now(KST).plusMinutes(5));
        verification.setAttempts(0);
        when(verificationRepository.findByEmailForUpdate(EMAIL))
                .thenReturn(Optional.of(verification));
        when(passwordEncoder.matches("000000", "stored-hash"))
                .thenReturn(false);

        EmailVerificationService service = transactions.proxy(
                new EmailVerificationService(
                        verificationRepository,
                        memberRepository,
                        passwordEncoder,
                        mock(JavaMailSender.class),
                        "sender@example.test",
                        SendQuotaTestSupport.unlimited()
                )
        );

        assertThrows(
                VerificationCodeRejectedException.class,
                () -> service.verifyCode(EMAIL, "000000")
        );

        assertEquals(1, verification.getAttempts());
        verify(verificationRepository).save(verification);
        assertCommitted();
    }

    private PasswordResetService passwordResetService() {
        return transactions.proxy(new PasswordResetService(
                resetRepository,
                memberRepository,
                passwordEncoder,
                mock(RefreshTokenService.class),
                mock(NotificationService.class),
                mock(JavaMailSender.class),
                "sender@example.test",
                SendQuotaTestSupport.unlimited()
        ));
    }

    private static PasswordResetCode activeResetCode() {
        PasswordResetCode reset = new PasswordResetCode(EMAIL);
        reset.setCodeHash("stored-hash");
        reset.setExpiresAt(LocalDateTime.now(KST).plusMinutes(5));
        reset.setAttempts(0);
        return reset;
    }

    private static Member member() {
        return new Member(
                "alice",
                "encoded-password",
                "Alice",
                EMAIL,
                "encoded-pin"
        );
    }

    private void assertCommitted() {
        assertEquals(1, transactions.commits());
        assertEquals(0, transactions.rollbacks());
    }
}
