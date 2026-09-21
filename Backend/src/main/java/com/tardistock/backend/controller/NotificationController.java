package com.tardistock.backend.controller;

import com.tardistock.backend.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(
            NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ResponseEntity<?> recent(Authentication authentication) {
        try {
            return ResponseEntity.ok(
                    notificationService.recent(
                            requireUsername(authentication)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/unread-count")
    public ResponseEntity<?> unreadCount(
            Authentication authentication) {
        try {
            long count = notificationService.unreadCount(
                    requireUsername(authentication));
            return ResponseEntity.ok(Map.of("count", count));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", e.getMessage()));
        }
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<?> markRead(
            @PathVariable Long id,
            Authentication authentication) {
        try {
            notificationService.markRead(
                    requireUsername(authentication), id);
            return ResponseEntity.ok(
                    Map.of("status", "SUCCESS"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/read-all")
    public ResponseEntity<?> markAllRead(
            Authentication authentication) {
        try {
            int count = notificationService.markAllRead(
                    requireUsername(authentication));
            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "updated", count
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", e.getMessage()));
        }
    }

    private String requireUsername(
            Authentication authentication) {
        if (authentication == null
                || authentication.getName() == null
                || "anonymousUser".equals(
                        authentication.getName())) {
            throw new IllegalArgumentException(
                    "로그인이 필요합니다.");
        }
        return authentication.getName();
    }
}
