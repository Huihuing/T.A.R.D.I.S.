package com.tardistock.backend.entity;

import jakarta.persistence.*;

@Entity
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String pin; // 암호화된 계좌 4자리 비밀번호

    private java.time.LocalDate lastLoginDate;
    private java.time.LocalDate lastReliefDate;

    public Member() {}

    public Member(String username, String password, String name, String email, String pin) {
        this.username = username;
        this.password = password;
        this.name = name;
        this.email = email;
        this.pin = pin;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPin() { return pin; }
    public void setPin(String pin) { this.pin = pin; }

    public java.time.LocalDate getLastLoginDate() { return lastLoginDate; }
    public void setLastLoginDate(java.time.LocalDate lastLoginDate) { this.lastLoginDate = lastLoginDate; }

    public java.time.LocalDate getLastReliefDate() { return lastReliefDate; }
    public void setLastReliefDate(java.time.LocalDate lastReliefDate) { this.lastReliefDate = lastReliefDate; }

    // 기존 랭킹 등에서 사용하던 nickname 호환용 (name 반환)
    public String getNickname() { return name != null ? name : username; }
}