package com.tardistock.backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class Comment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @ManyToOne
    @JoinColumn(name = "member_id", nullable = true)
    private Member member;

    @Column(nullable = true)
    private String guestIp;

    @Column(nullable = false)
    private String content;

    private LocalDateTime createdAt;

    public Comment() {}

    public Comment(Post post, Member member, String content) {
        this.post = post;
        this.member = member;
        this.content = content;
        this.createdAt = LocalDateTime.now();
    }

    public Comment(Post post, String guestIp, String content) {
        this.post = post;
        this.guestIp = guestIp;
        this.content = content;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Post getPost() { return post; }
    public Member getMember() { return member; }
    public String getGuestIp() { return guestIp; }
    public String getContent() { return content; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}