package com.tardistock.backend.controller;

import com.tardistock.backend.entity.Comment;
import com.tardistock.backend.entity.CommunityReport;
import com.tardistock.backend.entity.Post;
import com.tardistock.backend.repository.*;
import com.tardistock.backend.service.AdminAccessService;
import jakarta.transaction.Transactional;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
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
    private final CommunityReportRepository communityReportRepository;

    public AdminController(
            AdminAccessService adminAccessService,
            MemberRepository memberRepository,
            PostRepository postRepository,
            CommentRepository commentRepository,
            TradeHistoryRepository tradeHistoryRepository,
            LedgerEntryRepository ledgerEntryRepository,
            NotificationRepository notificationRepository,
            LimitOrderRepository limitOrderRepository,
            PriceAlertRepository priceAlertRepository,
            CommunityReportRepository communityReportRepository) {
        this.adminAccessService = adminAccessService;
        this.memberRepository = memberRepository;
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.tradeHistoryRepository = tradeHistoryRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.notificationRepository = notificationRepository;
        this.limitOrderRepository = limitOrderRepository;
        this.priceAlertRepository = priceAlertRepository;
        this.communityReportRepository = communityReportRepository;
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
        stats.put("openReports", communityReportRepository.countByStatus("OPEN"));

        return ResponseEntity.ok(stats);
    }

    @GetMapping("/reports")
    public ResponseEntity<?> reports(
            @RequestParam(defaultValue = "OPEN") String status,
            Authentication authentication) {
        if (!adminAccessService.isAdmin(authentication)) {
            return ResponseEntity.status(403)
                    .body(Map.of("message", "관리자 권한이 없습니다."));
        }

        String normalized = status == null ? "OPEN" : status.trim().toUpperCase();
        List<CommunityReport> reports = "ALL".equals(normalized)
                ? communityReportRepository.findAllByOrderByCreatedAtDesc()
                : communityReportRepository.findByStatusOrderByCreatedAtDesc(normalized);

        return ResponseEntity.ok(reports.stream().map(report -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", report.getId());
            item.put("targetType", report.getTargetType());
            item.put("targetId", report.getTargetId());
            item.put("reason", report.getReason());
            item.put("detail", report.getDetail());
            item.put("reporterUsername", report.getReporterUsername());
            item.put("reporterIp", report.getReporterIp());
            item.put("targetAuthor", report.getTargetAuthor());
            item.put("targetPreview", report.getTargetPreview());
            item.put("status", report.getStatus());
            item.put("createdAt", report.getCreatedAt());
            item.put("resolvedAt", report.getResolvedAt());
            item.put("resolvedBy", report.getResolvedBy());
            return item;
        }).toList());
    }

    @PatchMapping("/reports/{id}")
    @Transactional
    public ResponseEntity<?> resolveReport(
            @PathVariable Long id,
            @RequestBody Map<String, String> request,
            Authentication authentication) {
        if (!adminAccessService.isAdmin(authentication)) {
            return ResponseEntity.status(403)
                    .body(Map.of("message", "관리자 권한이 없습니다."));
        }

        CommunityReport report = communityReportRepository.findById(id)
                .orElse(null);
        if (report == null) {
            return ResponseEntity.status(404)
                    .body(Map.of("message", "신고 내역이 없습니다."));
        }

        if (!"OPEN".equals(report.getStatus())) {
            return ResponseEntity.status(409).body(
                    Map.of(
                            "message", "이미 처리된 신고입니다.",
                            "status", report.getStatus()
                    ));
        }

        String action = request.get("action");
        action = action == null ? "" : action.trim().toUpperCase();

        if ("DISMISS".equals(action)) {
            report.resolve("DISMISSED", authentication.getName());
            communityReportRepository.save(report);
            return ResponseEntity.ok(Map.of("status", "DISMISSED"));
        }

        if (!"DELETE_CONTENT".equals(action)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "처리 방식이 올바르지 않습니다."));
        }

        if ("POST".equals(report.getTargetType())) {
            Post post = postRepository.findById(report.getTargetId()).orElse(null);
            if (post != null) {
                List<Comment> comments =
                        commentRepository.findByPostOrderByCreatedAtAsc(post);
                for (Comment comment : comments) {
                    markTargetReports(
                            "COMMENT",
                            comment.getId(),
                            "ACTIONED",
                            authentication.getName()
                    );
                }
                commentRepository.deleteAll(comments);
                postRepository.delete(post);
            }
        } else if ("COMMENT".equals(report.getTargetType())) {
            commentRepository.findById(report.getTargetId())
                    .ifPresent(commentRepository::delete);
        }

        markTargetReports(
                report.getTargetType(),
                report.getTargetId(),
                "ACTIONED",
                authentication.getName()
        );

        return ResponseEntity.ok(Map.of("status", "ACTIONED"));
    }

    private void markTargetReports(
            String targetType,
            Long targetId,
            String status,
            String resolvedBy) {
        List<CommunityReport> related =
                communityReportRepository.findByTargetTypeAndTargetIdAndStatus(
                        targetType,
                        targetId,
                        "OPEN"
                );
        for (CommunityReport item : related) {
            item.resolve(status, resolvedBy);
        }
        if (!related.isEmpty()) {
            communityReportRepository.saveAll(related);
        }
    }
}
