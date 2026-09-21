package com.tardistock.backend.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/account")
public class AccountController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    private Map<String, Object> verifyAndGetAccount(String username, String rawPassword) {
        String sql = "SELECT Account_accountNumber, Account_balance, Account_password FROM Account WHERE Account_owner = ?";
        Map<String, Object> accountInfo = jdbcTemplate.queryForMap(sql, username);

        String encodedPassword = (String) accountInfo.get("Account_password");
        if (!passwordEncoder.matches(rawPassword, encodedPassword)) {
            throw new IllegalArgumentException("계좌 비밀번호가 일치하지 않습니다.");
        }
        return accountInfo;
    }

    @PostMapping("/deposit")
    @Transactional
    public ResponseEntity<?> deposit(@RequestBody Map<String, Object> request, Authentication authentication) {
        try {
            String username = requireUsername(authentication);
            double amount = parsePositiveAmount(request.get("amount"));
            String accountPassword = (String) request.get("accountPassword");

            Map<String, Object> accountInfo = verifyAndGetAccount(username, accountPassword);
            String accountNumber = (String) accountInfo.get("Account_accountNumber");
            double currentBalance = ((Number) accountInfo.get("Account_balance")).doubleValue();
            double afterBalance = currentBalance + amount;

            jdbcTemplate.update("UPDATE Account SET Account_balance = ? WHERE Account_accountNumber = ?", afterBalance, accountNumber);
            jdbcTemplate.update(
                "INSERT INTO Deposit (Deposit_accountNumber, Deposit_amount, Deposit_balanceAfterDeposit, Deposit_depositDateTime) VALUES (?, ?, ?, NOW())",
                accountNumber, amount, afterBalance
            );

            return ResponseEntity.ok(Map.of("status", "SUCCESS", "message", "입금이 완료되었습니다."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("message", "입금 중 오류가 발생했습니다."));
        }
    }

    @PostMapping("/withdrawal")
    @Transactional
    public ResponseEntity<?> withdrawal(@RequestBody Map<String, Object> request, Authentication authentication) {
        try {
            String username = requireUsername(authentication);
            double amount = parsePositiveAmount(request.get("amount"));
            String accountPassword = (String) request.get("accountPassword");

            Map<String, Object> accountInfo = verifyAndGetAccount(username, accountPassword);
            String accountNumber = (String) accountInfo.get("Account_accountNumber");
            double currentBalance = ((Number) accountInfo.get("Account_balance")).doubleValue();

            if (currentBalance < amount) {
                return ResponseEntity.badRequest().body(Map.of("message", "잔고가 부족합니다."));
            }

            double afterBalance = currentBalance - amount;
            jdbcTemplate.update("UPDATE Account SET Account_balance = ? WHERE Account_accountNumber = ?", afterBalance, accountNumber);
            jdbcTemplate.update(
                "INSERT INTO Withdrawal (Withdrawal_accountNumber, Withdrawal_amount, Withdrawal_balanceAfterWithdrawal, Withdrawal_withdrawalDateTime) VALUES (?, ?, ?, NOW())",
                accountNumber, amount, afterBalance
            );

            return ResponseEntity.ok(Map.of("status", "SUCCESS", "message", "출금이 완료되었습니다."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("message", "출금 중 오류가 발생했습니다."));
        }
    }

    @PostMapping("/transfer")
    @Transactional
    public ResponseEntity<?> transfer(@RequestBody Map<String, Object> request, Authentication authentication) {
        try {
            String fromUser = requireUsername(authentication);
            String toUser = (String) request.get("toUser");
            double amount = parsePositiveAmount(request.get("amount"));
            String accountPassword = (String) request.get("accountPassword");

            if (toUser == null || toUser.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("message", "송금 대상을 입력하세요."));
            }
            if (fromUser.equals(toUser)) {
                return ResponseEntity.badRequest().body(Map.of("message", "본인에게 송금할 수 없습니다."));
            }

            Map<String, Object> fromAccountInfo = verifyAndGetAccount(fromUser, accountPassword);
            String fromAccountNumber = (String) fromAccountInfo.get("Account_accountNumber");
            double fromBalance = ((Number) fromAccountInfo.get("Account_balance")).doubleValue();

            if (fromBalance < amount) {
                return ResponseEntity.badRequest().body(Map.of("message", "잔고가 부족합니다."));
            }

            Map<String, Object> toAccountInfo;
            try {
                toAccountInfo = jdbcTemplate.queryForMap(
                    "SELECT Account_accountNumber, Account_balance FROM Account WHERE Account_owner = ?",
                    toUser
                );
            } catch (Exception e) {
                return ResponseEntity.badRequest().body(Map.of("message", "송금받을 유저가 존재하지 않습니다."));
            }

            String toAccountNumber = (String) toAccountInfo.get("Account_accountNumber");
            double toBalance = ((Number) toAccountInfo.get("Account_balance")).doubleValue();
            double afterFromBalance = fromBalance - amount;
            double afterToBalance = toBalance + amount;

            jdbcTemplate.update("UPDATE Account SET Account_balance = ? WHERE Account_accountNumber = ?", afterFromBalance, fromAccountNumber);
            jdbcTemplate.update("UPDATE Account SET Account_balance = ? WHERE Account_accountNumber = ?", afterToBalance, toAccountNumber);
            jdbcTemplate.update(
                "INSERT INTO Transfer (Transfer_fromAccountNumber, Transfer_toAccountNumber, Transfer_amount, Transfer_balanceAfterTransfer, Transfer_transactionDate) VALUES (?, ?, ?, ?, NOW())",
                fromAccountNumber, toAccountNumber, amount, afterFromBalance
            );

            messagingTemplate.convertAndSend(
                "/topic/alerts/" + toUser,
                (Object) Map.of("type", "TRANSFER", "message", fromUser + "님으로부터 $" + String.format("%.2f", amount) + " 송금이 도착했습니다!")
            );

            return ResponseEntity.ok(Map.of("status", "SUCCESS", "message", toUser + "님에게 송금이 완료되었습니다."));
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
