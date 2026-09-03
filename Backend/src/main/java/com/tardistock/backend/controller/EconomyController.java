package com.tardistock.backend.controller;

import com.tardistock.backend.service.EconomyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/economy")
@CrossOrigin(origins = "*")
public class EconomyController {

    private final EconomyService economyService;

    public EconomyController(EconomyService economyService) {
        this.economyService = economyService;
    }

    @GetMapping("/status")
    public ResponseEntity<?> getStatus(@RequestParam(required = false, defaultValue = "") String username) {
        if (username.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "로그인이 필요합니다."));
        }
        try {
            return ResponseEntity.ok(economyService.getStatus(username));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/check-in")
    public ResponseEntity<?> checkIn(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        if (username == null || username.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "로그인이 필요합니다."));
        }
        try {
            return ResponseEntity.ok(economyService.checkIn(username));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/bankruptcy-relief")
    public ResponseEntity<?> claimBankruptcyRelief(@RequestBody Map<String, Object> request) {
        String username = (String) request.get("username");
        if (username == null || username.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "로그인이 필요합니다."));
        }
        double amount = 1000.0;
        if (request.containsKey("rewardAmount")) {
            amount = Double.parseDouble(request.get("rewardAmount").toString());
        }
        try {
            return ResponseEntity.ok(economyService.claimBankruptcyRelief(username, amount));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/claim-quest")
    public ResponseEntity<?> claimQuest(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String questType = request.get("questType");
        if (username == null || username.isEmpty() || questType == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "잘못된 요청입니다."));
        }
        try {
            return ResponseEntity.ok(economyService.claimQuest(username, questType));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}
