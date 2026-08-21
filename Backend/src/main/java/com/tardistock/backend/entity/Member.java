package com.tardistock.backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false) // 아이디는 중복될 수 없음!
    private String username;

    @Column(nullable = false)
    private String password;

    private String nickname; // 화면에 띄워줄 유저 닉네임
    private LocalDateTime createdAt;

    public Member() {
    }

    public Member(String username, String password, String nickname) {
        this.username = username;
        this.password = password;
        this.nickname = nickname;
        this.createdAt = LocalDateTime.now();
    }

    // 데이터 조회를 위한 Getter
    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getNickname() {
        return nickname;
    }
}