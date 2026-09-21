package com.tardistock.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "limit_order",
        indexes = {
                @Index(
                        name = "idx_limit_order_status_symbol",
                        columnList = "status, symbol"
                ),
                @Index(
                        name = "idx_limit_order_member_created",
                        columnList = "member_id, created_at"
                )
        }
)
public class LimitOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(nullable = false, length = 8)
    private String side;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(nullable = false)
    private int amount;

    @Column(nullable = false)
    private double limitPrice;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime completedAt;
    private Double fillPrice;

    @Column(length = 255)
    private String resultMessage;

    public LimitOrder() {}

    public LimitOrder(
            Member member,
            String side,
            String symbol,
            int amount,
            double limitPrice,
            LocalDateTime createdAt) {
        this.member = member;
        this.side = side;
        this.symbol = symbol;
        this.amount = amount;
        this.limitPrice = limitPrice;
        this.createdAt = createdAt;
        this.status = "PENDING";
    }

    public Long getId() { return id; }
    public Member getMember() { return member; }
    public String getSide() { return side; }
    public String getSymbol() { return symbol; }
    public int getAmount() { return amount; }
    public double getLimitPrice() { return limitPrice; }
    public String getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public Double getFillPrice() { return fillPrice; }
    public String getResultMessage() { return resultMessage; }

    public boolean isPending() {
        return "PENDING".equals(status);
    }

    public void fill(double fillPrice, LocalDateTime completedAt) {
        this.status = "FILLED";
        this.fillPrice = fillPrice;
        this.completedAt = completedAt;
        this.resultMessage = "체결 완료";
    }

    public void cancel(LocalDateTime completedAt) {
        this.status = "CANCELLED";
        this.completedAt = completedAt;
        this.resultMessage = "사용자 취소";
    }

    public void reject(String message, LocalDateTime completedAt) {
        this.status = "REJECTED";
        this.completedAt = completedAt;
        this.resultMessage = message;
    }
}
