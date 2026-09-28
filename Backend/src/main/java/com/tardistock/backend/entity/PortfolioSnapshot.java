package com.tardistock.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.tardistock.backend.util.MoneyMath;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

    @Column(
            nullable = false,
            precision = MoneyMath.MONEY_PRECISION,
            scale = MoneyMath.MONEY_SCALE
    )
    private BigDecimal cashBalance;

    @Column(
            nullable = false,
            precision = MoneyMath.MONEY_PRECISION,
            scale = MoneyMath.MONEY_SCALE
    )
    private BigDecimal investedValue;

    @Column(
            nullable = false,
            precision = MoneyMath.MONEY_PRECISION,
            scale = MoneyMath.MONEY_SCALE
    )
    private BigDecimal totalAssets;

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
        this.cashBalance = money(cashBalance);
        this.investedValue = money(investedValue);
        this.totalAssets = money(totalAssets);
        this.capturedAt = capturedAt;
    }

    public Long getId() { return id; }
    public double getCashBalance() { return cashBalance.doubleValue(); }
    public double getInvestedValue() { return investedValue.doubleValue(); }
    public double getTotalAssets() { return totalAssets.doubleValue(); }
    public LocalDateTime getCapturedAt() { return capturedAt; }

    private static BigDecimal money(double value) {
        if (!MoneyMath.fitsCents(value)) {
            throw new IllegalArgumentException(
                    "자산 스냅샷 금액은 DECIMAL(19,2) 범위의 유한한 숫자여야 합니다."
            );
        }
        return BigDecimal.valueOf(value)
                .setScale(MoneyMath.MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
