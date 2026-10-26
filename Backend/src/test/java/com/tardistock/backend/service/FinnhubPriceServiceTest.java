package com.tardistock.backend.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.math.BigDecimal;
import java.math.RoundingMode;
import com.tardistock.backend.entity.Member;
import com.tardistock.backend.entity.TradeHistory;
import java.time.LocalDateTime;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FinnhubPriceServiceTest {

    @Test
    void tradingPriceMatchesStoredSixDecimalUnitPriceBeforeMultiplyingShares() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        MutableClock clock = new MutableClock();
        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenReturn(Map.of("c", 0.1234567, "t", clock.instant().getEpochSecond()));
        FinnhubPriceService service = new FinnhubPriceService(restTemplate, clock);

        assertEquals(0.1234567, service.getPrice("AAPL"));
        double price = service.getTradingPrice("AAPL");
        assertEquals(0.123457, price);
        TradeHistory history = new TradeHistory(new Member(), "BUY", "AAPL", 1_000_000,
                price, LocalDateTime.now());
        BigDecimal chargedTotal = BigDecimal.valueOf(price).multiply(BigDecimal.valueOf(1_000_000))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal recordedTotal = BigDecimal.valueOf(history.getPrice()).multiply(BigDecimal.valueOf(history.getAmount()))
                .setScale(2, RoundingMode.HALF_UP);
        assertEquals(new BigDecimal("123457.00"), chargedTotal);
        assertEquals(chargedTotal, recordedTotal);
    }

    @Test
    void positiveProviderPriceThatRoundsToZeroAtStoragePrecisionCannotTrade() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        MutableClock clock = new MutableClock();
        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenReturn(Map.of("c", 0.0000001, "t", clock.instant().getEpochSecond()));
        FinnhubPriceService service = new FinnhubPriceService(restTemplate, clock);

        assertEquals(0.0000001, service.getPrice("AAPL"));
        assertEquals(0.0, service.getTradingPrice("AAPL"));
    }

    @Test
    void expiredDisplayFallbackCannotBeUsedForTradingDuringNetworkFailure() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        MutableClock clock = new MutableClock();
        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenReturn(Map.of("c", 123.45, "t", clock.instant().getEpochSecond()))
                .thenThrow(new ResourceAccessException("offline"));
        FinnhubPriceService service = new FinnhubPriceService(restTemplate, clock);

        assertEquals(123.45, service.getTradingPrice("AAPL"));
        clock.advanceSeconds(60);

        assertEquals(123.45, service.getPrice("AAPL"));
        assertEquals(0.0, service.getTradingPrice("AAPL"));
    }

    @Test
    void rateLimitDoesNotAllowTradingWithExpiredCache() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        MutableClock clock = new MutableClock();
        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenReturn(Map.of("c", 200, "t", clock.instant().getEpochSecond()))
                .thenThrow(new HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS));
        FinnhubPriceService service = new FinnhubPriceService(restTemplate, clock);

        assertEquals(200, service.getTradingPrice("AAPL"));
        clock.advanceSeconds(61);
        assertEquals(0.0, service.getTradingPrice("AAPL"));
    }

    @Test
    void freshFetchOfOldProviderQuoteIsDisplayOnly() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        MutableClock clock = new MutableClock();
        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenReturn(Map.of("c", 123.45, "t", clock.instant().minusSeconds(121).getEpochSecond()));
        FinnhubPriceService service = new FinnhubPriceService(restTemplate, clock);

        assertEquals(123.45, service.getPrice("AAPL"));
        assertEquals(0.0, service.getTradingPrice("AAPL"));
        verify(restTemplate, times(1)).getForObject(anyString(), eq(Map.class));
    }

    @Test
    void providerTimestampAgeIsRecheckedWhileFetchCacheIsFresh() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        MutableClock clock = new MutableClock();
        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenReturn(Map.of("c", 123.45, "t", clock.instant().minusSeconds(120).getEpochSecond()));
        FinnhubPriceService service = new FinnhubPriceService(restTemplate, clock);

        assertEquals(123.45, service.getTradingPrice("AAPL"));
        clock.advanceSeconds(1);
        assertEquals(0.0, service.getTradingPrice("AAPL"));
        verify(restTemplate, times(1)).getForObject(anyString(), eq(Map.class));
    }

    @Test
    void missingMalformedAndFutureProviderTimestampsNeverTrade() {
        for (Object timestamp : List.of("missing", "not-a-time", "Infinity", 0, -1, 1.5,
                Instant.parse("2026-10-26T00:00:31Z").getEpochSecond())) {
            RestTemplate restTemplate = mock(RestTemplate.class);
            Map<String, Object> quote = timestamp.equals("missing")
                    ? Map.of("c", 100.0)
                    : Map.of("c", 100.0, "t", timestamp);
            when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(quote);
            FinnhubPriceService service = new FinnhubPriceService(restTemplate, new MutableClock());

            assertEquals(100.0, service.getPrice("AAPL"));
            assertEquals(0.0, service.getTradingPrice("AAPL"), timestamp.toString());
        }
    }

    @Test
    void invalidRefreshDoesNotMakeOldCachedPriceTradable() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        MutableClock clock = new MutableClock();
        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenReturn(Map.of("c", 123.45, "t", clock.instant().getEpochSecond()))
                .thenReturn(Map.of("c", "NaN", "t", clock.instant().getEpochSecond()));
        FinnhubPriceService service = new FinnhubPriceService(restTemplate, clock);

        assertEquals(123.45, service.getTradingPrice("AAPL"));
        clock.advanceSeconds(60);
        assertEquals(0.0, service.getTradingPrice("AAPL"));
    }

    @Test
    void reusesFreshPriceCache() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenReturn(Map.of("c", 123.45));

        FinnhubPriceService service = service(restTemplate);

        assertEquals(123.45, service.getPrice("AAPL"));
        assertEquals(123.45, service.getPrice("aapl"));

        verify(restTemplate, times(1))
                .getForObject(anyString(), eq(Map.class));
    }

    @Test
    void concurrentCacheMissesShareOneFinnhubCall() throws Exception {
        RestTemplate restTemplate = mock(RestTemplate.class);
        CountDownLatch callStarted = new CountDownLatch(1);
        CountDownLatch allowResponse = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();

        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenAnswer(invocation -> {
                    calls.incrementAndGet();
                    callStarted.countDown();
                    assertTrue(allowResponse.await(5, TimeUnit.SECONDS));
                    return Map.of("c", 222.22);
                });

        FinnhubPriceService service = service(restTemplate);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Double> first =
                    executor.submit(() -> service.getPrice("MSFT"));

            assertTrue(callStarted.await(5, TimeUnit.SECONDS));

            Future<Double> second =
                    executor.submit(() -> service.getPrice("msft"));

            allowResponse.countDown();

            assertEquals(222.22, first.get(5, TimeUnit.SECONDS));
            assertEquals(222.22, second.get(5, TimeUnit.SECONDS));
            assertEquals(1, calls.get());
            verify(restTemplate, times(1))
                    .getForObject(anyString(), eq(Map.class));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void priceCacheRemainsBounded() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenReturn(Map.of("c", 10.0));

        FinnhubPriceService service = service(restTemplate);

        for (int i = 0; i < 501; i++) {
            service.getPrice("S" + i);
        }

        assertEquals(500, service.cacheSizeForTest());
        verify(restTemplate, times(501))
                .getForObject(anyString(), eq(Map.class));
    }

    @Test
    void priceCacheRemainsBoundedUnderConcurrentDistinctSymbols()
            throws Exception {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenReturn(Map.of("c", 10.0));

        FinnhubPriceService service = service(restTemplate);
        ExecutorService executor = Executors.newFixedThreadPool(32);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Double>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < 600; i++) {
                int index = i;
                futures.add(executor.submit(() -> {
                    assertTrue(start.await(5, TimeUnit.SECONDS));
                    return service.getPrice("C" + index);
                }));
            }

            start.countDown();

            for (Future<Double> future : futures) {
                assertEquals(10.0, future.get(10, TimeUnit.SECONDS));
            }

            assertEquals(500, service.cacheSizeForTest());
            verify(restTemplate, times(600))
                    .getForObject(anyString(), eq(Map.class));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void invalidSymbolDoesNotCallProvider() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        FinnhubPriceService service = service(restTemplate);

        assertEquals(0.0, service.getPrice("../../etc/passwd"));

        verify(restTemplate, times(0))
                .getForObject(anyString(), eq(Map.class));
    }

    private FinnhubPriceService service(RestTemplate restTemplate) {
        FinnhubPriceService service =
                new FinnhubPriceService(restTemplate);
        ReflectionTestUtils.setField(service, "apiKey", "test-key");
        return service;
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-26T00:00:00Z");

        void advanceSeconds(long seconds) { now = now.plusSeconds(seconds); }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
