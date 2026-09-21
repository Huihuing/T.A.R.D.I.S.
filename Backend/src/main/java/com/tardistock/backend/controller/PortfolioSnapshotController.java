package com.tardistock.backend.controller;

import com.tardistock.backend.service.PortfolioSnapshotService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/portfolio-history")
public class PortfolioSnapshotController {

    private final PortfolioSnapshotService snapshotService;

    public PortfolioSnapshotController(
            PortfolioSnapshotService snapshotService) {
        this.snapshotService = snapshotService;
    }

    @GetMapping
    public ResponseEntity<?> history(
            @RequestParam(defaultValue = "1W") String range,
            Authentication authentication) {
        try {
            String username = requireUsername(authentication);
            return ResponseEntity.ok(
                    snapshotService.captureAndList(
                            username,
                            range
                    )
            );
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
}
