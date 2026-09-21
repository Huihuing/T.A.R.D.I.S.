package com.tardistock.backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.ZoneId;

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

    @Column(nullable = true, length = 20)
    private String guestNickname;

    @Column(nullable = true, length = 100)
    private String guestPasswordHash;

    @Column(nullable = false)
    private String content;

    private LocalDateTime createdAt;

    public Comment() {}

    public Comment(Post post, Member member, String content) {
        this.post = post;
        this.member = member;
        this.content = content;
        this.createdAt = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
    }

    // Legacy guest rows created before guest credentials were introduced.
    public Comment(Post post, String guestIp, String content) {
        this(post, guestIp, null, null, content);
    }

    public Comment(
            Post post,
            String guestIp,
            String guestNickname,
            String guestPasswordHash,
            String content) {
        this.post = post;
        this.guestIp = guestIp;
        this.guestNickname = guestNickname;
        this.guestPasswordHash = guestPasswordHash;
        this.content = content;
        this.createdAt = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
    }

    public Long getId() { return id; }
    public Post getPost() { return post; }
    public Member getMember() { return member; }
    public String getGuestIp() { return guestIp; }
    public String getGuestNickname() { return guestNickname; }
    public String getGuestPasswordHash() { return guestPasswordHash; }
    public String getContent() { return content; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setContent(String content) { this.content = content; }
}
