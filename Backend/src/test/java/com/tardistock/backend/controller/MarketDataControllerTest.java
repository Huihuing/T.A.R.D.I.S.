package com.tardistock.backend.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    @Test
    void globalNewsCoalescesConcurrentCacheMisses() throws Exception {
        RestTemplate restTemplate = mock(RestTemplate.class);
        NewsController controller = new NewsController(restTemplate);
        ReflectionTestUtils.setField(
                controller,
                "finnhubToken",
                "test-token"
        );

        CountDownLatch upstreamEntered = new CountDownLatch(1);
        CountDownLatch releaseUpstream = new CountDownLatch(1);
        when(restTemplate.getForEntity(anyString(), eq(String.class)))
                .thenAnswer(invocation -> {
                    upstreamEntered.countDown();
                    assertTrue(
                            releaseUpstream.await(2, TimeUnit.SECONDS)
                    );
                    return ResponseEntity.ok("[{\"headline\":\"ok\"}]");
                });

        int requestCount = 12;
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ResponseEntity<?>>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < requestCount; i++) {
                futures.add(executor.submit(() -> {
                    assertTrue(start.await(2, TimeUnit.SECONDS));
                    return controller.getGlobalNews("AAPL");
                }));
            }

            start.countDown();
            assertTrue(upstreamEntered.await(2, TimeUnit.SECONDS));

            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (controller.inFlightSizeForTest() != 1
                    && System.nanoTime() < deadline) {
                Thread.onSpinWait();
            }
            assertEquals(1, controller.inFlightSizeForTest());

            Thread.sleep(100);
            releaseUpstream.countDown();

            for (Future<ResponseEntity<?>> future : futures) {
                ResponseEntity<?> response =
                        future.get(2, TimeUnit.SECONDS);
                assertEquals(200, response.getStatusCode().value());
                assertEquals(
                        "[{\"headline\":\"ok\"}]",
                        response.getBody()
                );
            }

            verify(restTemplate, times(1))
                    .getForEntity(anyString(), eq(String.class));
            assertEquals(0, controller.inFlightSizeForTest());
        } finally {
            releaseUpstream.countDown();
            executor.shutdownNow();
        }
    }
}
