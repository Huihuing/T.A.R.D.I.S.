package com.tardistock.backend.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Entity
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 📜 어떤 유저의 지갑인지 연결 (1:1 관계)
    @OneToOne
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance; // 잔고

    public Wallet() {}

    public Wallet(Member member, double balance) {
        this.member = member;
        this.balance = money(balance);
    }

    public double getBalance() { return balance.doubleValue(); }
    public void setBalance(double balance) { this.balance = money(balance); }
    public Member getMember() { return member; }

    private static BigDecimal money(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("잔액은 유한한 숫자여야 합니다.");
        }
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }
}
