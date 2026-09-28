package com.tardistock.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Entity
public class Portfolio {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    private String symbol;
    private int amount;

    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal averagePrice;

    public Portfolio() {}

    public Portfolio(Member member, String symbol, int amount, double averagePrice) {
        this.member = member;
        this.symbol = symbol;
        this.amount = amount;
        this.averagePrice = price(averagePrice);
    }

    public Long getId() { return id; }
    public Member getMember() { return member; }
    public String getSymbol() { return symbol; }
    public int getAmount() { return amount; }
    public void setAmount(int amount) { this.amount = amount; }
    public double getAveragePrice() { return averagePrice.doubleValue(); }
    public void setAveragePrice(double averagePrice) { this.averagePrice = price(averagePrice); }

    private static BigDecimal price(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("평균 매입가는 유한한 숫자여야 합니다.");
        }
        return BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP);
    }
}
