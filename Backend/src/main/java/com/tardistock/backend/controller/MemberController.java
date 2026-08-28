package com.tardistock.backend.controller;

import com.tardistock.backend.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Random;
import java.util.UUID;

@RestController
@RequestMapping("/api/member")
public class MemberController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @PostMapping("/signup/email/verify")
    public ResponseEntity<?> sendVerifyEmail(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        if (email == null || email.isEmpty()) return ResponseEntity.badRequest().body(Map.of("message", "이메일을 입력하세요."));

        String verifyCode = String.format("%06d", new Random().nextInt(999999));
        String sql = "INSERT INTO Email_verify (Member_email, e_verify_number, e_verify_isVerify, e_verify_time, e_verify_createAt) VALUES (?, ?, false, DATE_ADD(NOW(), INTERVAL 5 MINUTE), NOW())";
        jdbcTemplate.update(sql, email, verifyCode);
        
        System.out.println("[" + email + "] 인증 코드: " + verifyCode);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "message", "인증 코드가 발송되었습니다. (콘솔 확인)"));
    }

    @PostMapping("/signup/email/verify/check")
    public ResponseEntity<?> checkVerifyEmail(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String code = request.get("code");
        try {
            String sql = "SELECT e_verify_id FROM Email_verify WHERE Member_email = ? AND e_verify_number = ? AND e_verify_time >= NOW() AND e_verify_isVerify = false ORDER BY e_verify_createAt DESC LIMIT 1";
            Long verifyId = jdbcTemplate.queryForObject(sql, Long.class, email, code);
            jdbcTemplate.update("UPDATE Email_verify SET e_verify_isVerify = true WHERE e_verify_id = ?", verifyId);
            return ResponseEntity.ok(Map.of("status", "SUCCESS", "message", "이메일 인증이 완료되었습니다."));
        } catch (EmptyResultDataAccessException e) {
            return ResponseEntity.badRequest().body(Map.of("message", "인증 코드가 틀렸거나 만료되었습니다."));
        }
    }

    @PostMapping("/signup")
    @Transactional
    public ResponseEntity<?> signup(@RequestBody Map<String, String> request) {
        try {
            String username = request.get("username");
            String password = request.get("password");
            String name = request.get("name");
            String email = request.get("email");
            String accountPassword = request.get("accountPassword"); 

            // 💡 [개발 편의] 이메일 인증 확인 로직 일시 정지 (무조건 통과)
            /*
            String verifyCheckSql = "SELECT COUNT(*) FROM Email_verify WHERE Member_email = ? AND e_verify_isVerify = true";
            Integer isVerified = jdbcTemplate.queryForObject(verifyCheckSql, Integer.class, email);
            if (isVerified == null || isVerified == 0) return ResponseEntity.badRequest().body(Map.of("message", "이메일 인증을 완료해주세요."));
            */

            Integer existCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM Member WHERE Member_id = ?", Integer.class, username);
            if (existCount != null && existCount > 0) return ResponseEntity.badRequest().body(Map.of("message", "이미 존재하는 아이디입니다."));

            String accountNumber = UUID.randomUUID().toString().replaceAll("-", "").substring(0, 10);
            String encodedPassword = passwordEncoder.encode(password);
            
            String insertMemberSql = "INSERT INTO Member (Member_id, Member_accountNumber, Member_password, Member_name, Member_email, Member_createAt) VALUES (?, ?, ?, ?, ?, NOW())";
            jdbcTemplate.update(insertMemberSql, username, accountNumber, encodedPassword, name, email);

            String encodedAccountPw = passwordEncoder.encode(accountPassword);
            String insertAccountSql = "INSERT INTO Account (Account_owner, Account_accountNumber, Account_password, Account_balance, Account_create_at) VALUES (?, ?, ?, 10000, NOW())";
            jdbcTemplate.update(insertAccountSql, username, accountNumber, encodedAccountPw);

            // jdbcTemplate.update("DELETE FROM Email_verify WHERE Member_email = ?", email);
            
            return ResponseEntity.ok(Map.of("status", "SUCCESS", "message", "회원가입 완료! 계좌가 발급되었습니다."));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("message", "회원가입 중 오류가 발생했습니다."));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> request) {
        try {
            String username = request.get("username");
            String password = request.get("password");

            String dbPassword;
            try {
                dbPassword = jdbcTemplate.queryForObject("SELECT Member_password FROM Member WHERE Member_id = ?", String.class, username);
            } catch (EmptyResultDataAccessException e) {
                return ResponseEntity.status(401).body(Map.of("message", "아이디/비밀번호가 틀렸습니다."));
            }

            if (!passwordEncoder.matches(password, dbPassword)) {
                return ResponseEntity.status(401).body(Map.of("message", "아이디/비밀번호가 틀렸습니다."));
            }

            String token = jwtTokenProvider.createToken(username);
            return ResponseEntity.ok(Map.of("status", "SUCCESS", "token", token, "username", username, "message", "로그인 성공!"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("message", "로그인 중 에러가 발생했습니다."));
        }
    }
}