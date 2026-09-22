package com.tardistock.backend.controller;

import com.tardistock.backend.entity.Member;
import com.tardistock.backend.entity.Wallet;
import com.tardistock.backend.repository.MemberRepository;
import com.tardistock.backend.repository.WalletRepository;
import com.tardistock.backend.security.JwtTokenProvider;
import com.tardistock.backend.service.EmailVerificationService;
import com.tardistock.backend.service.GoogleIdentityService;
import com.tardistock.backend.service.LedgerService;
import com.tardistock.backend.service.NotificationService;
import com.tardistock.backend.service.RefreshTokenService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final String GOOGLE = "GOOGLE";
    private static final Pattern USERNAME_PATTERN =
            Pattern.compile("^[A-Za-z0-9_]{3,20}$");
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$");

    private final MemberRepository memberRepository;
    private final WalletRepository walletRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final LedgerService ledgerService;
    private final EmailVerificationService emailVerificationService;
    private final GoogleIdentityService googleIdentityService;
    private final RefreshTokenService refreshTokenService;
    private final NotificationService notificationService;

    public AuthController(
            MemberRepository memberRepository,
            WalletRepository walletRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider,
            LedgerService ledgerService,
            EmailVerificationService emailVerificationService,
            GoogleIdentityService googleIdentityService,
            RefreshTokenService refreshTokenService,
            NotificationService notificationService) {
        this.memberRepository = memberRepository;
        this.walletRepository = walletRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.ledgerService = ledgerService;
        this.emailVerificationService = emailVerificationService;
        this.googleIdentityService = googleIdentityService;
        this.refreshTokenService = refreshTokenService;
        this.notificationService = notificationService;
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
    public ResponseEntity<?> register(
            @RequestBody Map<String, String> request) {
        String username = normalize(request.get("username"));
        String password = request.get("password");
        String name = normalize(request.get("name"));
        String email = normalize(request.get("email"));
        String pin = normalize(request.get("pin"));

        if (username == null || password == null || name == null
                || email == null || pin == null) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", "모든 필드를 입력해주세요."));
        }
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message",
                    "아이디는 영문, 숫자, 밑줄(_)만 사용하여 3~20자로 입력해주세요."));
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
        if (normalizedEmail.length() > 254
                || !EMAIL_PATTERN.matcher(normalizedEmail).matches()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "올바른 이메일 주소를 입력해주세요."));
        }
        if (!pin.matches("\\d{4}")) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "계좌 PIN은 숫자 4자리로 입력해주세요."));
        }

        if (memberRepository.findByUsername(username).isPresent()) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", "이미 존재하는 아이디입니다."));
        }
        if (memberRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", "이미 사용 중인 이메일입니다."));
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
        member.setEmailVerified(true);
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
    public ResponseEntity<?> login(
            @RequestBody Map<String, String> request) {
        String username = normalize(request.get("username"));
        String password = request.get("password");

        if (username == null || password == null || password.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "아이디와 비밀번호를 입력해주세요."));
        }

        return memberRepository.findByUsernameForUpdate(username)
                .map(member -> {
                    if (!member.isPasswordLoginEnabled()) {
                        return ResponseEntity.status(401).body(
                                Map.of("message",
                                        "이 계정은 Google 로그인을 사용해주세요."));
                    }
                    if (!passwordEncoder.matches(
                            password, member.getPassword())) {
                        return ResponseEntity.status(401).body(
                                Map.of("message",
                                        "비밀번호가 일치하지 않습니다."));
                    }
                    return completeLogin(member);
                })
                .orElseGet(() -> ResponseEntity.status(401).body(
                        Map.of("message",
                                "존재하지 않는 아이디입니다.")));
    }

    @PostMapping("/google")
    @Transactional
    public ResponseEntity<?> googleLogin(
            @RequestBody Map<String, String> request) {
        try {
            GoogleIdentityService.GoogleIdentity identity =
                    googleIdentityService.verify(
                            request.get("credential"));

            Member member = memberRepository
                    .findBySocialProviderAndSocialSubject(
                            GOOGLE, identity.subject())
                    .orElse(null);

            if (member == null) {
                member = memberRepository
                        .findByEmailIgnoreCase(identity.email())
                        .orElse(null);
            }

            if (member == null) {
                member = createGoogleMember(identity);
            } else {
                String provider = member.getSocialProvider();
                if (provider != null
                        && !provider.isBlank()
                        && !GOOGLE.equals(provider)) {
                    return ResponseEntity.status(409).body(Map.of(
                            "message",
                            "이미 다른 SNS 계정과 연결된 이메일입니다."
                    ));
                }
                member.setSocialProvider(GOOGLE);
                member.setSocialSubject(identity.subject());
                member.setEmailVerified(true);
                memberRepository.save(member);
            }

            Member locked = memberRepository
                    .findByUsernameForUpdate(member.getUsername())
                    .orElse(member);

            return completeLogin(locked);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(401).body(
                    Map.of("message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(503).body(
                    Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(
            @CookieValue(
                    name = RefreshTokenService.COOKIE_NAME,
                    required = false
            ) String refreshToken) {
        try {
            RefreshTokenService.RotatedSession session =
                    refreshTokenService.rotate(refreshToken);

            Member member = session.member();
            String accessToken = jwtTokenProvider.createToken(
                    member.getUsername()
            );

            return ResponseEntity.ok()
                    .header(
                            HttpHeaders.SET_COOKIE,
                            refreshTokenService
                                    .buildCookie(session.rawToken())
                                    .toString()
                    )
                    .body(Map.of(
                            "token", accessToken,
                            "username", member.getUsername(),
                            "needsPinSetup", !member.isPinConfigured()
                    ));
        } catch (IllegalArgumentException e) {
            // Do not clear the cookie here. Another browser tab may have
            // rotated the shared refresh cookie while this request was
            // in flight. Logout remains responsible for explicit removal.
            return ResponseEntity.status(401)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(
            @CookieValue(
                    name = RefreshTokenService.COOKIE_NAME,
                    required = false
            ) String refreshToken) {
        refreshTokenService.revoke(refreshToken);

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.SET_COOKIE,
                        refreshTokenService
                                .clearCookie()
                                .toString()
                )
                .body(Map.of(
                        "status", "SUCCESS",
                        "message", "로그아웃되었습니다."
                ));
    }

    @GetMapping("/sessions")
    public ResponseEntity<?> sessions(
            @CookieValue(
                    name = RefreshTokenService.COOKIE_NAME,
                    required = false
            ) String refreshToken,
            Authentication authentication) {
        try {
            Member member = memberRepository.findByUsername(
                            requireUsername(authentication))
                    .orElseThrow(() ->
                            new IllegalArgumentException(
                                    "사용자를 찾을 수 없습니다."));

            return ResponseEntity.ok(
                    refreshTokenService.sessions(
                            member,
                            refreshToken
                    )
            );
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(401).body(
                    Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/sessions/revoke-others")
    @Transactional
    public ResponseEntity<?> revokeOtherSessions(
            @CookieValue(
                    name = RefreshTokenService.COOKIE_NAME,
                    required = false
            ) String refreshToken,
            Authentication authentication) {
        try {
            Member member = memberRepository.findByUsernameForUpdate(
                            requireUsername(authentication))
                    .orElseThrow(() ->
                            new IllegalArgumentException(
                                    "사용자를 찾을 수 없습니다."));

            long revoked = refreshTokenService.revokeOtherSessions(
                    member,
                    refreshToken
            );

            if (revoked > 0) {
                notificationService.create(
                        member,
                        "SECURITY",
                        "다른 로그인 세션 "
                                + revoked
                                + "개가 종료되었습니다."
                );
            }

            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "revoked", revoked,
                    "message",
                    revoked > 0
                            ? "다른 로그인 세션을 모두 종료했습니다."
                            : "종료할 다른 로그인 세션이 없습니다."
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(401).body(
                    Map.of("message", e.getMessage()));
        }
    }

    private Member createGoogleMember(
            GoogleIdentityService.GoogleIdentity identity) {
        String username = generateGoogleUsername(identity.subject());
        String randomPassword = UUID.randomUUID() + "-" + UUID.randomUUID();
        String randomPin = UUID.randomUUID().toString();

        Member member = new Member(
                username,
                passwordEncoder.encode(randomPassword),
                identity.name(),
                identity.email(),
                passwordEncoder.encode(randomPin)
        );
        member.setSocialProvider(GOOGLE);
        member.setSocialSubject(identity.subject());
        member.setEmailVerified(true);
        member.setPinConfigured(false);
        member.setPasswordLoginEnabled(false);
        memberRepository.save(member);

        Wallet wallet = new Wallet(member, 10000.0);
        walletRepository.save(wallet);
        ledgerService.record(
                member,
                "INITIAL_BALANCE",
                10000.0,
                wallet.getBalance(),
                "Google 가입 초기 가상자금"
        );
        return member;
    }

    private ResponseEntity<?> completeLogin(Member member) {
        String username = member.getUsername();
        boolean dailyReward = false;
        LocalDate today = LocalDate.now(KST);

        if (member.getLastLoginDate() == null
                || !member.getLastLoginDate().equals(today)) {
            Wallet wallet = walletRepository
                    .findForUpdateByMember(member)
                    .orElse(null);
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

        String token = jwtTokenProvider.createToken(username);
        String refreshToken = refreshTokenService.issue(member);

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.SET_COOKIE,
                        refreshTokenService
                                .buildCookie(refreshToken)
                                .toString()
                )
                .body(Map.of(
                        "token", token,
                        "username", username,
                        "dailyReward", dailyReward,
                        "needsPinSetup", !member.isPinConfigured()
                ));
    }

    private String generateGoogleUsername(String subject) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(subject.getBytes(StandardCharsets.UTF_8));
            String base = "g_"
                    + HexFormat.of().formatHex(digest).substring(0, 14);
            String candidate = base;
            int suffix = 1;
            while (memberRepository.findByUsername(candidate).isPresent()) {
                String tail = "_" + suffix++;
                candidate = base.substring(
                        0,
                        Math.min(base.length(), 20 - tail.length())
                ) + tail;
            }
            return candidate;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Google 계정 아이디를 생성할 수 없습니다.");
        }
    }

    private String requireUsername(
            Authentication authentication) {
        if (authentication == null
                || authentication.getName() == null
                || "anonymousUser".equals(authentication.getName())) {
            throw new IllegalArgumentException(
                    "로그인이 필요합니다.");
        }
        return authentication.getName();
    }

    private String normalize(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
