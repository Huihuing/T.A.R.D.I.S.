package com.tardistock.backend.controller;

import com.tardistock.backend.entity.Member;
import com.tardistock.backend.entity.Wallet;
import com.tardistock.backend.repository.MemberRepository;
import com.tardistock.backend.repository.WalletRepository;
import com.tardistock.backend.security.JwtTokenProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final MemberRepository memberRepository;
    private final WalletRepository walletRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthController(MemberRepository memberRepository, 
                          WalletRepository walletRepository, 
                          PasswordEncoder passwordEncoder, 
                          JwtTokenProvider jwtTokenProvider) {
        this.memberRepository = memberRepository;
        this.walletRepository = walletRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String password = request.get("password");
        String name = request.get("name");
        String email = request.get("email");
        String pin = request.get("pin");

        // 유효성 검사
        if (username == null || password == null || name == null || email == null || pin == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "모든 필드를 입력해주세요."));
        }

        if (memberRepository.findByUsername(username).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("message", "이미 존재하는 아이디입니다."));
        }

        // 비밀번호 및 PIN 4자리 암호화
        String encodedPassword = passwordEncoder.encode(password);
        String encodedPin = passwordEncoder.encode(pin);

        // Member 저장
        Member member = new Member(username, encodedPassword, name, email, encodedPin);
        memberRepository.save(member);

        // 신규 회원 초기 자본금 $10,000 지갑 생성
        Wallet wallet = new Wallet(member, 10000.0);
        walletRepository.save(wallet);

        return ResponseEntity.ok(Map.of("status", "SUCCESS", "message", "회원가입이 완료되었습니다."));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String password = request.get("password");

        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "아이디와 비밀번호를 입력해주세요."));
        }

        String normalizedUsername = username.trim();

        return memberRepository.findByUsername(normalizedUsername)
            .map(member -> {
                if (passwordEncoder.matches(password, member.getPassword())) {
                    String token = jwtTokenProvider.createToken(normalizedUsername);
                    boolean dailyReward = false;
                    
                    // 💡 일일 출석 체크 로직
                    java.time.LocalDate today = java.time.LocalDate.now();
                    if (member.getLastLoginDate() == null || !member.getLastLoginDate().equals(today)) {
                        member.setLastLoginDate(today);
                        memberRepository.save(member);
                        
                        // 지갑에 500달러 추가
                        walletRepository.findByMember(member).ifPresent(wallet -> {
                            wallet.setBalance(wallet.getBalance() + 500.0);
                            walletRepository.save(wallet);
                        });
                        dailyReward = true;
                    }
                    
                    return ResponseEntity.ok(Map.of(
                        "token", token, 
                        "username", normalizedUsername,
                        "dailyReward", dailyReward
                    ));
                }
                return ResponseEntity.status(401).body(Map.of("message", "비밀번호가 일치하지 않습니다."));
            })
            .orElseGet(() -> ResponseEntity.status(401).body(Map.of("message", "존재하지 않는 아이디입니다.")));
    }
}