package com.tardistock.backend.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/wallet")
public class WalletController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // 💰 1. 입금 (Deposit)
    @PostMapping("/deposit")
    @Transactional
    public ResponseEntity<?> deposit(@RequestBody Map<String, Object> request) {
        try {
            String username = (String) request.get("username");
            double amount = Double.parseDouble(request.get("amount").toString());

            if (amount <= 0) return ResponseEntity.badRequest().body(Map.of("message", "올바른 금액을 입력하세요."));

            // 1. 유저의 계좌 정보 및 잔고 조회 (Member 테이블과 Account 테이블 조인)
            String accountQuery = "SELECT a.Account_accountNumber, a.Account_balance FROM Account a JOIN Member m ON a.Account_owner = m.Member_id WHERE m.Member_id = ?";
            Map<String, Object> accountInfo = jdbcTemplate.queryForMap(accountQuery, username);
            
            String accountNumber = (String) accountInfo.get("Account_accountNumber");
            double currentBalance = ((Number) accountInfo.get("Account_balance")).doubleValue();
            double afterBalance = currentBalance + amount;

            // 2. Account 테이블 잔고 업데이트
            jdbcTemplate.update("UPDATE Account SET Account_balance = ? WHERE Account_accountNumber = ?", afterBalance, accountNumber);

            // 3. Deposit (입금 내역) 테이블에 기록 저장
            String insertDeposit = "INSERT INTO Deposit (Deposit_accountNumber, Deposit_amount, Deposit_balanceAfterDeposit, Deposit_depositDateTime) VALUES (?, ?, ?, NOW())";
            jdbcTemplate.update(insertDeposit, accountNumber, amount, afterBalance);

            return ResponseEntity.ok(Map.of("status", "SUCCESS", "message", "입금이 완료되었습니다."));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("message", "입금 처리 중 에러가 발생했습니다."));
        }
    }

    // 💸 2. 출금 (Withdrawal)
    @PostMapping("/withdraw")
    @Transactional
    public ResponseEntity<?> withdraw(@RequestBody Map<String, Object> request) {
        try {
            String username = (String) request.get("username");
            double amount = Double.parseDouble(request.get("amount").toString());

            if (amount <= 0) return ResponseEntity.badRequest().body(Map.of("message", "올바른 금액을 입력하세요."));

            // 1. 유저 계좌 조회
            String accountQuery = "SELECT a.Account_accountNumber, a.Account_balance FROM Account a JOIN Member m ON a.Account_owner = m.Member_id WHERE m.Member_id = ?";
            Map<String, Object> accountInfo = jdbcTemplate.queryForMap(accountQuery, username);
            
            String accountNumber = (String) accountInfo.get("Account_accountNumber");
            double currentBalance = ((Number) accountInfo.get("Account_balance")).doubleValue();

            if (currentBalance < amount) {
                return ResponseEntity.badRequest().body(Map.of("message", "잔고가 부족합니다."));
            }

            double afterBalance = currentBalance - amount;

            // 2. Account 테이블 잔고 차감
            jdbcTemplate.update("UPDATE Account SET Account_balance = ? WHERE Account_accountNumber = ?", afterBalance, accountNumber);

            // 3. Withdrawal (출금 내역) 테이블에 기록 저장
            String insertWithdrawal = "INSERT INTO Withdrawal (Withdrawal_accountNumber, Withdrawal_amount, Withdrawal_balanceAfterWithdrawal, Withdrawal_withdrawalDateTime) VALUES (?, ?, ?, NOW())";
            jdbcTemplate.update(insertWithdrawal, accountNumber, amount, afterBalance);

            return ResponseEntity.ok(Map.of("status", "SUCCESS", "message", "출금이 완료되었습니다."));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("message", "출금 처리 중 에러가 발생했습니다."));
        }
    }

    // 🤝 3. 송금 (Transfer)
    @PostMapping("/transfer")
    @Transactional
    public ResponseEntity<?> transfer(@RequestBody Map<String, Object> request) {
        try {
            String fromUser = (String) request.get("fromUser");
            String toUser = (String) request.get("toUser");
            double amount = Double.parseDouble(request.get("amount").toString());

            if (amount <= 0) return ResponseEntity.badRequest().body(Map.of("message", "올바른 금액을 입력하세요."));
            if (fromUser.equals(toUser)) return ResponseEntity.badRequest().body(Map.of("message", "자기 자신에게 송금할 수 없습니다."));

            // 1. 송금자 계좌 확인
            String fromQuery = "SELECT a.Account_accountNumber, a.Account_balance FROM Account a JOIN Member m ON a.Account_owner = m.Member_id WHERE m.Member_id = ?";
            Map<String, Object> fromAccountInfo = jdbcTemplate.queryForMap(fromQuery, fromUser);
            String fromAccountNumber = (String) fromAccountInfo.get("Account_accountNumber");
            double fromBalance = ((Number) fromAccountInfo.get("Account_balance")).doubleValue();

            if (fromBalance < amount) return ResponseEntity.badRequest().body(Map.of("message", "잔고가 부족합니다."));

            // 2. 수신자 계좌 확인
            String toQuery = "SELECT a.Account_accountNumber, a.Account_balance FROM Account a JOIN Member m ON a.Account_owner = m.Member_id WHERE m.Member_id = ?";
            Map<String, Object> toAccountInfo;
            try {
                toAccountInfo = jdbcTemplate.queryForMap(toQuery, toUser);
            } catch (Exception e) {
                return ResponseEntity.badRequest().body(Map.of("message", "송금받을 유저가 존재하지 않습니다."));
            }
            
            String toAccountNumber = (String) toAccountInfo.get("Account_accountNumber");
            double toBalance = ((Number) toAccountInfo.get("Account_balance")).doubleValue();

            // 3. 잔고 업데이트 (송금자 차감, 수신자 증가)
            double afterFromBalance = fromBalance - amount;
            double afterToBalance = toBalance + amount;
            
            jdbcTemplate.update("UPDATE Account SET Account_balance = ? WHERE Account_accountNumber = ?", afterFromBalance, fromAccountNumber);
            jdbcTemplate.update("UPDATE Account SET Account_balance = ? WHERE Account_accountNumber = ?", afterToBalance, toAccountNumber);

            // 4. Transfer (이체 내역) 테이블에 기록 저장
            String insertTransfer = "INSERT INTO Transfer (Transfer_fromAccountNumber, Transfer_toAccountNumber, Transfer_amount, Transfer_balanceAfterTransfer, Transfer_transactionDate) VALUES (?, ?, ?, ?, NOW())";
            jdbcTemplate.update(insertTransfer, fromAccountNumber, toAccountNumber, amount, afterFromBalance);

            return ResponseEntity.ok(Map.of("status", "SUCCESS", "message", toUser + "님에게 송금이 완료되었습니다."));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("message", "송금 처리 중 에러가 발생했습니다."));
        }
    }
}