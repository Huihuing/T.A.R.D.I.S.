package com.tardistock.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "ledger_entry",
        indexes = {
                @Index(name = "idx_ledger_member_time", columnList = "member_id, created_at")
        }
)
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(nullable = false, length = 40)
    private String type;

    @Column(nullable = false)
    private double amount;

    @Column(nullable = false)
    private double balanceAfter;

    @Column(length = 80)
    private String counterparty;

    @Column(length = 20)
    private String symbol;

    @Column(length = 255)
    private String description;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public LedgerEntry() {}

    public LedgerEntry(
            Member member,
            String type,
            double amount,
            double balanceAfter,
            String counterparty,
            String symbol,
            String description,
            LocalDateTime createdAt) {
        this.member = member;
        this.type = type;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.counterparty = counterparty;
        this.symbol = symbol;
        this.description = description;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getType() { return type; }
    public double getAmount() { return amount; }
    public double getBalanceAfter() { return balanceAfter; }
    public String getCounterparty() { return counterparty; }
    public String getSymbol() { return symbol; }
    public String getDescription() { return description; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
