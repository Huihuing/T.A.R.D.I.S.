package com.tardistock.backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
@Table(name = "community_report")
public class CommunityReport {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 16)
    private String targetType;

    @Column(nullable = false)
    private Long targetId;

    @Column(nullable = false, length = 32)
    private String reason;

    @Column(nullable = true, length = 500)
    private String detail;

    @Column(nullable = true, length = 100)
    private String reporterUsername;

    @Column(nullable = true, length = 64)
    private String reporterIp;

    @Column(nullable = true, length = 100)
    private String targetAuthor;

    @Column(nullable = false, length = 500)
    private String targetPreview;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = true)
    private LocalDateTime resolvedAt;

    @Column(nullable = true, length = 100)
    private String resolvedBy;

    public CommunityReport() {}

    public CommunityReport(
            String targetType,
            Long targetId,
            String reason,
            String detail,
            String reporterUsername,
            String reporterIp,
            String targetAuthor,
            String targetPreview) {
        this.targetType = targetType;
        this.targetId = targetId;
        this.reason = reason;
        this.detail = detail;
        this.reporterUsername = reporterUsername;
        this.reporterIp = reporterIp;
        this.targetAuthor = targetAuthor;
        this.targetPreview = targetPreview;
        this.status = "OPEN";
        this.createdAt = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
    }

    public void resolve(String status, String resolvedBy) {
        this.status = status;
        this.resolvedBy = resolvedBy;
        this.resolvedAt = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
    }

    public Long getId() { return id; }
    public String getTargetType() { return targetType; }
    public Long getTargetId() { return targetId; }
    public String getReason() { return reason; }
    public String getDetail() { return detail; }
    public String getReporterUsername() { return reporterUsername; }
    public String getReporterIp() { return reporterIp; }
    public String getTargetAuthor() { return targetAuthor; }
    public String getTargetPreview() { return targetPreview; }
    public String getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public String getResolvedBy() { return resolvedBy; }
}
