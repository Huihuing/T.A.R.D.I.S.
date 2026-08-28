package com.tardistock.backend.entity;

import jakarta.persistence.*;

@Entity
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 📜 어떤 유저의 지갑인지 연결 (1:1 관계)
    @OneToOne
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    private double balance; // 잔고

    public Wallet() {}

    public Wallet(Member member, double balance) {
        this.member = member;
        this.balance = balance;
    }

    public double getBalance() { return balance; }
    public void setBalance(double balance) { this.balance = balance; }
    public Member getMember() { return member; }
}