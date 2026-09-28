package com.tardistock.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "price_alert",
        indexes = {
                @Index(
                        name = "idx_price_alert_active_symbol",
                        columnList = "active, symbol"
                )
        }
)
public class PriceAlert {

    private static final int PRICE_PRECISION = 19;
    private static final int PRICE_SCALE = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(nullable = false, length = 10)
    private String direction;

    @Column(nullable = false, precision = PRICE_PRECISION, scale = PRICE_SCALE)
    private BigDecimal targetPrice;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime triggeredAt;

    public PriceAlert() {}

    public PriceAlert(
            Member member,
            String symbol,
            String direction,
            double targetPrice,
            LocalDateTime createdAt) {
        this.member = member;
        this.symbol = symbol;
        this.direction = direction;
        this.targetPrice = money(targetPrice);
        this.createdAt = createdAt;
        this.active = true;
    }

    public Long getId() { return id; }
    public Member getMember() { return member; }
    public String getSymbol() { return symbol; }
    public String getDirection() { return direction; }
    public double getTargetPrice() { return targetPrice.doubleValue(); }
    public boolean isActive() { return active; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getTriggeredAt() { return triggeredAt; }

    public void trigger(LocalDateTime triggeredAt) {
        this.active = false;
        this.triggeredAt = triggeredAt;
    }

    public static boolean isPersistablePrice(double value) {
        if (!Double.isFinite(value)) {
            return false;
        }
        return normalizeMoney(value).precision() <= PRICE_PRECISION;
    }

    private static BigDecimal money(double value) {
        if (!isPersistablePrice(value)) {
            throw new IllegalArgumentException(
                    "목표 가격은 DECIMAL(19,2) 범위의 유한한 숫자여야 합니다."
            );
        }
        return normalizeMoney(value);
    }

    private static BigDecimal normalizeMoney(double value) {
        return BigDecimal.valueOf(value).setScale(PRICE_SCALE, RoundingMode.HALF_UP);
    }
}
