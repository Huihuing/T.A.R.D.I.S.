package com.tardistock.backend.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "password_reset_code",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_password_reset_email",
                        columnNames = "email"
                )
        }
)
public class PasswordResetCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(nullable = false, length = 100)
    private String codeHash;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    private LocalDateTime lastSentAt;

    @Column(nullable = false)
    private int attempts;

    public PasswordResetCode() {}

    public PasswordResetCode(String email) {
        this.email = email;
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getCodeHash() { return codeHash; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public LocalDateTime getLastSentAt() { return lastSentAt; }
    public int getAttempts() { return attempts; }

    public void setEmail(String email) { this.email = email; }
    public void setCodeHash(String codeHash) { this.codeHash = codeHash; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    public void setLastSentAt(LocalDateTime lastSentAt) { this.lastSentAt = lastSentAt; }
    public void setAttempts(int attempts) { this.attempts = attempts; }
}
