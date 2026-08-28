package com.tardistock.backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class TradeHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 📜 영수증의 주인이 누구인지(Member) 명시!
    @ManyToOne
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    private String tradeType; 
    private String symbol;
    private int amount;
    private double price;
    private LocalDateTime tradeTime;

    public TradeHistory() {}

    public TradeHistory(Member member, String tradeType, String symbol, int amount, double price, LocalDateTime tradeTime) {
        this.member = member;
        this.tradeType = tradeType;
        this.symbol = symbol;
        this.amount = amount;
        this.price = price;
        this.tradeTime = tradeTime;
    }

    public Long getId() { return id; }
    public Member getMember() { return member; }
    public String getTradeType() { return tradeType; }
    public String getSymbol() { return symbol; }
    public int getAmount() { return amount; }
    public double getPrice() { return price; }
    public LocalDateTime getTradeTime() { return tradeTime; }
}