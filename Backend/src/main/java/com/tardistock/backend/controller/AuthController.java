package com.tardistock.backend.controller;

import com.tardistock.backend.entity.Member;
import com.tardistock.backend.entity.Wallet;
import com.tardistock.backend.repository.MemberRepository;
import com.tardistock.backend.repository.WalletRepository;
import com.tardistock.backend.security.JwtTokenProvider;
import com.tardistock.backend.service.LedgerService;
import com.tardistock.backend.service.EmailVerificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{3,20}$");
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$");

    private final MemberRepository memberRepository;
    private final WalletRepository walletRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final LedgerService ledgerService;
    private final EmailVerificationService emailVerificationService;

    public AuthController(MemberRepository memberRepository,
                          WalletRepository walletRepository,
                          PasswordEncoder passwordEncoder,
                          JwtTokenProvider jwtTokenProvider,
                          LedgerService ledgerService,
                          EmailVerificationService emailVerificationService) {
        this.memberRepository = memberRepository;
        this.walletRepository = walletRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.ledgerService = ledgerService;
        this.emailVerificationService = emailVerificationService;
    }

    @PostMapping("/email/send")
    public ResponseEntity<?> sendEmailVerification(
            @RequestBody Map<String, String> request) {
        try {
            emailVerificationService.sendCode(request.get("email"));
            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "message", "인증번호를 이메일로 전송했습니다."
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(503).body(
                    Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/email/verify")
    public ResponseEntity<?> verifyEmail(
            @RequestBody Map<String, String> request) {
        try {
            emailVerificationService.verifyCode(
                    request.get("email"),
                    request.get("code")
            );
            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "message", "이메일 인증이 완료되었습니다."
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(410).body(
                    Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/register")
    @Transactional
    public ResponseEntity<?> register(@RequestBody Map<String, String> request) {
        String username = normalize(request.get("username"));
        String password = request.get("password");
        String name = normalize(request.get("name"));
        String email = normalize(request.get("email"));
        String pin = normalize(request.get("pin"));

        if (username == null || password == null || name == null || email == null || pin == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "모든 필드를 입력해주세요."));
        }
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "아이디는 영문, 숫자, 밑줄(_)만 사용하여 3~20자로 입력해주세요."));
        }
        if (password.length() < 8 || password.length() > 64) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "비밀번호는 8~64자로 입력해주세요."));
        }
        if (name.length() > 40) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "이름은 40자 이하로 입력해주세요."));
        }

        String normalizedEmail = email.toLowerCase(Locale.ROOT);
        if (normalizedEmail.length() > 254 || !EMAIL_PATTERN.matcher(normalizedEmail).matches()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "올바른 이메일 주소를 입력해주세요."));
        }
        if (!pin.matches("\\d{4}")) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "계좌 PIN은 숫자 4자리로 입력해주세요."));
        }

        if (memberRepository.findByUsername(username).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("message", "이미 존재하는 아이디입니다."));
        }
        if (memberRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            return ResponseEntity.badRequest().body(Map.of("message", "이미 사용 중인 이메일입니다."));
        }
        if (!emailVerificationService.consumeVerified(normalizedEmail)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "이메일 인증을 완료한 뒤 회원가입해주세요."
            ));
        }

        Member member = new Member(
                username,
                passwordEncoder.encode(password),
                name,
                normalizedEmail,
                passwordEncoder.encode(pin)
        );
        memberRepository.save(member);
        Wallet wallet = new Wallet(member, 10000.0);
        walletRepository.save(wallet);
        ledgerService.record(
                member,
                "INITIAL_BALANCE",
                10000.0,
                wallet.getBalance(),
                "회원가입 초기 가상자금"
        );

        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "회원가입이 완료되었습니다."
        ));
    }

    @PostMapping("/login")
    @Transactional
    public ResponseEntity<?> login(@RequestBody Map<String, String> request) {
        String username = normalize(request.get("username"));
        String password = request.get("password");

        if (username == null || password == null || password.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "아이디와 비밀번호를 입력해주세요."));
        }

        return memberRepository.findByUsernameForUpdate(username)
                .map(member -> {
                    if (!passwordEncoder.matches(password, member.getPassword())) {
                        return ResponseEntity.status(401).body(
                                Map.of("message", "비밀번호가 일치하지 않습니다."));
                    }

                    String token = jwtTokenProvider.createToken(username);
                    boolean dailyReward = false;
                    LocalDate today = LocalDate.now(KST);

                    if (member.getLastLoginDate() == null || !member.getLastLoginDate().equals(today)) {
                        Wallet wallet = walletRepository.findForUpdateByMember(member).orElse(null);
                        if (wallet != null) {
                            wallet.setBalance(wallet.getBalance() + 500.0);
                            walletRepository.save(wallet);
                            ledgerService.record(
                                    member,
                                    "DAILY_LOGIN_REWARD",
                                    500.0,
                                    wallet.getBalance(),
                                    "일일 로그인 보상"
                            );
                            member.setLastLoginDate(today);
                            memberRepository.save(member);
                            dailyReward = true;
                        }
                    }

                    return ResponseEntity.ok(Map.of(
                            "token", token,
                            "username", username,
                            "dailyReward", dailyReward
                    ));
                })
                .orElseGet(() -> ResponseEntity.status(401).body(
                        Map.of("message", "존재하지 않는 아이디입니다.")));
    }

    private String normalize(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
