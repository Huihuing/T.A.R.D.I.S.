package com.tardistock.backend.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "email_verification",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_email_verification_email",
                        columnNames = "email"
                )
        }
)
public class EmailVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(nullable = false, length = 100)
    private String codeHash;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    private LocalDateTime verifiedAt;
    private LocalDateTime lastSentAt;

    @Column(nullable = false)
    private int attempts;

    public EmailVerification() {}

    public EmailVerification(String email) {
        this.email = email;
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getCodeHash() { return codeHash; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public LocalDateTime getVerifiedAt() { return verifiedAt; }
    public LocalDateTime getLastSentAt() { return lastSentAt; }
    public int getAttempts() { return attempts; }

    public void setEmail(String email) { this.email = email; }
    public void setCodeHash(String codeHash) { this.codeHash = codeHash; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    public void setVerifiedAt(LocalDateTime verifiedAt) { this.verifiedAt = verifiedAt; }
    public void setLastSentAt(LocalDateTime lastSentAt) { this.lastSentAt = lastSentAt; }
    public void setAttempts(int attempts) { this.attempts = attempts; }
}
