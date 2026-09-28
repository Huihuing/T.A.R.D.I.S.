package com.tardistock.backend.entity;

import com.tardistock.backend.util.MoneyMath;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Entity
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 📜 어떤 유저의 지갑인지 연결 (1:1 관계)
    @OneToOne
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(
            nullable = false,
            precision = MoneyMath.MONEY_PRECISION,
            scale = MoneyMath.MONEY_SCALE
    )
    private BigDecimal balance; // 잔고

    public Wallet() {}

    public Wallet(Member member, double balance) {
        this.member = member;
        this.balance = money(balance);
    }

    public double getBalance() { return balance.doubleValue(); }
    public void setBalance(double balance) { this.balance = money(balance); }
    public Member getMember() { return member; }

    private static BigDecimal money(double value) {
        if (!MoneyMath.fitsCents(value)) {
            throw new IllegalArgumentException(
                    "잔액은 DECIMAL(19,2) 범위의 유한한 숫자여야 합니다."
            );
        }
        return BigDecimal.valueOf(value)
                .setScale(MoneyMath.MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
