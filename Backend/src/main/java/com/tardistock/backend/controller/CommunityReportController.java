package com.tardistock.backend.controller;

import com.tardistock.backend.entity.Comment;
import com.tardistock.backend.entity.CommunityReport;
import com.tardistock.backend.entity.Member;
import com.tardistock.backend.entity.Post;
import com.tardistock.backend.repository.CommentRepository;
import com.tardistock.backend.repository.CommunityReportRepository;
import com.tardistock.backend.repository.PostRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

@RestController
@RequestMapping("/api/board/reports")
public class CommunityReportController {

    private static final Set<String> TARGET_TYPES = Set.of("POST", "COMMENT");
    private static final Set<String> REASONS = Set.of(
            "SPAM",
            "ABUSE",
            "HARASSMENT",
            "MISINFORMATION",
            "OTHER"
    );

    private final CommunityReportRepository reportRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;

    public CommunityReportController(
            CommunityReportRepository reportRepository,
            PostRepository postRepository,
            CommentRepository commentRepository) {
        this.reportRepository = reportRepository;
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
    }

    @PostMapping
    public ResponseEntity<?> createReport(
            @RequestBody Map<String, Object> request,
            HttpServletRequest httpRequest,
            Authentication authentication) {

        String targetType = upper(request.get("targetType"));
        Long targetId = parseId(request.get("targetId"));
        String reason = upper(request.get("reason"));
        String detail = normalize(request.get("detail"));

        if (!TARGET_TYPES.contains(targetType) || targetId == null) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", "신고 대상이 올바르지 않습니다."));
        }
        if (!REASONS.contains(reason)) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", "신고 사유가 올바르지 않습니다."));
        }
        if (detail != null && detail.length() > 500) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", "추가 설명은 500자 이하로 입력해주세요."));
        }

        String targetAuthor;
        String targetPreview;

        if ("POST".equals(targetType)) {
            Optional<Post> postOpt = postRepository.findById(targetId);
            if (postOpt.isEmpty()) {
                return ResponseEntity.status(404).body(
                        Map.of("message", "신고할 게시글이 없습니다."));
            }
            Post post = postOpt.get();
            targetAuthor = authorName(
                    post.getMember(),
                    post.getGuestNickname(),
                    post.getGuestIp()
            );
            targetPreview = clip(post.getTitle() + " — " + post.getContent());
        } else {
            Optional<Comment> commentOpt = commentRepository.findById(targetId);
            if (commentOpt.isEmpty()) {
                return ResponseEntity.status(404).body(
                        Map.of("message", "신고할 댓글이 없습니다."));
            }
            Comment comment = commentOpt.get();
            targetAuthor = authorName(
                    comment.getMember(),
                    comment.getGuestNickname(),
                    comment.getGuestIp()
            );
            targetPreview = clip(comment.getContent());
        }

        String reporterUsername = null;
        if (authentication != null
                && authentication.isAuthenticated()
                && authentication.getName() != null
                && !"anonymousUser".equals(authentication.getName())) {
            reporterUsername = authentication.getName();
        }

        if (reporterUsername != null
                && reportRepository
                        .existsByTargetTypeAndTargetIdAndStatusAndReporterUsername(
                                targetType,
                                targetId,
                                "OPEN",
                                reporterUsername
                        )) {
            return ResponseEntity.status(409).body(
                    Map.of("message", "이미 신고한 콘텐츠입니다."));
        }

        CommunityReport report = reportRepository.save(new CommunityReport(
                targetType,
                targetId,
                reason,
                detail,
                reporterUsername,
                maskIp(getClientIp(httpRequest)),
                targetAuthor,
                targetPreview
        ));

        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "reportId", report.getId()
        ));
    }

    private Long parseId(Object value) {
        if (value == null) return null;
        try {
            long id = Long.parseLong(value.toString());
            return id > 0 ? id : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String upper(Object value) {
        String normalized = normalize(value);
        return normalized == null ? null : normalized.toUpperCase();
    }

    private String normalize(Object value) {
        if (value == null) return null;
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private String clip(String value) {
        String normalized = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 500
                ? normalized
                : normalized.substring(0, 497) + "...";
    }

    private String authorName(Member member, String guestNickname, String guestIp) {
        if (member != null) return member.getUsername();
        if (guestNickname != null && !guestNickname.isBlank()) return guestNickname;
        return guestIp == null || guestIp.isBlank() ? "Guest" : "Guest (" + guestIp + ")";
    }

    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String remote = request.getRemoteAddr();
        return remote == null || remote.isBlank() ? "unknown" : remote;
    }

    private String maskIp(String ip) {
        if (ip == null || ip.isBlank() || "unknown".equals(ip)) return "unknown";
        if (ip.contains(".")) {
            String[] parts = ip.split("\\.");
            if (parts.length == 4) {
                return parts[0] + "." + parts[1] + ".*.*";
            }
        }
        if (ip.contains(":")) {
            String[] parts = ip.split(":");
            int take = Math.min(parts.length, 4);
            StringBuilder masked = new StringBuilder();
            for (int i = 0; i < take; i++) {
                if (i > 0) masked.append(':');
                masked.append(parts[i]);
            }
            return masked.append(":*").toString();
        }
        return "masked";
    }
}
