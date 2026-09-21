package com.tardistock.backend.controller;

import com.tardistock.backend.repository.*;
import com.tardistock.backend.service.AdminAccessService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminAccessService adminAccessService;
    private final MemberRepository memberRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final TradeHistoryRepository tradeHistoryRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final NotificationRepository notificationRepository;
    private final LimitOrderRepository limitOrderRepository;
    private final PriceAlertRepository priceAlertRepository;

    public AdminController(
            AdminAccessService adminAccessService,
            MemberRepository memberRepository,
            PostRepository postRepository,
            CommentRepository commentRepository,
            TradeHistoryRepository tradeHistoryRepository,
            LedgerEntryRepository ledgerEntryRepository,
            NotificationRepository notificationRepository,
            LimitOrderRepository limitOrderRepository,
            PriceAlertRepository priceAlertRepository) {
        this.adminAccessService = adminAccessService;
        this.memberRepository = memberRepository;
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.tradeHistoryRepository = tradeHistoryRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.notificationRepository = notificationRepository;
        this.limitOrderRepository = limitOrderRepository;
        this.priceAlertRepository = priceAlertRepository;
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(Authentication authentication) {
        if (!adminAccessService.isAdmin(authentication)) {
            return ResponseEntity.status(403)
                    .body(Map.of("message", "관리자 권한이 없습니다."));
        }

        return ResponseEntity.ok(Map.of(
                "admin", true,
                "username", authentication.getName()
        ));
    }

    @GetMapping("/stats")
    public ResponseEntity<?> stats(Authentication authentication) {
        if (!adminAccessService.isAdmin(authentication)) {
            return ResponseEntity.status(403)
                    .body(Map.of("message", "관리자 권한이 없습니다."));
        }

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("members", memberRepository.count());
        stats.put("posts", postRepository.count());
        stats.put("comments", commentRepository.count());
        stats.put("trades", tradeHistoryRepository.count());
        stats.put("ledgerEntries", ledgerEntryRepository.count());
        stats.put("notifications", notificationRepository.count());
        stats.put("limitOrders", limitOrderRepository.count());
        stats.put("priceAlerts", priceAlertRepository.count());

        return ResponseEntity.ok(stats);
    }
}
