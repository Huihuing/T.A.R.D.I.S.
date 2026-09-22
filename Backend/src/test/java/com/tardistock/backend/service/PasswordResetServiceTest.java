package com.tardistock.backend.service;

import com.tardistock.backend.entity.Member;
import com.tardistock.backend.entity.PasswordResetCode;
import com.tardistock.backend.repository.MemberRepository;
import com.tardistock.backend.repository.PasswordResetCodeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class PasswordResetServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Test
    void passwordResetMailFailureBecomesServiceUnavailableState() {
        PasswordResetCodeRepository resetRepository =
                mock(PasswordResetCodeRepository.class);
        MemberRepository memberRepository =
                mock(MemberRepository.class);
        PasswordEncoder passwordEncoder =
                mock(PasswordEncoder.class);
        RefreshTokenService refreshTokenService =
                mock(RefreshTokenService.class);
        NotificationService notificationService =
                mock(NotificationService.class);
        JavaMailSender mailSender = mock(JavaMailSender.class);

        Member member = new Member(
                "alice",
                "encoded-password",
                "Alice",
                "alice@example.test",
                "encoded-pin"
        );

        when(memberRepository.findByEmailIgnoreCase(
                "alice@example.test"
        )).thenReturn(Optional.of(member));
        when(resetRepository.findByEmailForUpdate(
                "alice@example.test"
        )).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString()))
                .thenReturn("hashed-code");
        doThrow(new MailSendException("smtp unavailable"))
                .when(mailSender)
                .send(any(SimpleMailMessage.class));

        PasswordResetService service = new PasswordResetService(
                resetRepository,
                memberRepository,
                passwordEncoder,
                refreshTokenService,
                notificationService,
                mailSender,
                "sender@example.test"
        );

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service.sendCode("alice@example.test")
        );

        assertEquals(
                "이메일 발송 서비스를 일시적으로 사용할 수 없습니다.",
                error.getMessage()
        );
    }

    @Test
    void securityCodeUsesAccountSecurityPurpose() {
        PasswordResetCodeRepository resetRepository =
                mock(PasswordResetCodeRepository.class);
        MemberRepository memberRepository =
                mock(MemberRepository.class);
        PasswordEncoder passwordEncoder =
                mock(PasswordEncoder.class);
        RefreshTokenService refreshTokenService =
                mock(RefreshTokenService.class);
        NotificationService notificationService =
                mock(NotificationService.class);
        JavaMailSender mailSender = mock(JavaMailSender.class);

        Member member = new Member(
                "alice",
                "encoded-password",
                "Alice",
                "alice@example.test",
                "encoded-pin"
        );

        PasswordResetCode reset =
                new PasswordResetCode("alice@example.test");
        reset.setCodeHash("stored-hash");
        reset.setExpiresAt(LocalDateTime.now(KST).plusMinutes(5));
        reset.setAttempts(0);

        when(resetRepository.findByEmailForUpdate("alice@example.test"))
                .thenReturn(Optional.of(reset));
        when(passwordEncoder.matches(
                "123456:ACCOUNT_SECURITY",
                "stored-hash"
        )).thenReturn(true);

        PasswordResetService service = new PasswordResetService(
                resetRepository,
                memberRepository,
                passwordEncoder,
                refreshTokenService,
                notificationService,
                mailSender,
                "sender@example.test"
        );

        service.consumeSecurityCode(member, "123456");

        verify(passwordEncoder).matches(
                "123456:ACCOUNT_SECURITY",
                "stored-hash"
        );
        verify(resetRepository).delete(reset);
        verify(refreshTokenService, never()).revokeAll(any());
    }

    @Test
    void passwordResetUsesPasswordResetPurpose() {
        PasswordResetCodeRepository resetRepository =
                mock(PasswordResetCodeRepository.class);
        MemberRepository memberRepository =
                mock(MemberRepository.class);
        PasswordEncoder passwordEncoder =
                mock(PasswordEncoder.class);
        RefreshTokenService refreshTokenService =
                mock(RefreshTokenService.class);
        NotificationService notificationService =
                mock(NotificationService.class);
        JavaMailSender mailSender = mock(JavaMailSender.class);

        Member member = new Member(
                "alice",
                "encoded-old-password",
                "Alice",
                "alice@example.test",
                "encoded-pin"
        );

        PasswordResetCode reset =
                new PasswordResetCode("alice@example.test");
        reset.setCodeHash("stored-hash");
        reset.setExpiresAt(LocalDateTime.now(KST).plusMinutes(5));
        reset.setAttempts(0);

        when(memberRepository.findByEmailIgnoreCase("alice@example.test"))
                .thenReturn(Optional.of(member));
        when(resetRepository.findByEmailForUpdate("alice@example.test"))
                .thenReturn(Optional.of(reset));
        when(passwordEncoder.matches(
                "654321:PASSWORD_RESET",
                "stored-hash"
        )).thenReturn(true);
        when(passwordEncoder.encode("new-password"))
                .thenReturn("encoded-new-password");

        PasswordResetService service = new PasswordResetService(
                resetRepository,
                memberRepository,
                passwordEncoder,
                refreshTokenService,
                notificationService,
                mailSender,
                "sender@example.test"
        );

        service.resetPassword(
                "alice@example.test",
                "654321",
                "new-password"
        );

        verify(passwordEncoder).matches(
                "654321:PASSWORD_RESET",
                "stored-hash"
        );
        verify(memberRepository).save(member);
        verify(notificationService).create(
                member,
                "SECURITY",
                "이메일 인증을 통한 비밀번호 재설정이 완료되었습니다."
        );
        verify(refreshTokenService).revokeAll(member);
        verify(resetRepository).delete(reset);
    }
}
