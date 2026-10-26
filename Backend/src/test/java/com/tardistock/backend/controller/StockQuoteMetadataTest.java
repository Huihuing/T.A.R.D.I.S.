package com.tardistock.backend.controller;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class StockQuoteMetadataTest {
    private static final String URL = "https://finnhub.io/api/v1/quote?symbol=AAPL&token=test-token";
    private static final String QUOTE = "{\"c\":100,\"d\":1,\"dp\":1,\"t\":1700000000}";

    @Test
    void freshCachePreservesProviderTimestampAndOriginalFetchTimeWithoutAnotherProviderCall() {
        Fixture fixture = new Fixture();
        fixture.server.expect(requestTo(URL)).andRespond(withSuccess(QUOTE, MediaType.APPLICATION_JSON));
        ResponseEntity<?> first = fixture.controller.getStockQuote("AAPL");
        fixture.clock.time += 5_000;
        ResponseEntity<?> cached = fixture.controller.getStockQuote("aapl");
        assertEquals(false, body(cached).get("stale"));
        assertEquals(body(first).get("fetchedAt"), body(cached).get("fetchedAt"));
        assertEquals(1700000000, body(cached).get("t"));
        assertEquals("false", cached.getHeaders().getFirst("X-Tardis-Quote-Stale"));
        assertEquals(1, fixture.registry.get("tardis.finnhub.requests").tag("outcome", "success").counter().count());
        assertEquals(1, fixture.registry.get("tardis.market.quote.responses").tag("state", "fresh_cache").counter().count());
        fixture.server.verify();
    }

    @Test
    void providerFailureReturnsExplicitStaleMetadataAndPreservesQuoteTimes() {
        Fixture fixture = new Fixture();
        fixture.server.expect(requestTo(URL)).andRespond(withSuccess(QUOTE, MediaType.APPLICATION_JSON));
        fixture.server.expect(requestTo(URL)).andRespond(withServerError());
        ResponseEntity<?> first = fixture.controller.getStockQuote("AAPL");
        fixture.clock.time += 10_001;
        ResponseEntity<?> fallback = fixture.controller.getStockQuote("AAPL");
        assertEquals(200, fallback.getStatusCode().value());
        assertEquals(true, body(fallback).get("stale"));
        assertEquals(body(first).get("fetchedAt"), body(fallback).get("fetchedAt"));
        assertEquals(body(first).get("t"), body(fallback).get("t"));
        assertEquals("true", fallback.getHeaders().getFirst("X-Tardis-Quote-Stale"));
        assertEquals(first.getHeaders().getFirst("X-Tardis-Quote-Fetched-At"), fallback.getHeaders().getFirst("X-Tardis-Quote-Fetched-At"));
        assertEquals(1, fixture.registry.get("tardis.finnhub.requests").tag("outcome", "failure").counter().count());
        fixture.server.verify();
    }

    @Test
    void rateLimitFallbackIsMarkedStaleAndCountedWithoutLoggingSecrets() {
        Fixture fixture = new Fixture();
        fixture.server.expect(requestTo(URL)).andRespond(withSuccess(QUOTE, MediaType.APPLICATION_JSON));
        fixture.server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        fixture.controller.getStockQuote("AAPL");
        fixture.clock.time += 10_001;
        assertEquals(true, body(fixture.controller.getStockQuote("AAPL")).get("stale"));
        assertEquals(1, fixture.registry.get("tardis.finnhub.requests").tag("outcome", "rate_limited").counter().count());
        fixture.server.verify();
    }

    @Test
    void expiredFallbackIsRejectedInsteadOfReturningAnUnboundedOldQuote() {
        Fixture fixture = new Fixture();
        fixture.server.expect(requestTo(URL)).andRespond(withSuccess(QUOTE, MediaType.APPLICATION_JSON));
        fixture.server.expect(requestTo(URL)).andRespond(withServerError());
        fixture.controller.getStockQuote("AAPL");
        fixture.clock.time += 310_001;
        assertEquals(502, fixture.controller.getStockQuote("AAPL").getStatusCode().value());
        fixture.server.verify();
    }

    private static Map<?, ?> body(ResponseEntity<?> response) { return (Map<?, ?>) response.getBody(); }

    private static class Fixture {
        final MutableClock clock = new MutableClock();
        final SimpleMeterRegistry registry = new SimpleMeterRegistry();
        final RestTemplate template = new RestTemplate();
        final MockRestServiceServer server = MockRestServiceServer.bindTo(template).build();
        final StockController controller = new StockController(template, registry, clock);
        Fixture() { ReflectionTestUtils.setField(controller, "finnhubToken", "test-token"); }
    }

    private static class MutableClock extends Clock {
        long time = 1_700_000_000_000L;
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return Instant.ofEpochMilli(time); }
        @Override public long millis() { return time; }
    }
}
