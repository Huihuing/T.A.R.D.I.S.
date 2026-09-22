package com.tardistock.backend.service;

import com.tardistock.backend.entity.EmailVerification;
import com.tardistock.backend.repository.EmailVerificationRepository;
import com.tardistock.backend.repository.MemberRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class EmailVerificationService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final EmailVerificationRepository verificationRepository;
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;
    private final String mailUsername;

    public EmailVerificationService(
            EmailVerificationRepository verificationRepository,
            MemberRepository memberRepository,
            PasswordEncoder passwordEncoder,
            JavaMailSender mailSender,
            @Value("${spring.mail.username:}") String mailUsername) {
        this.verificationRepository = verificationRepository;
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailSender = mailSender;
        this.mailUsername = mailUsername;
    }

    @Transactional
    public void sendCode(String rawEmail) {
        String email = normalizeEmail(rawEmail);
        if (memberRepository.existsByEmailIgnoreCase(email)) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
        }
        if (mailUsername == null || mailUsername.isBlank()) {
            throw new IllegalStateException(
                    "이메일 발송 설정이 아직 구성되지 않았습니다.");
        }

        LocalDateTime now = LocalDateTime.now(KST);
        EmailVerification verification =
                verificationRepository.findByEmailForUpdate(email)
                        .orElseGet(() -> new EmailVerification(email));

        if (verification.getLastSentAt() != null
                && now.isBefore(verification.getLastSentAt().plusMinutes(1))) {
            throw new IllegalStateException(
                    "인증번호는 1분 후 다시 요청할 수 있습니다.");
        }

        String code = String.format(
                Locale.ROOT,
                "%06d",
                100000 + RANDOM.nextInt(900000)
        );

        verification.setEmail(email);
        verification.setCodeHash(passwordEncoder.encode(code));
        verification.setExpiresAt(now.plusMinutes(10));
        verification.setVerifiedAt(null);
        verification.setLastSentAt(now);
        verification.setAttempts(0);
        verificationRepository.save(verification);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailUsername);
        message.setTo(email);
        message.setSubject("[T.A.R.D.I.S.] 회원가입 이메일 인증번호");
        message.setText(
                "T.A.R.D.I.S. 회원가입 인증번호는 " + code
                        + " 입니다.\n\n"
                        + "인증번호는 10분 동안 유효합니다.\n"
                        + "본인이 요청하지 않았다면 이 메일을 무시해주세요."
        );
        mailSender.send(message);
    }

    @Transactional
    public void verifyCode(String rawEmail, String rawCode) {
        String email = normalizeEmail(rawEmail);
        String code = rawCode == null ? "" : rawCode.trim();

        if (!code.matches("\\d{6}")) {
            throw new IllegalArgumentException("6자리 인증번호를 입력해주세요.");
        }

        EmailVerification verification =
                verificationRepository.findByEmailForUpdate(email)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "먼저 인증번호를 요청해주세요."));

        LocalDateTime now = LocalDateTime.now(KST);
        if (verification.getExpiresAt() == null
                || now.isAfter(verification.getExpiresAt())) {
            throw new IllegalStateException(
                    "인증번호가 만료되었습니다. 다시 요청해주세요.");
        }
        if (verification.getAttempts() >= 5) {
            throw new IllegalStateException(
                    "인증번호 입력 횟수를 초과했습니다. 다시 요청해주세요.");
        }

        verification.setAttempts(verification.getAttempts() + 1);
        if (!passwordEncoder.matches(code, verification.getCodeHash())) {
            verificationRepository.save(verification);
            throw new IllegalArgumentException("인증번호가 일치하지 않습니다.");
        }

        verification.setVerifiedAt(now);
        verificationRepository.save(verification);
    }

    @Transactional
    public boolean consumeVerified(String rawEmail) {
        String email = normalizeEmail(rawEmail);
        EmailVerification verification =
                verificationRepository.findByEmailForUpdate(email)
                        .orElse(null);

        if (verification == null || verification.getVerifiedAt() == null) {
            return false;
        }

        LocalDateTime now = LocalDateTime.now(KST);
        if (now.isAfter(verification.getVerifiedAt().plusMinutes(30))) {
            verificationRepository.delete(verification);
            return false;
        }

        verificationRepository.delete(verification);
        return true;
    }

    public String normalizeEmail(String rawEmail) {
        if (rawEmail == null) {
            throw new IllegalArgumentException("이메일 주소를 입력해주세요.");
        }

        String email = rawEmail.trim().toLowerCase(Locale.ROOT);
        if (email.length() > 254 || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException(
                    "올바른 이메일 주소를 입력해주세요.");
        }
        return email;
    }
}
