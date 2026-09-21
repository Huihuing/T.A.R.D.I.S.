package com.tardistock.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

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

    @Column(nullable = false)
    private double targetPrice;

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
        this.targetPrice = targetPrice;
        this.createdAt = createdAt;
        this.active = true;
    }

    public Long getId() { return id; }
    public Member getMember() { return member; }
    public String getSymbol() { return symbol; }
    public String getDirection() { return direction; }
    public double getTargetPrice() { return targetPrice; }
    public boolean isActive() { return active; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getTriggeredAt() { return triggeredAt; }

    public void trigger(LocalDateTime triggeredAt) {
        this.active = false;
        this.triggeredAt = triggeredAt;
    }
}
