package com.tardistock.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Entity
public class TradeHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    private String tradeType;
    private String symbol;
    private int amount;

    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal price;

    private LocalDateTime tradeTime;

    public TradeHistory() {}

    public TradeHistory(Member member, String tradeType, String symbol, int amount, double price, LocalDateTime tradeTime) {
        this.member = member;
        this.tradeType = tradeType;
        this.symbol = symbol;
        this.amount = amount;
        this.price = price(price);
        this.tradeTime = tradeTime;
    }

    public Long getId() { return id; }
    public Member getMember() { return member; }
    public String getTradeType() { return tradeType; }
    public String getSymbol() { return symbol; }
    public int getAmount() { return amount; }
    public double getPrice() { return price.doubleValue(); }
    public LocalDateTime getTradeTime() { return tradeTime; }

    private static BigDecimal price(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("체결 가격은 유한한 숫자여야 합니다.");
        }
        return BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP);
    }
}
