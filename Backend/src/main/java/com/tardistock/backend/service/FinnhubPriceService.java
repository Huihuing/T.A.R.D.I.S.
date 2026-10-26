package com.tardistock.backend.service;

import com.tardistock.backend.config.ExternalApiHttpClient;
import com.tardistock.backend.util.DecimalMath;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Clock;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Finnhub REST API를 통해 실시간 주식 시세를 조회하는 서비스.
 * Rate Limit(분당 60회) 보호를 위해 종목별로 60초 인메모리 캐시를 사용합니다.
 */
@Service
public class FinnhubPriceService {

    private static final Logger log =
            LoggerFactory.getLogger(FinnhubPriceService.class);
    private static final Pattern SYMBOL_PATTERN =
            Pattern.compile("^[A-Z0-9.-]{1,15}$");
    private static final int CACHE_SECONDS = 60;
    private static final int TRADING_QUOTE_MAX_AGE_SECONDS = 120;
    private static final int MAX_FUTURE_SKEW_SECONDS = 30;
    private static final int MAX_CACHE_ENTRIES = 500;
    private static final int MARKET_PRICE_PRECISION = 19;
    private static final int MARKET_PRICE_SCALE = 6;

    @Value("${finnhub.api.key}")
    private String apiKey;

    private final RestTemplate restTemplate;
    private final Clock clock;
    private final MeterRegistry metrics;
    private final ConcurrentHashMap<String, CachedPrice> cache =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CompletableFuture<Double>> inFlight =
            new ConcurrentHashMap<>();

    public FinnhubPriceService() {
        this(ExternalApiHttpClient.create());
    }

    @Autowired
    public FinnhubPriceService(MeterRegistry metrics) {
        this(ExternalApiHttpClient.create(), Clock.systemUTC(), metrics);
    }

    FinnhubPriceService(RestTemplate restTemplate) {
        this(restTemplate, Clock.systemUTC());
    }

    FinnhubPriceService(RestTemplate restTemplate, Clock clock) {
        this(restTemplate, clock, Metrics.globalRegistry);
    }

    FinnhubPriceService(RestTemplate restTemplate, Clock clock, MeterRegistry metrics) {
        this.restTemplate = restTemplate;
        this.clock = clock;
        this.metrics = metrics;
    }

    /**
     * 종목 심볼의 현재가를 반환합니다.
     * 캐시가 유효하면 캐시 값을 반환하고, 만료되었으면 Finnhub API를 호출합니다.
     * 동일 종목의 동시 cache miss는 한 번의 외부 호출만 수행합니다.
     * API 호출 실패 시 만료된 캐시 값(있으면)을 반환하며, 없으면 0.0을 반환합니다.
     *
     * @param symbol 종목 심볼 (e.g. "AAPL", "TSLA")
     * @return 현재가 (double), 조회 실패 시 0.0
     */
    public double getPrice(String symbol) {
        String normalized = normalizeSymbol(symbol);
        if (normalized == null) {
            return 0.0;
        }

        CachedPrice cached = cache.get(normalized);
        if (isFresh(cached)) {
            return cached.price;
        }

        CompletableFuture<Double> candidate = new CompletableFuture<>();
        CompletableFuture<Double> existing =
                inFlight.putIfAbsent(normalized, candidate);

        if (existing != null) {
            try {
                return existing.join();
            } catch (Exception e) {
                CachedPrice fallback = cache.get(normalized);
                return fallback != null ? fallback.price : 0.0;
            }
        }

        try {
            double price = refreshPrice(normalized, cached);
            candidate.complete(price);
            return price;
        } catch (RuntimeException e) {
            candidate.completeExceptionally(e);
            throw e;
        } finally {
            inFlight.remove(normalized, candidate);
        }
    }

    /**
     * 신규 거래 전용 가격. 표시용 stale fallback은 절대 체결에 사용하지 않습니다.
     * 조회 TTL 60초 및 공급자 시세 시각 120초(미래 오차 30초)를 모두 검증합니다.
     * 장 마감 시세, 누락된 시각, 갱신 실패 시 0을 반환해 체결을 보류합니다.
     */
    public double getTradingPrice(String symbol) {
        String normalized = normalizeSymbol(symbol);
        if (normalized == null) return 0.0;

        getPrice(normalized);
        CachedPrice cached = cache.get(normalized);
        Instant now = clock.instant();
        if (!isFresh(cached)
                || cached.quotedAt == null
                || cached.quotedAt.isBefore(now.minusSeconds(TRADING_QUOTE_MAX_AGE_SECONDS))
                || cached.quotedAt.isAfter(now.plusSeconds(MAX_FUTURE_SKEW_SECONDS))) {
            return 0.0;
        }
        // Use the same unit price for cash math and DECIMAL(19,6) trade history.
        // Otherwise discarded fractional digits can accumulate into real cents
        // when a large number of shares is traded.
        BigDecimal tradingPrice = BigDecimal.valueOf(cached.price)
                .setScale(MARKET_PRICE_SCALE, RoundingMode.HALF_UP);
        return tradingPrice.signum() > 0 ? tradingPrice.doubleValue() : 0.0;
    }

    private double refreshPrice(
            String normalized,
            CachedPrice staleCache) {
        String outcome = "failure";
        try {
            String url =
                    "https://finnhub.io/api/v1/quote?symbol="
                            + normalized
                            + "&token="
                            + apiKey;
            Map<?, ?> response =
                    restTemplate.getForObject(url, Map.class);

            if (response != null && response.containsKey("c")) {
                Object cVal = response.get("c");
                double currentPrice =
                        Double.parseDouble(cVal.toString());

                if (currentPrice > 0
                        && DecimalMath.fits(
                                currentPrice,
                                MARKET_PRICE_PRECISION,
                                MARKET_PRICE_SCALE
                        )) {
                    putBounded(
                            normalized,
                            new CachedPrice(
                                    currentPrice,
                                    clock.instant(),
                                    quoteTimestamp(response.get("t"))
                            )
                    );
                    log.debug(
                            "Finnhub price refreshed for {}",
                            normalized
                    );
                    outcome = "success";
                    return currentPrice;
                }

                log.warn(
                        "Finnhub returned an unsupported price for {}",
                        normalized
                );
            }
        } catch (Exception e) {
            if (e instanceof HttpClientErrorException.TooManyRequests) {
                outcome = "rate_limited";
            }
            log.warn(
                    "Finnhub price request failed for {}: {}",
                    normalized,
                    e.getClass().getSimpleName()
            );
        } finally {
            metrics.counter("tardis.finnhub.requests", "endpoint", "trading_price",
                    "outcome", outcome).increment();
        }

        return staleCache != null ? staleCache.price : 0.0;
    }

    private boolean isFresh(CachedPrice cached) {
        return cached != null
                && cached.cachedAt
                .plusSeconds(CACHE_SECONDS)
                .isAfter(clock.instant());
    }

    private Instant quoteTimestamp(Object value) {
        if (value == null) return null;
        try {
            long seconds = new java.math.BigDecimal(value.toString()).longValueExact();
            return seconds > 0 ? Instant.ofEpochSecond(seconds) : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private void putBounded(String symbol, CachedPrice price) {
        synchronized (cache) {
            if (!cache.containsKey(symbol)
                    && cache.size() >= MAX_CACHE_ENTRIES) {
                cache.entrySet().stream()
                        .min(Comparator.comparing(
                                entry -> entry.getValue().cachedAt
                        ))
                        .map(Map.Entry::getKey)
                        .ifPresent(cache::remove);
            }
            cache.put(symbol, price);
        }
    }

    private String normalizeSymbol(String symbol) {
        if (symbol == null) return null;
        String normalized =
                symbol.trim().toUpperCase(Locale.ROOT);
        return SYMBOL_PATTERN.matcher(normalized).matches()
                ? normalized
                : null;
    }

    int cacheSizeForTest() {
        return cache.size();
    }

    private static class CachedPrice {
        final double price;
        final Instant cachedAt;
        final Instant quotedAt;

        CachedPrice(double price, Instant cachedAt, Instant quotedAt) {
            this.price = price;
            this.cachedAt = cachedAt;
            this.quotedAt = quotedAt;
        }
    }
}
