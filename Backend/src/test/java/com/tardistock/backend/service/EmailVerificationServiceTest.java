package com.tardistock.backend.service;

import com.tardistock.backend.entity.EmailVerification;
import com.tardistock.backend.repository.EmailVerificationRepository;
import com.tardistock.backend.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EmailVerificationServiceTest {

    @Test
    void sendsHashedCodeWithoutPersistingPlainCode() {
        EmailVerificationRepository repository =
                mock(EmailVerificationRepository.class);
        MemberRepository members = mock(MemberRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        JavaMailSender sender = mock(JavaMailSender.class);

        when(repository.findByEmailForUpdate("alice@example.com"))
                .thenReturn(Optional.empty());
        when(encoder.encode(anyString())).thenReturn("hashed-code");

        EmailVerificationService service =
                new EmailVerificationService(
                        repository,
                        members,
                        encoder,
                        sender,
                        "noreply@example.com"
                );

        service.sendCode(" Alice@Example.COM ");

        verify(sender).send(any(SimpleMailMessage.class));
        verify(repository).save(argThat(v ->
                "alice@example.com".equals(v.getEmail())
                        && "hashed-code".equals(v.getCodeHash())
                        && v.getVerifiedAt() == null
        ));
    }

    @Test
    void mailFailureBecomesServiceUnavailableState() {
        EmailVerificationRepository repository =
                mock(EmailVerificationRepository.class);
        MemberRepository members = mock(MemberRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        JavaMailSender sender = mock(JavaMailSender.class);

        when(repository.findByEmailForUpdate("alice@example.com"))
                .thenReturn(Optional.empty());
        when(encoder.encode(anyString())).thenReturn("hashed-code");
        doThrow(new MailSendException("smtp unavailable"))
                .when(sender)
                .send(any(SimpleMailMessage.class));

        EmailVerificationService service =
                new EmailVerificationService(
                        repository,
                        members,
                        encoder,
                        sender,
                        "noreply@example.com"
                );

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service.sendCode("alice@example.com")
        );

        assertEquals(
                "이메일 발송 서비스를 일시적으로 사용할 수 없습니다.",
                error.getMessage()
        );
    }

    @Test
    void consumeVerifiedRejectsUnverifiedEmail() {
        EmailVerificationRepository repository =
                mock(EmailVerificationRepository.class);
        MemberRepository members = mock(MemberRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        JavaMailSender sender = mock(JavaMailSender.class);

        when(repository.findByEmailForUpdate("alice@example.com"))
                .thenReturn(Optional.empty());

        EmailVerificationService service =
                new EmailVerificationService(
                        repository,
                        members,
                        encoder,
                        sender,
                        "noreply@example.com"
                );

        assertFalse(service.consumeVerified("alice@example.com"));
    }
}
