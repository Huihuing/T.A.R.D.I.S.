package com.tardistock.backend.service;

import com.tardistock.backend.entity.Member;
import com.tardistock.backend.entity.PasswordResetCode;
import com.tardistock.backend.repository.MemberRepository;
import com.tardistock.backend.repository.PasswordResetCodeRepository;
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
public class PasswordResetService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final PasswordResetCodeRepository resetRepository;
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final JavaMailSender mailSender;
    private final String mailUsername;

    public PasswordResetService(
            PasswordResetCodeRepository resetRepository,
            MemberRepository memberRepository,
            PasswordEncoder passwordEncoder,
            RefreshTokenService refreshTokenService,
            JavaMailSender mailSender,
            @Value("${spring.mail.username:}") String mailUsername) {
        this.resetRepository = resetRepository;
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
        this.mailSender = mailSender;
        this.mailUsername = mailUsername;
    }

    @Transactional
    public void sendCode(String rawEmail) {
        String email = normalizeEmail(rawEmail);

        if (mailUsername == null || mailUsername.isBlank()) {
            throw new IllegalStateException(
                    "이메일 발송 설정이 아직 구성되지 않았습니다.");
        }

        Member member = memberRepository.findByEmailIgnoreCase(email)
                .orElse(null);

        // 계정 존재 여부와 SNS 전용 계정 여부는 응답에서 노출하지 않습니다.
        if (member == null || !member.isPasswordLoginEnabled()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now(KST);
        PasswordResetCode reset = resetRepository
                .findByEmailIgnoreCase(email)
                .orElseGet(() -> new PasswordResetCode(email));

        // 재발송 쿨다운 중에는 동일한 성공 응답을 유지합니다.
        if (reset.getLastSentAt() != null
                && now.isBefore(reset.getLastSentAt().plusMinutes(1))) {
            return;
        }

        String code = String.format(
                Locale.ROOT,
                "%06d",
                100000 + RANDOM.nextInt(900000)
        );

        reset.setEmail(email);
        reset.setCodeHash(passwordEncoder.encode(code + ":PASSWORD_RESET"));
        reset.setExpiresAt(now.plusMinutes(10));
        reset.setLastSentAt(now);
        reset.setAttempts(0);
        resetRepository.save(reset);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailUsername);
        message.setTo(email);
        message.setSubject("[T.A.R.D.I.S.] 비밀번호 재설정 인증번호");
        message.setText(
                "T.A.R.D.I.S. 비밀번호 재설정 인증번호는 "
                        + code + " 입니다.\n\n"
                        + "인증번호는 10분 동안 유효합니다.\n"
                        + "본인이 요청하지 않았다면 이 메일을 무시해주세요."
        );
        mailSender.send(message);
    }

    @Transactional
    public void sendSecurityCode(Member member) {
        if (member == null) {
            throw new IllegalArgumentException(
                    "사용자를 찾을 수 없습니다.");
        }
        if (!member.isEmailVerified()) {
            throw new IllegalArgumentException(
                    "이메일 인증이 완료된 계정에서만 사용할 수 있습니다.");
        }
        if (mailUsername == null || mailUsername.isBlank()) {
            throw new IllegalStateException(
                    "이메일 발송 설정이 아직 구성되지 않았습니다.");
        }

        String email = normalizeEmail(member.getEmail());
        LocalDateTime now = LocalDateTime.now(KST);
        PasswordResetCode reset = resetRepository
                .findByEmailIgnoreCase(email)
                .orElseGet(() -> new PasswordResetCode(email));

        if (reset.getLastSentAt() != null
                && now.isBefore(reset.getLastSentAt().plusMinutes(1))) {
            return;
        }

        String code = String.format(
                Locale.ROOT,
                "%06d",
                100000 + RANDOM.nextInt(900000)
        );

        reset.setEmail(email);
        reset.setCodeHash(passwordEncoder.encode(code + ":ACCOUNT_SECURITY"));
        reset.setExpiresAt(now.plusMinutes(10));
        reset.setLastSentAt(now);
        reset.setAttempts(0);
        resetRepository.save(reset);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailUsername);
        message.setTo(email);
        message.setSubject("[T.A.R.D.I.S.] 계정 보안 인증번호");
        message.setText(
                "T.A.R.D.I.S. 계정 보안 인증번호는 "
                        + code + " 입니다.\n\n"
                        + "인증번호는 10분 동안 유효합니다.\n"
                        + "PIN 재설정 또는 로그인 방식 변경 요청에 사용됩니다.\n"
                        + "본인이 요청하지 않았다면 이 메일을 무시해주세요."
        );
        mailSender.send(message);
    }

    @Transactional
    public void consumeSecurityCode(
            Member member,
            String rawCode) {
        if (member == null) {
            throw new IllegalArgumentException(
                    "사용자를 찾을 수 없습니다.");
        }

        String email = normalizeEmail(member.getEmail());
        String code = rawCode == null ? "" : rawCode.trim();

        if (!code.matches("\\d{6}")) {
            throw invalidRequest();
        }

        PasswordResetCode reset = resetRepository
                .findByEmailIgnoreCase(email)
                .orElseThrow(this::invalidRequest);

        LocalDateTime now = LocalDateTime.now(KST);
        if (reset.getExpiresAt() == null
                || now.isAfter(reset.getExpiresAt())) {
            resetRepository.delete(reset);
            throw new IllegalStateException(
                    "인증번호가 만료되었습니다. 다시 요청해주세요.");
        }

        if (reset.getAttempts() >= 5) {
            resetRepository.delete(reset);
            throw new IllegalStateException(
                    "인증번호 입력 횟수를 초과했습니다. 다시 요청해주세요.");
        }

        reset.setAttempts(reset.getAttempts() + 1);
        if (!passwordEncoder.matches(
                code + ":ACCOUNT_SECURITY",
                reset.getCodeHash())) {
            resetRepository.save(reset);
            throw invalidRequest();
        }

        resetRepository.delete(reset);
    }

    @Transactional
    public void resetPassword(
            String rawEmail,
            String rawCode,
            String newPassword) {
        String email = normalizeEmail(rawEmail);
        String code = rawCode == null ? "" : rawCode.trim();

        if (!code.matches("\\d{6}")) {
            throw invalidRequest();
        }
        if (newPassword == null
                || newPassword.length() < 8
                || newPassword.length() > 64) {
            throw new IllegalArgumentException(
                    "새 비밀번호는 8~64자로 입력해주세요.");
        }

        Member member = memberRepository.findByEmailIgnoreCase(email)
                .orElseThrow(this::invalidRequest);
        if (!member.isPasswordLoginEnabled()) {
            throw invalidRequest();
        }

        PasswordResetCode reset = resetRepository
                .findByEmailIgnoreCase(email)
                .orElseThrow(this::invalidRequest);

        LocalDateTime now = LocalDateTime.now(KST);
        if (reset.getExpiresAt() == null
                || now.isAfter(reset.getExpiresAt())) {
            resetRepository.delete(reset);
            throw new IllegalStateException(
                    "인증번호가 만료되었습니다. 다시 요청해주세요.");
        }

        if (reset.getAttempts() >= 5) {
            resetRepository.delete(reset);
            throw new IllegalStateException(
                    "인증번호 입력 횟수를 초과했습니다. 다시 요청해주세요.");
        }

        reset.setAttempts(reset.getAttempts() + 1);
        if (!passwordEncoder.matches(
                code + ":PASSWORD_RESET",
                reset.getCodeHash())) {
            resetRepository.save(reset);
            throw invalidRequest();
        }

        member.setPassword(passwordEncoder.encode(newPassword));
        memberRepository.save(member);
        refreshTokenService.revokeAll(member);
        resetRepository.delete(reset);
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

    private IllegalArgumentException invalidRequest() {
        return new IllegalArgumentException(
                "인증번호 또는 요청 정보가 올바르지 않습니다.");
    }
}
