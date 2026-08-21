package com.tardistock.backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class TradeHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String tradeType;
    private String symbol;
    private int amount;
    private double price;
    private LocalDateTime tradeTime;

    public TradeHistory() {
    }

    public TradeHistory(String tradeType, String symbol, int amount, double price) {
        this.tradeType = tradeType;
        this.symbol = symbol;
        this.amount = amount;
        this.price = price;
        this.tradeTime = LocalDateTime.now();
    }

    // ==========================================
    // 📜 프론트엔드로 데이터를 보내기 위해 꼭 필요한 Getter!
    // ==========================================
    public Long getId() {
        return id;
    }

    public String getTradeType() {
        return tradeType;
    }

    public String getSymbol() {
        return symbol;
    }

    public int getAmount() {
        return amount;
    }

    public double getPrice() {
        return price;
    }

    public LocalDateTime getTradeTime() {
        return tradeTime;
    }
}