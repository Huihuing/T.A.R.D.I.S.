package com.tardistock.backend.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Entity
public class Bookmark {

    private static final int PRICE_PRECISION = 19;
    private static final int PRICE_SCALE = 6;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(nullable = false)
    private String symbol;

    @Column(nullable = false, precision = PRICE_PRECISION, scale = PRICE_SCALE)
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

    public static boolean isPersistablePrice(double value) {
        if (!Double.isFinite(value) || value < 0) {
            return false;
        }
        return normalizePrice(value).precision() <= PRICE_PRECISION;
    }

    private static BigDecimal price(double value) {
        if (!isPersistablePrice(value)) {
            throw new IllegalArgumentException(
                    "북마크 가격은 DECIMAL(19,6) 범위의 0 이상 유한한 숫자여야 합니다."
            );
        }
        return normalizePrice(value);
    }

    private static BigDecimal normalizePrice(double value) {
        return BigDecimal.valueOf(value).setScale(PRICE_SCALE, RoundingMode.HALF_UP);
    }
}
