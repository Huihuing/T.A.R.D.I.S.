package com.tardistock.backend.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class Wallet {

    @Id
    private Long id; // 아직 로그인 기능이 없으므로, ID를 1번으로 고정해서 내 지갑으로 씁니다.
    private double balance; // 내 잔고

    public Wallet() {
    }

    public Wallet(Long id, double balance) {
        this.id = id;
        this.balance = balance;
    }

    public Long getId() {
        return id;
    }

    public double getBalance() {
        return balance;
    }

    public void setBalance(double balance) {
        this.balance = balance;
    }
}