package com.tardistock.backend.controller;

import com.tardistock.backend.service.PriceAlertService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/price-alerts")
public class PriceAlertController {

    private final PriceAlertService priceAlertService;

    public PriceAlertController(PriceAlertService priceAlertService) {
        this.priceAlertService = priceAlertService;
    }

    @GetMapping
    public ResponseEntity<?> list(Authentication authentication) {
        try {
            return ResponseEntity.ok(
                    priceAlertService.list(requireUsername(authentication))
            );
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping
    public ResponseEntity<?> create(
            @RequestBody Map<String, Object> request,
            Authentication authentication) {
        try {
            String symbol = request.get("symbol") == null
                    ? null
                    : request.get("symbol").toString();
            String direction = request.get("direction") == null
                    ? null
                    : request.get("direction").toString();
            double targetPrice = parsePrice(request.get("targetPrice"));

            return ResponseEntity.ok(
                    priceAlertService.create(
                            requireUsername(authentication),
                            symbol,
                            direction,
                            targetPrice
                    )
            );
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(
            @PathVariable Long id,
            Authentication authentication) {
        try {
            priceAlertService.delete(
                    requireUsername(authentication),
                    id
            );
            return ResponseEntity.ok(Map.of("status", "SUCCESS"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", e.getMessage()));
        }
    }

    private String requireUsername(Authentication authentication) {
        if (authentication == null
                || authentication.getName() == null
                || "anonymousUser".equals(authentication.getName())) {
            throw new IllegalArgumentException("로그인이 필요합니다.");
        }
        return authentication.getName();
    }

    private double parsePrice(Object value) {
        if (value == null) {
            throw new IllegalArgumentException(
                    "목표 가격을 입력해주세요."
            );
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "목표 가격이 올바르지 않습니다."
            );
        }
    }
}
