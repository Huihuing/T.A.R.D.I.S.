package com.tardistock.backend.controller;

import com.tardistock.backend.dto.AuthRequest;
import com.tardistock.backend.entity.Member;
import com.tardistock.backend.repository.MemberRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "http://localhost:5173") // 프론트엔드(React) 접근 허용
public class AuthController {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthController(MemberRepository memberRepository, PasswordEncoder passwordEncoder) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // 🚀 1. 회원가입 API
    @PostMapping("/signup")
    public Map<String, String> signup(@RequestBody AuthRequest request) {
        Map<String, String> response = new HashMap<>();

        // 1-1. 이미 존재하는 아이디인지 검사
        if (memberRepository.findByUsername(request.getUsername()).isPresent()) {
            response.put("status", "FAIL");
            response.put("message", "이미 존재하는 아이디입니다.");
            return response;
        }

        // 1-2. 비밀번호를 암호화하여 DB에 저장
        String encodedPassword = passwordEncoder.encode(request.getPassword());
        Member newMember = new Member(request.getUsername(), encodedPassword, request.getNickname());
        memberRepository.save(newMember);

        response.put("status", "SUCCESS");
        response.put("message", "회원가입이 완료되었습니다!");
        return response;
    }

    // 🔑 2. 로그인 API
    @PostMapping("/login")
    public Map<String, String> login(@RequestBody AuthRequest request) {
        Map<String, String> response = new HashMap<>();

        // 2-1. DB에서 아이디 검색
        Optional<Member> memberOpt = memberRepository.findByUsername(request.getUsername());

        // 2-2. 유저가 존재하고, 비밀번호가 일치하는지 확인 (암호화된 비밀번호 비교)
        if (memberOpt.isPresent() && passwordEncoder.matches(request.getPassword(), memberOpt.get().getPassword())) {
            response.put("status", "SUCCESS");
            response.put("message", "로그인 성공!");
            // TODO: 다음 단계에서 여기에 'JWT 토큰(통행증)'을 발급해줄 예정입니다!
        } else {
            response.put("status", "FAIL");
            response.put("message", "아이디 또는 비밀번호가 잘못되었습니다.");
        }

        return response;
    }
}