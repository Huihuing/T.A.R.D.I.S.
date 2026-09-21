package com.tardistock.backend.controller;

import com.tardistock.backend.service.LimitOrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/limit-orders")
public class LimitOrderController {

    private final LimitOrderService limitOrderService;

    public LimitOrderController(LimitOrderService limitOrderService) {
        this.limitOrderService = limitOrderService;
    }

    @GetMapping
    public ResponseEntity<?> list(Authentication authentication) {
        try {
            return ResponseEntity.ok(
                    limitOrderService.list(
                            requireUsername(authentication)
                    )
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
            return ResponseEntity.ok(
                    limitOrderService.create(
                            requireUsername(authentication),
                            stringValue(request.get("side")),
                            stringValue(request.get("symbol")),
                            parseAmount(request.get("amount")),
                            parsePrice(request.get("limitPrice"))
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
    public ResponseEntity<?> cancel(
            @PathVariable Long id,
            Authentication authentication) {
        try {
            limitOrderService.cancel(
                    requireUsername(authentication),
                    id
            );
            return ResponseEntity.ok(Map.of("status", "SUCCESS"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409)
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

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private int parseAmount(Object value) {
        if (value == null) {
            throw new IllegalArgumentException(
                    "주문 수량을 입력해주세요."
            );
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "주문 수량이 올바르지 않습니다."
            );
        }
    }

    private double parsePrice(Object value) {
        if (value == null) {
            throw new IllegalArgumentException(
                    "지정가를 입력해주세요."
            );
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "지정가가 올바르지 않습니다."
            );
        }
    }
}
