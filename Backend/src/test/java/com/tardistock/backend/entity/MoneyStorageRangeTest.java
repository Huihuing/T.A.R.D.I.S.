package com.tardistock.backend.entity;

import com.tardistock.backend.util.MoneyMath;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoneyStorageRangeTest {

    private static final double IN_RANGE = 10_000_000_000_000_000d;
    private static final double OUT_OF_RANGE = 100_000_000_000_000_000d;

    @Test
    void moneyMathMatchesDecimal19Scale2Range() {
        assertTrue(MoneyMath.fitsCents(IN_RANGE));
        assertFalse(MoneyMath.fitsCents(OUT_OF_RANGE));
        assertFalse(MoneyMath.fitsCents(Double.POSITIVE_INFINITY));
    }

    @Test
    void walletRejectsBalanceOutsideDecimal19Scale2() {
        Member member = new Member();

        assertDoesNotThrow(() -> new Wallet(member, IN_RANGE));
        assertThrows(
                IllegalArgumentException.class,
                () -> new Wallet(member, OUT_OF_RANGE)
        );
    }

    @Test
    void ledgerRejectsAmountOrBalanceOutsideDecimal19Scale2() {
        Member member = new Member();
        LocalDateTime now = LocalDateTime.now();

        assertDoesNotThrow(() -> new LedgerEntry(
                member,
                "TEST",
                IN_RANGE,
                IN_RANGE,
                null,
                null,
                "test",
                now
        ));
        assertThrows(
                IllegalArgumentException.class,
                () -> new LedgerEntry(
                        member,
                        "TEST",
                        OUT_OF_RANGE,
                        IN_RANGE,
                        null,
                        null,
                        "test",
                        now
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new LedgerEntry(
                        member,
                        "TEST",
                        1.0,
                        OUT_OF_RANGE,
                        null,
                        null,
                        "test",
                        now
                )
        );
    }

    @Test
    void portfolioSnapshotRejectsValueOutsideDecimal19Scale2() {
        Member member = new Member();
        LocalDateTime now = LocalDateTime.now();

        assertDoesNotThrow(() -> new PortfolioSnapshot(
                member,
                IN_RANGE,
                IN_RANGE,
                IN_RANGE,
                now
        ));
        assertThrows(
                IllegalArgumentException.class,
                () -> new PortfolioSnapshot(
                        member,
                        IN_RANGE,
                        OUT_OF_RANGE,
                        OUT_OF_RANGE,
                        now
                )
        );
    }
}
