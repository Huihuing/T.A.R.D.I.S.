package com.tardistock.backend.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Entity
public class Bookmark {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(nullable = false)
    private String symbol;

    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal price;

    public Bookmark() {}

    public Bookmark(Member member, String symbol, double price) {
        this.member = member;
        this.symbol = symbol;
        this.price = price(price);
    }

    public Long getId() { return id; }
    public Member getMember() { return member; }
    public void setMember(Member member) { this.member = member; }
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public double getPrice() { return price.doubleValue(); }
    public void setPrice(double price) { this.price = price(price); }

    private static BigDecimal price(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("북마크 가격은 유한한 숫자여야 합니다.");
        }
        return BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP);
    }
}
