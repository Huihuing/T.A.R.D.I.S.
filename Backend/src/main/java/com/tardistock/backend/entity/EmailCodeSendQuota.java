package com.tardistock.backend.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "email_code_send_quota",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_email_code_send_quota_email",
                        columnNames = "email"
                )
        }
)
public class EmailCodeSendQuota {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(nullable = false)
    private int sendCount;

    @Column(nullable = false)
    private LocalDateTime windowStartedAt;

    public EmailCodeSendQuota() {}

    public EmailCodeSendQuota(String email, LocalDateTime windowStartedAt) {
        this.email = email;
        this.windowStartedAt = windowStartedAt;
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public int getSendCount() { return sendCount; }
    public LocalDateTime getWindowStartedAt() { return windowStartedAt; }

    public void setSendCount(int sendCount) { this.sendCount = sendCount; }
    public void setWindowStartedAt(LocalDateTime windowStartedAt) { this.windowStartedAt = windowStartedAt; }
}
