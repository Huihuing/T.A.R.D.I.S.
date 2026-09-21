package com.tardistock.backend.controller;

import com.tardistock.backend.service.EconomyService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/economy")
public class EconomyController {

    private final EconomyService economyService;

    public EconomyController(EconomyService economyService) {
        this.economyService = economyService;
    }

    @GetMapping("/status")
    public ResponseEntity<?> getStatus(Authentication authentication) {
        try {
            return ResponseEntity.ok(economyService.getStatus(requireUsername(authentication)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/check-in")
    public ResponseEntity<?> checkIn(Authentication authentication) {
        try {
            return ResponseEntity.ok(economyService.checkIn(requireUsername(authentication)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/bankruptcy-relief")
    public ResponseEntity<?> claimBankruptcyRelief(Authentication authentication) {
        try {
            // The server, not the browser, decides the relief amount.
            return ResponseEntity.ok(economyService.claimBankruptcyRelief(requireUsername(authentication), 1000.0));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/claim-quest")
    public ResponseEntity<?> claimQuest(@RequestBody Map<String, String> request, Authentication authentication) {
        String questType = request.get("questType");
        if (questType == null || questType.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "잘못된 요청입니다."));
        }
        try {
            return ResponseEntity.ok(economyService.claimQuest(requireUsername(authentication), questType));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    private String requireUsername(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || "anonymousUser".equals(authentication.getName())) {
            throw new IllegalStateException("로그인이 필요합니다.");
        }
        return authentication.getName();
    }
}
