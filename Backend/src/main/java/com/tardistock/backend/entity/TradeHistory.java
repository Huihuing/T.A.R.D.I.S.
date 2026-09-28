package com.tardistock.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.tardistock.backend.util.DecimalMath;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Entity
public class TradeHistory {

    private static final int PRICE_PRECISION = 19;
    private static final int PRICE_SCALE = 6;

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

    @Column(nullable = false, precision = PRICE_PRECISION, scale = PRICE_SCALE)
    private BigDecimal price;

    private LocalDateTime tradeTime;

    public TradeHistory() {}

    public TradeHistory(
            Member member,
            String tradeType,
            String symbol,
            int amount,
            double price,
            LocalDateTime tradeTime) {
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

    public static boolean isPersistablePrice(double value) {
        return DecimalMath.fits(value, PRICE_PRECISION, PRICE_SCALE);
    }

    private static BigDecimal price(double value) {
        if (!isPersistablePrice(value)) {
            throw new IllegalArgumentException(
                    "체결 가격은 DECIMAL(19,6) 범위의 유한한 숫자여야 합니다."
            );
        }
        return BigDecimal.valueOf(value).setScale(PRICE_SCALE, RoundingMode.HALF_UP);
    }
}
