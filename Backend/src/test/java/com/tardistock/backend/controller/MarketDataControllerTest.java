package com.tardistock.backend.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MarketDataControllerTest {

    @Test
    void stockQuoteRejectsInvalidSymbolBeforeUpstreamCall() {
        StockController controller = new StockController();

        ResponseEntity<?> response =
                controller.getStockQuote("../AAPL?token=x");

        assertEquals(400, response.getStatusCode().value());
    }

    @Test
    void stockCandlesRejectUnsupportedResolution() {
        StockController controller = new StockController();

        ResponseEntity<?> response =
                controller.getStockCandles("AAPL", "YEAR");

        assertEquals(400, response.getStatusCode().value());
    }

    @Test
    void stockSearchRejectsBlankQuery() {
        StockController controller = new StockController();

        ResponseEntity<?> response =
                controller.searchStocks("   ");

        assertEquals(400, response.getStatusCode().value());
    }

    @Test
    void stockSearchRejectsOverlongQuery() {
        StockController controller = new StockController();

        ResponseEntity<?> response =
                controller.searchStocks("a".repeat(101));

        assertEquals(400, response.getStatusCode().value());
    }

    @Test
    void globalNewsRejectsInvalidSymbolBeforeUpstreamCall() {
        NewsController controller = new NewsController();

        ResponseEntity<?> response =
                controller.getGlobalNews("AAPL&token=other");

        assertEquals(400, response.getStatusCode().value());
    }

    @Test
    void koreanNewsRejectsOverlongQueryBeforeUpstreamCall() {
        NewsController controller = new NewsController();

        ResponseEntity<?> response =
                controller.getKoreanNews("가".repeat(121));

        assertEquals(400, response.getStatusCode().value());
    }
}
