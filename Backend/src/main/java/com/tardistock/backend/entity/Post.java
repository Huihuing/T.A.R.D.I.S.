package com.tardistock.backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
public class Post {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "member_id", nullable = true)
    private Member member;

    @Column(nullable = true)
    private String guestIp;

    @Column(nullable = true, length = 20)
    private String guestNickname;

    @Column(nullable = true, length = 100)
    private String guestPasswordHash;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    private LocalDateTime createdAt;

    public Post() {}

    public Post(Member member, String title, String content) {
        this.member = member;
        this.title = title;
        this.content = content;
        this.createdAt = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
    }

    // Legacy guest rows created before guest credentials were introduced.
    public Post(String guestIp, String title, String content) {
        this(guestIp, null, null, title, content);
    }

    public Post(
            String guestIp,
            String guestNickname,
            String guestPasswordHash,
            String title,
            String content) {
        this.guestIp = guestIp;
        this.guestNickname = guestNickname;
        this.guestPasswordHash = guestPasswordHash;
        this.title = title;
        this.content = content;
        this.createdAt = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
    }

    public Long getId() { return id; }
    public Member getMember() { return member; }
    public String getGuestIp() { return guestIp; }
    public String getGuestNickname() { return guestNickname; }
    public String getGuestPasswordHash() { return guestPasswordHash; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setTitle(String title) { this.title = title; }
    public void setContent(String content) { this.content = content; }
}
