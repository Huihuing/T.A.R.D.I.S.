package com.tardistock.backend.service;

import com.tardistock.backend.entity.EmailCodeSendQuota;
import com.tardistock.backend.entity.Member;
import com.tardistock.backend.entity.PasswordResetCode;
import com.tardistock.backend.repository.EmailCodeSendQuotaRepository;
import com.tardistock.backend.repository.EmailVerificationRepository;
import com.tardistock.backend.repository.MemberRepository;
import com.tardistock.backend.repository.PasswordResetCodeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EmailCodeSendQuotaServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final String EMAIL = "alice@example.test";
    private static final int LIMIT = 3;

    private final EmailCodeSendQuotaRepository quotaRepository =
            mock(EmailCodeSendQuotaRepository.class);
    private final EmailCodeSendQuotaService quotaService =
            new EmailCodeSendQuotaService(quotaRepository, LIMIT);
    private final LocalDateTime now = LocalDateTime.now(KST);

    @Test
    void firstSendCreatesQuotaWithOneSend() {
        assertTrue(quotaService.tryConsume(EMAIL, now));

        verify(quotaRepository).save(argThat(quota ->
                EMAIL.equals(quota.getEmail())
                        && quota.getSendCount() == 1
                        && now.equals(quota.getWindowStartedAt())
        ));
    }

    @Test
    void rejectsOnceLimitIsReachedWithinWindow() {
        EmailCodeSendQuota quota = quota(LIMIT, now.minusHours(23));

        assertFalse(quotaService.tryConsume(EMAIL, now));

        assertEquals(LIMIT, quota.getSendCount());
        verify(quotaRepository, never()).save(any());
    }

    @Test
    void startsNewWindowAfter24Hours() {
        EmailCodeSendQuota quota = quota(LIMIT, now.minusHours(24));

        assertTrue(quotaService.tryConsume(EMAIL, now));

        assertEquals(1, quota.getSendCount());
        assertEquals(now, quota.getWindowStartedAt());
        verify(quotaRepository).save(quota);
    }

    @Test
    void rejectsNonPositiveLimit() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new EmailCodeSendQuotaService(quotaRepository, 0)
        );
    }

    @Test
    void signupCodeOverLimitIsRejectedWithoutMail() {
        quota(LIMIT, now);
        EmailVerificationRepository verificationRepository =
                mock(EmailVerificationRepository.class);
        JavaMailSender mailSender = mock(JavaMailSender.class);
        when(verificationRepository.findByEmailForUpdate(EMAIL))
                .thenReturn(Optional.empty());

        EmailVerificationService service = new EmailVerificationService(
                verificationRepository,
                mock(MemberRepository.class),
                mock(PasswordEncoder.class),
                mailSender,
                "sender@example.test",
                quotaService
        );

        assertThrows(
                EmailCodeSendLimitException.class,
                () -> service.sendCode(EMAIL)
        );

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
        verify(verificationRepository, never()).save(any());
    }

    @Test
    void passwordResetCodeOverLimitKeepsGenericSuccessWithoutMail() {
        quota(LIMIT, now);
        MemberRepository memberRepository = mock(MemberRepository.class);
        PasswordResetCodeRepository resetRepository =
                mock(PasswordResetCodeRepository.class);
        JavaMailSender mailSender = mock(JavaMailSender.class);
        when(memberRepository.findByEmailIgnoreCase(EMAIL))
                .thenReturn(Optional.of(member()));
        when(resetRepository.findByEmailForUpdate(EMAIL))
                .thenReturn(Optional.empty());

        // No exception: the response must not reveal whether an account
        // exists or has hit its quota.
        passwordResetService(memberRepository, resetRepository, mailSender)
                .sendCode(EMAIL);

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
        verify(resetRepository, never()).save(any());
    }

    @Test
    void securityCodeOverLimitIsRejectedWithoutMail() {
        quota(LIMIT, now);
        PasswordResetCodeRepository resetRepository =
                mock(PasswordResetCodeRepository.class);
        JavaMailSender mailSender = mock(JavaMailSender.class);
        when(resetRepository.findByEmailForUpdate(EMAIL))
                .thenReturn(Optional.of(new PasswordResetCode(EMAIL)));
        Member member = member();
        member.setEmailVerified(true);

        assertThrows(
                EmailCodeSendLimitException.class,
                () -> passwordResetService(
                        mock(MemberRepository.class),
                        resetRepository,
                        mailSender
                ).sendSecurityCode(member)
        );

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
        verify(resetRepository, never()).save(any());
    }

    private EmailCodeSendQuota quota(int sendCount, LocalDateTime start) {
        EmailCodeSendQuota quota = new EmailCodeSendQuota(EMAIL, start);
        quota.setSendCount(sendCount);
        when(quotaRepository.findByEmailForUpdate(EMAIL))
                .thenReturn(Optional.of(quota));
        return quota;
    }

    private PasswordResetService passwordResetService(
            MemberRepository memberRepository,
            PasswordResetCodeRepository resetRepository,
            JavaMailSender mailSender) {
        return new PasswordResetService(
                resetRepository,
                memberRepository,
                mock(PasswordEncoder.class),
                mock(RefreshTokenService.class),
                mock(NotificationService.class),
                mailSender,
                "sender@example.test",
                quotaService
        );
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
}
