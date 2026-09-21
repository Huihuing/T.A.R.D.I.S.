package com.tardistock.backend.controller;

import com.tardistock.backend.service.PasswordResetService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth/password")
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    public PasswordResetController(
            PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/send")
    public ResponseEntity<?> sendResetCode(
            @RequestBody Map<String, String> request) {
        try {
            passwordResetService.sendCode(request.get("email"));
            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "message",
                    "해당 이메일로 재설정 가능한 계정이 있다면 인증번호를 전송했습니다."
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(503)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/reset")
    public ResponseEntity<?> resetPassword(
            @RequestBody Map<String, String> request) {
        try {
            passwordResetService.resetPassword(
                    request.get("email"),
                    request.get("code"),
                    request.get("newPassword")
            );
            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "message",
                    "비밀번호를 변경했습니다. 새 비밀번호로 로그인해주세요."
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(410)
                    .body(Map.of("message", e.getMessage()));
        }
    }
}
