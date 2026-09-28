package com.tardistock.backend.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertThrows;

class MarketPriceEntityTest {

    @Test
    void tradeHistoryRejectsPriceOutsideDecimal19Scale6() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new TradeHistory(
                        new Member(),
                        "BUY",
                        "AAPL",
                        1,
                        10_000_000_000_000d,
                        LocalDateTime.now()
                )
        );
    }

    @Test
    void portfolioRejectsAveragePriceOutsideDecimal19Scale6() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Portfolio(
                        new Member(),
                        "AAPL",
                        1,
                        10_000_000_000_000d
                )
        );
    }
}
