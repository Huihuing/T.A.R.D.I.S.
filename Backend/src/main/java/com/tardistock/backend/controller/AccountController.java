package com.tardistock.backend.controller;

import com.tardistock.backend.entity.Member;
import com.tardistock.backend.entity.Wallet;
import com.tardistock.backend.repository.MemberRepository;
import com.tardistock.backend.repository.WalletRepository;
import com.tardistock.backend.service.GoogleIdentityService;
import com.tardistock.backend.service.LedgerService;
import com.tardistock.backend.service.NotificationService;
import com.tardistock.backend.service.RefreshTokenService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/account")
public class AccountController {

    private final WalletRepository walletRepository;
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final NotificationService notificationService;
    private final LedgerService ledgerService;
    private final RefreshTokenService refreshTokenService;
    private final GoogleIdentityService googleIdentityService;

    public AccountController(
            WalletRepository walletRepository,
            MemberRepository memberRepository,
            PasswordEncoder passwordEncoder,
            NotificationService notificationService,
            LedgerService ledgerService,
            RefreshTokenService refreshTokenService,
            GoogleIdentityService googleIdentityService) {
        this.walletRepository = walletRepository;
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.notificationService = notificationService;
        this.ledgerService = ledgerService;
        this.refreshTokenService = refreshTokenService;
        this.googleIdentityService = googleIdentityService;
    }

    @GetMapping("/ledger")
    public ResponseEntity<?> ledger(Authentication authentication) {
        try {
            return ResponseEntity.ok(
                    ledgerService.recent(
                            requireUsername(authentication)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/pin/setup")
    @Transactional
    public ResponseEntity<?> setupPin(
            @RequestBody Map<String, String> request,
            Authentication authentication) {
        try {
            String username = requireUsername(authentication);
            String pin = request.get("pin") == null
                    ? ""
                    : request.get("pin").trim();

            if (!pin.matches("\\d{4}")) {
                return ResponseEntity.badRequest().body(Map.of(
                        "message",
                        "계좌 PIN은 숫자 4자리로 입력해주세요."
                ));
            }

            Member member = memberRepository
                    .findByUsernameForUpdate(username)
                    .orElseThrow(() ->
                            new IllegalArgumentException(
                                    "사용자를 찾을 수 없습니다."));

            if (member.isPinConfigured()) {
                return ResponseEntity.status(409).body(Map.of(
                        "message",
                        "이미 계좌 PIN이 설정되어 있습니다."
                ));
            }

            member.setPin(passwordEncoder.encode(pin));
            member.setPinConfigured(true);
            memberRepository.save(member);

            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "message", "계좌 PIN 설정이 완료되었습니다."
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/settings")
    public ResponseEntity<?> settings(Authentication authentication) {
        try {
            Member member = memberRepository.findByUsername(
                            requireUsername(authentication))
                    .orElseThrow(() ->
                            new IllegalArgumentException(
                                    "사용자를 찾을 수 없습니다."));

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("username", member.getUsername());
            result.put("name", member.getName());
            result.put("email", member.getEmail());
            result.put("emailVerified", member.isEmailVerified());
            result.put("pinConfigured", member.isPinConfigured());
            result.put(
                    "passwordLoginEnabled",
                    member.isPasswordLoginEnabled()
            );
            result.put(
                    "googleConnected",
                    "GOOGLE".equals(member.getSocialProvider())
            );
            result.put(
                    "socialProvider",
                    member.getSocialProvider() == null
                            ? ""
                            : member.getSocialProvider()
            );
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/password/change")
    @Transactional
    public ResponseEntity<?> changePassword(
            @RequestBody Map<String, String> request,
            Authentication authentication) {
        try {
            Member member = memberRepository.findByUsernameForUpdate(
                            requireUsername(authentication))
                    .orElseThrow(() ->
                            new IllegalArgumentException(
                                    "사용자를 찾을 수 없습니다."));

            if (!member.isPasswordLoginEnabled()) {
                return ResponseEntity.status(409).body(Map.of(
                        "message",
                        "Google 전용 계정은 일반 비밀번호를 변경할 수 없습니다."
                ));
            }

            String currentPassword = request.get("currentPassword");
            String newPassword = request.get("newPassword");

            if (currentPassword == null || currentPassword.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "message", "현재 비밀번호를 입력해주세요."
                ));
            }
            if (newPassword == null
                    || newPassword.length() < 8
                    || newPassword.length() > 64) {
                return ResponseEntity.badRequest().body(Map.of(
                        "message", "새 비밀번호는 8~64자로 입력해주세요."
                ));
            }
            if (!passwordEncoder.matches(
                    currentPassword,
                    member.getPassword())) {
                return ResponseEntity.status(401).body(Map.of(
                        "message", "현재 비밀번호가 일치하지 않습니다."
                ));
            }
            if (passwordEncoder.matches(
                    newPassword,
                    member.getPassword())) {
                return ResponseEntity.badRequest().body(Map.of(
                        "message", "현재 비밀번호와 다른 비밀번호를 사용해주세요."
                ));
            }

            member.setPassword(passwordEncoder.encode(newPassword));
            memberRepository.save(member);
            refreshTokenService.revokeAll(member);

            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "requiresReLogin", true,
                    "message",
                    "비밀번호가 변경되었습니다. 다시 로그인해주세요."
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/pin/change")
    @Transactional
    public ResponseEntity<?> changePin(
            @RequestBody Map<String, String> request,
            Authentication authentication) {
        try {
            Member member = memberRepository.findByUsernameForUpdate(
                            requireUsername(authentication))
                    .orElseThrow(() ->
                            new IllegalArgumentException(
                                    "사용자를 찾을 수 없습니다."));

            String currentPin = request.get("currentPin");
            String newPin = request.get("newPin");

            if (newPin == null || !newPin.matches("\\d{4}")) {
                return ResponseEntity.badRequest().body(Map.of(
                        "message", "새 PIN은 숫자 4자리로 입력해주세요."
                ));
            }

            if (member.isPinConfigured()) {
                if (currentPin == null
                        || !currentPin.matches("\\d{4}")
                        || !passwordEncoder.matches(
                                currentPin,
                                member.getPin())) {
                    return ResponseEntity.status(401).body(Map.of(
                            "message", "현재 PIN이 일치하지 않습니다."
                    ));
                }
                if (passwordEncoder.matches(newPin, member.getPin())) {
                    return ResponseEntity.badRequest().body(Map.of(
                            "message", "현재 PIN과 다른 PIN을 사용해주세요."
                    ));
                }
            }

            member.setPin(passwordEncoder.encode(newPin));
            member.setPinConfigured(true);
            memberRepository.save(member);

            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "message", "송금 PIN이 변경되었습니다."
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/google/link")
    @Transactional
    public ResponseEntity<?> linkGoogle(
            @RequestBody Map<String, String> request,
            Authentication authentication) {
        try {
            Member member = memberRepository.findByUsernameForUpdate(
                            requireUsername(authentication))
                    .orElseThrow(() ->
                            new IllegalArgumentException(
                                    "사용자를 찾을 수 없습니다."));

            GoogleIdentityService.GoogleIdentity identity =
                    googleIdentityService.verify(
                            request.get("credential"));

            if (!member.getEmail().equalsIgnoreCase(identity.email())) {
                return ResponseEntity.status(409).body(Map.of(
                        "message",
                        "현재 계정과 같은 이메일의 Google 계정만 연결할 수 있습니다."
                ));
            }

            String provider = member.getSocialProvider();
            if (provider != null
                    && !provider.isBlank()
                    && !"GOOGLE".equals(provider)) {
                return ResponseEntity.status(409).body(Map.of(
                        "message",
                        "이미 다른 SNS 계정과 연결되어 있습니다."
                ));
            }

            Member linked = memberRepository
                    .findBySocialProviderAndSocialSubject(
                            "GOOGLE",
                            identity.subject()
                    )
                    .orElse(null);
            if (linked != null
                    && !linked.getId().equals(member.getId())) {
                return ResponseEntity.status(409).body(Map.of(
                        "message",
                        "이 Google 계정은 이미 다른 계정에 연결되어 있습니다."
                ));
            }

            member.setSocialProvider("GOOGLE");
            member.setSocialSubject(identity.subject());
            member.setEmailVerified(true);
            memberRepository.save(member);

            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "googleConnected", true,
                    "message", "Google 계정 연결이 완료되었습니다."
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(401).body(
                    Map.of("message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(503).body(
                    Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/google/link")
    @Transactional
    public ResponseEntity<?> unlinkGoogle(
            Authentication authentication) {
        try {
            Member member = memberRepository.findByUsernameForUpdate(
                            requireUsername(authentication))
                    .orElseThrow(() ->
                            new IllegalArgumentException(
                                    "사용자를 찾을 수 없습니다."));

            if (!"GOOGLE".equals(member.getSocialProvider())) {
                return ResponseEntity.status(409).body(Map.of(
                        "message", "연결된 Google 계정이 없습니다."
                ));
            }
            if (!member.isPasswordLoginEnabled()) {
                return ResponseEntity.status(409).body(Map.of(
                        "message",
                        "Google 전용 계정은 연결을 해제할 수 없습니다. 계정 잠김을 방지하기 위한 제한입니다."
                ));
            }

            member.setSocialProvider(null);
            member.setSocialSubject(null);
            memberRepository.save(member);

            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "googleConnected", false,
                    "message", "Google 계정 연결을 해제했습니다."
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/deposit")
    public ResponseEntity<?> deposit() {
        return ResponseEntity.status(410).body(Map.of(
                "message",
                "공개 버전에서는 임의 입금 기능을 사용하지 않습니다. 출석 및 활동 보상으로 가상 자산을 획득해주세요."
        ));
    }

    @PostMapping("/withdrawal")
    public ResponseEntity<?> withdrawal() {
        return ResponseEntity.status(410).body(Map.of(
                "message",
                "공개 버전에서는 별도 출금 기능을 사용하지 않습니다."
        ));
    }

    @PostMapping("/transfer")
    @Transactional
    public ResponseEntity<?> transfer(
            @RequestBody Map<String, Object> request,
            Authentication authentication) {
        try {
            String fromUsername = requireUsername(authentication);
            String toUsername = request.get("toUser") == null
                    ? null
                    : request.get("toUser").toString().trim();
            String accountPassword =
                    request.get("accountPassword") == null
                            ? null
                            : request.get("accountPassword").toString();
            double amount =
                    parsePositiveAmount(request.get("amount"));

            if (toUsername == null || toUsername.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "message", "송금 대상을 입력하세요."));
            }
            if (fromUsername.equals(toUsername)) {
                return ResponseEntity.badRequest().body(Map.of(
                        "message", "본인에게 송금할 수 없습니다."));
            }

            List<Wallet> lockedWallets =
                    walletRepository.findAllForUpdateByUsernames(
                            List.of(fromUsername, toUsername));

            Map<String, Wallet> byUsername =
                    lockedWallets.stream().collect(
                            Collectors.toMap(
                                    wallet ->
                                            wallet.getMember()
                                                    .getUsername(),
                                    Function.identity()
                            ));

            Wallet fromWallet = byUsername.get(fromUsername);
            Wallet toWallet = byUsername.get(toUsername);

            if (fromWallet == null) {
                throw new IllegalArgumentException(
                        "보내는 사용자의 지갑을 찾을 수 없습니다.");
            }
            if (toWallet == null) {
                throw new IllegalArgumentException(
                        "송금받을 유저가 존재하지 않거나 지갑이 없습니다.");
            }
            if (!fromWallet.getMember().isPinConfigured()) {
                return ResponseEntity.status(409).body(Map.of(
                        "message",
                        "먼저 계좌 PIN을 설정해주세요."
                ));
            }
            if (accountPassword == null
                    || !passwordEncoder.matches(
                            accountPassword,
                            fromWallet.getMember().getPin())) {
                return ResponseEntity.badRequest().body(Map.of(
                        "message", "계좌 비밀번호가 일치하지 않습니다."));
            }
            if (fromWallet.getBalance() < amount) {
                return ResponseEntity.badRequest().body(Map.of(
                        "message", "잔고가 부족합니다."));
            }

            fromWallet.setBalance(
                    fromWallet.getBalance() - amount);
            toWallet.setBalance(
                    toWallet.getBalance() + amount);
            walletRepository.save(fromWallet);
            walletRepository.save(toWallet);

            var outgoingEntry = ledgerService.record(
                    fromWallet.getMember(),
                    "TRANSFER_OUT",
                    -amount,
                    fromWallet.getBalance(),
                    toUsername,
                    null,
                    toUsername + "님에게 송금"
            );
            ledgerService.record(
                    toWallet.getMember(),
                    "TRANSFER_IN",
                    amount,
                    toWallet.getBalance(),
                    fromUsername,
                    null,
                    fromUsername + "님에게서 송금 수신"
            );

            notificationService.create(
                    toWallet.getMember(),
                    "TRANSFER",
                    fromUsername + "님으로부터 $"
                            + String.format("%.2f", amount)
                            + " 송금이 도착했습니다!"
            );

            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "message",
                    toUsername + "님에게 송금이 완료되었습니다.",
                    "transactionId", outgoingEntry.getId(),
                    "fromUser", fromUsername,
                    "toUser", toUsername,
                    "amount", amount,
                    "transferredAt", outgoingEntry.getCreatedAt().toString(),
                    "newBalance", fromWallet.getBalance()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(
                    Map.of("message",
                            "송금 중 오류가 발생했습니다."));
        }
    }

    private String requireUsername(
            Authentication authentication) {
        if (authentication == null
                || authentication.getName() == null
                || "anonymousUser".equals(
                        authentication.getName())) {
            throw new IllegalArgumentException(
                    "로그인이 필요합니다.");
        }
        return authentication.getName();
    }

    private double parsePositiveAmount(Object value) {
        if (value == null) {
            throw new IllegalArgumentException(
                    "금액을 올바르게 입력하세요.");
        }
        try {
            double parsed =
                    Double.parseDouble(value.toString());
            if (!Double.isFinite(parsed)
                    || parsed <= 0
                    || parsed > 1_000_000_000.0) {
                throw new IllegalArgumentException(
                        "금액을 올바르게 입력하세요.");
            }
            double rounded =
                    Math.round(parsed * 100.0) / 100.0;
            if (rounded <= 0) {
                throw new IllegalArgumentException(
                        "금액을 올바르게 입력하세요.");
            }
            return rounded;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "금액을 올바르게 입력하세요.");
        }
    }
}
