package com.tardistock.backend.controller;

import com.tardistock.backend.entity.Member;
import com.tardistock.backend.entity.Wallet;
import com.tardistock.backend.repository.MemberRepository;
import com.tardistock.backend.repository.WalletRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/account")
public class AccountController {

    private final MemberRepository memberRepository;
    private final WalletRepository walletRepository;
    private final PasswordEncoder passwordEncoder;
    private final SimpMessagingTemplate messagingTemplate;

    public AccountController(
            MemberRepository memberRepository,
            WalletRepository walletRepository,
            PasswordEncoder passwordEncoder,
            SimpMessagingTemplate messagingTemplate) {
        this.memberRepository = memberRepository;
        this.walletRepository = walletRepository;
        this.passwordEncoder = passwordEncoder;
        this.messagingTemplate = messagingTemplate;
    }

    @PostMapping("/deposit")
    public ResponseEntity<?> deposit() {
        return ResponseEntity.status(410).body(Map.of(
                "message", "공개 버전에서는 임의 입금 기능을 사용하지 않습니다. 출석 및 활동 보상으로 가상 자산을 획득해주세요."
        ));
    }

    @PostMapping("/withdrawal")
    public ResponseEntity<?> withdrawal() {
        return ResponseEntity.status(410).body(Map.of(
                "message", "공개 버전에서는 별도 출금 기능을 사용하지 않습니다."
        ));
    }

    @PostMapping("/transfer")
    @Transactional
    public ResponseEntity<?> transfer(@RequestBody Map<String, Object> request, Authentication authentication) {
        try {
            String fromUsername = requireUsername(authentication);
            String toUsername = request.get("toUser") == null ? null : request.get("toUser").toString().trim();
            String accountPassword = request.get("accountPassword") == null ? null : request.get("accountPassword").toString();
            double amount = parsePositiveAmount(request.get("amount"));

            if (toUsername == null || toUsername.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("message", "송금 대상을 입력하세요."));
            }
            if (fromUsername.equals(toUsername)) {
                return ResponseEntity.badRequest().body(Map.of("message", "본인에게 송금할 수 없습니다."));
            }

            Member fromMember = memberRepository.findByUsername(fromUsername)
                    .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

            if (accountPassword == null || !passwordEncoder.matches(accountPassword, fromMember.getPin())) {
                return ResponseEntity.badRequest().body(Map.of("message", "계좌 비밀번호가 일치하지 않습니다."));
            }

            Member toMember = memberRepository.findByUsername(toUsername)
                    .orElseThrow(() -> new IllegalArgumentException("송금받을 유저가 존재하지 않습니다."));

            Wallet fromWallet = walletRepository.findByMember(fromMember)
                    .orElseThrow(() -> new IllegalArgumentException("보내는 사용자의 지갑을 찾을 수 없습니다."));
            Wallet toWallet = walletRepository.findByMember(toMember)
                    .orElseThrow(() -> new IllegalArgumentException("받는 사용자의 지갑을 찾을 수 없습니다."));

            if (fromWallet.getBalance() < amount) {
                return ResponseEntity.badRequest().body(Map.of("message", "잔고가 부족합니다."));
            }

            fromWallet.setBalance(fromWallet.getBalance() - amount);
            toWallet.setBalance(toWallet.getBalance() + amount);
            walletRepository.save(fromWallet);
            walletRepository.save(toWallet);

            messagingTemplate.convertAndSend(
                    "/topic/alerts/" + toUsername,
                    (Object) Map.of(
                            "type", "TRANSFER",
                            "message", fromUsername + "님으로부터 $" + String.format("%.2f", amount) + " 송금이 도착했습니다!"
                    )
            );

            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "message", toUsername + "님에게 송금이 완료되었습니다.",
                    "newBalance", fromWallet.getBalance()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("message", "송금 중 오류가 발생했습니다."));
        }
    }

    private String requireUsername(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || "anonymousUser".equals(authentication.getName())) {
            throw new IllegalArgumentException("로그인이 필요합니다.");
        }
        return authentication.getName();
    }

    private double parsePositiveAmount(Object value) {
        if (value == null) {
            throw new IllegalArgumentException("금액을 올바르게 입력하세요.");
        }
        try {
            double amount = Double.parseDouble(value.toString());
            if (!Double.isFinite(amount) || amount <= 0) {
                throw new IllegalArgumentException("금액을 올바르게 입력하세요.");
            }
            return amount;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("금액을 올바르게 입력하세요.");
        }
    }
}
