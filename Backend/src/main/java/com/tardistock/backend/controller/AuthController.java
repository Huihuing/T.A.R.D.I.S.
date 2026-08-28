package com.tardistock.backend.controller;

import com.tardistock.backend.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    // 📝 1. 회원가입 (Register)
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> request) {
        try {
            String username = request.get("username");
            String password = request.get("password");

            // 아이디 중복 검사
            Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM Member WHERE Member_id = ?", Integer.class, username);
            if (count != null && count > 0) {
                return ResponseEntity.badRequest().body(Map.of("message", "이미 존재하는 아이디입니다."));
            }

            // 비밀번호 암호화 후 DB 저장 (나머지 ERD 컬럼은 더미값이나 기본값 처리)
            String encodedPassword = passwordEncoder.encode(password);
            String insertMemberSql = "INSERT INTO Member (Member_id, Member_password, Member_name, Member_email, Member_createAt) VALUES (?, ?, ?, ?, NOW())";
            jdbcTemplate.update(insertMemberSql, username, encodedPassword, "User_" + username, username + "@test.com");

            // ERD에 맞춰 계좌(Account) 자동 생성
            String accountNum = UUID.randomUUID().toString().substring(0, 10); // 임시 계좌번호
            String insertAccountSql = "INSERT INTO Account (Account_owner, Account_accountNumber, Account_password, Account_balance, Account_create_at) VALUES (?, ?, ?, 10000, NOW())";
            jdbcTemplate.update(insertAccountSql, username, accountNum, "0000"); // 기본 지원금 $10000

            return ResponseEntity.ok(Map.of("status", "SUCCESS", "message", "회원가입 완료! 1만 달러가 지급되었습니다."));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("message", "회원가입 에러"));
        }
    }

    // 🔑 2. 로그인 (Login)
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> request) {
        try {
            String username = request.get("username");
            String password = request.get("password");

            // DB에서 암호화된 비밀번호 가져오기
            String dbPassword;
            try {
                dbPassword = jdbcTemplate.queryForObject("SELECT Member_password FROM Member WHERE Member_id = ?", String.class, username);
            } catch (EmptyResultDataAccessException e) {
                return ResponseEntity.status(401).body(Map.of("message", "아이디가 존재하지 않습니다."));
            }

            // 비밀번호 일치 여부 확인
            if (!passwordEncoder.matches(password, dbPassword)) {
                return ResponseEntity.status(401).body(Map.of("message", "비밀번호가 틀렸습니다."));
            }

            // 🌟 성공 시 JWT 토큰 발급
            String token = jwtTokenProvider.createToken(username);

            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "token", token,
                    "username", username,
                    "message", "로그인 성공!"
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("message", "로그인 에러"));
        }
    }
}