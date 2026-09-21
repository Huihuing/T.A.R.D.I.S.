package com.tardistock.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "portfolio_snapshot",
        indexes = {
                @Index(
                        name = "idx_portfolio_snapshot_member_time",
                        columnList = "member_id, captured_at"
                )
        }
)
public class PortfolioSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(nullable = false)
    private double cashBalance;

    @Column(nullable = false)
    private double investedValue;

    @Column(nullable = false)
    private double totalAssets;

    @Column(name = "captured_at", nullable = false)
    private LocalDateTime capturedAt;

    public PortfolioSnapshot() {}

    public PortfolioSnapshot(
            Member member,
            double cashBalance,
            double investedValue,
            double totalAssets,
            LocalDateTime capturedAt) {
        this.member = member;
        this.cashBalance = cashBalance;
        this.investedValue = investedValue;
        this.totalAssets = totalAssets;
        this.capturedAt = capturedAt;
    }

    public Long getId() { return id; }
    public double getCashBalance() { return cashBalance; }
    public double getInvestedValue() { return investedValue; }
    public double getTotalAssets() { return totalAssets; }
    public LocalDateTime getCapturedAt() { return capturedAt; }
}
