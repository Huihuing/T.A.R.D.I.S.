package com.tardistock.backend.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FinnhubPriceMetricsTest {
    @Test
    void countsActualUpstreamCallsAndDoesNotCountCacheHits() {
        RestTemplate http = mock(RestTemplate.class);
        SimpleMeterRegistry metrics = new SimpleMeterRegistry();
        when(http.getForObject(anyString(), eq(Map.class)))
                .thenReturn(Map.of("c", 100, "t", Clock.systemUTC().instant().getEpochSecond()));
        FinnhubPriceService prices = new FinnhubPriceService(http, Clock.systemUTC(), metrics);

        assertEquals(100, prices.getTradingPrice("AAPL"));
        assertEquals(100, prices.getTradingPrice("aapl"));
        assertEquals(1, metrics.get("tardis.finnhub.requests")
                .tags("endpoint", "trading_price", "outcome", "success").counter().count());
        verify(http, times(1)).getForObject(anyString(), eq(Map.class));
    }

    @Test
    void countsProviderRateLimitWithoutReturningATradingPrice() {
        RestTemplate http = mock(RestTemplate.class);
        SimpleMeterRegistry metrics = new SimpleMeterRegistry();
        when(http.getForObject(anyString(), eq(Map.class))).thenThrow(
                HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS,
                        "rate limited", null, new byte[0], null));
        FinnhubPriceService prices = new FinnhubPriceService(http, Clock.systemUTC(), metrics);

        assertEquals(0, prices.getTradingPrice("AAPL"));
        assertEquals(1, metrics.get("tardis.finnhub.requests")
                .tags("endpoint", "trading_price", "outcome", "rate_limited").counter().count());
    }
}
