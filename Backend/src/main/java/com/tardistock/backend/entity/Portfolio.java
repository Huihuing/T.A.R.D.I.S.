package com.tardistock.backend.entity;

import jakarta.persistence.*;

@Entity
public class Portfolio {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 📜 주인이 누구인지(Member) 명시!
    @ManyToOne
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    private String symbol;
    private int amount;
    private double averagePrice;

    public Portfolio() {}

    public Portfolio(Member member, String symbol, int amount, double averagePrice) {
        this.member = member;
        this.symbol = symbol;
        this.amount = amount;
        this.averagePrice = averagePrice;
    }

    public Long getId() { return id; }
    public Member getMember() { return member; }
    public String getSymbol() { return symbol; }
    public int getAmount() { return amount; }
    public void setAmount(int amount) { this.amount = amount; }
    public double getAveragePrice() { return averagePrice; }
    public void setAveragePrice(double averagePrice) { this.averagePrice = averagePrice; }
}