package com.tardistock.backend.service;

import com.tardistock.backend.entity.EmailVerification;
import com.tardistock.backend.repository.EmailVerificationRepository;
import com.tardistock.backend.repository.MemberRepository;
import org.junit.jupiter.api.Test;
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
