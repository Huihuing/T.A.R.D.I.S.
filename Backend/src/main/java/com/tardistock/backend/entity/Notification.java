package com.tardistock.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(nullable = false, length = 40)
    private String type;

    @Column(nullable = false, length = 500)
    private String message;

    private LocalDateTime readAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public Notification() {}

    public Notification(
            Member member,
            String type,
            String message,
            LocalDateTime createdAt) {
        this.member = member;
        this.type = type;
        this.message = message;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Member getMember() { return member; }
    public String getType() { return type; }
    public String getMessage() { return message; }
    public LocalDateTime getReadAt() { return readAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setReadAt(LocalDateTime readAt) {
        this.readAt = readAt;
    }

    public boolean isRead() {
        return readAt != null;
    }
}
