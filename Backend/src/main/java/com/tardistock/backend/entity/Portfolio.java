package com.tardistock.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.tardistock.backend.util.DecimalMath;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Entity
public class Portfolio {

    private static final int PRICE_PRECISION = 19;
    private static final int PRICE_SCALE = 6;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    private String symbol;
    private int amount;

    @Column(nullable = false, precision = PRICE_PRECISION, scale = PRICE_SCALE)
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

    public static boolean isPersistablePrice(double value) {
        return DecimalMath.fits(value, PRICE_PRECISION, PRICE_SCALE);
    }

    private static BigDecimal price(double value) {
        if (!isPersistablePrice(value)) {
            throw new IllegalArgumentException(
                    "평균 매입가는 DECIMAL(19,6) 범위의 유한한 숫자여야 합니다."
            );
        }
        return BigDecimal.valueOf(value).setScale(PRICE_SCALE, RoundingMode.HALF_UP);
    }
}
