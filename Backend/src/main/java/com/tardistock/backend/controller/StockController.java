package com.tardistock.backend.controller;

import com.tardistock.backend.config.ExternalApiHttpClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/stock")
public class StockController {

    private static final Logger log =
            LoggerFactory.getLogger(StockController.class);
    private static final Pattern SYMBOL_PATTERN =
            Pattern.compile("^[A-Z0-9.-]{1,15}$");
    private static final int MAX_SEARCH_QUERY_LENGTH = 100;
    private static final long QUOTE_CACHE_MS = 10_000L;
    private static final long SYMBOL_CACHE_MS = 6 * 60 * 60 * 1000L;
    private static final long SEARCH_CACHE_MS = 60_000L;
    private static final long CANDLES_CACHE_MS = 5 * 60 * 1000L;
    private static final long QUOTE_STALE_MS = 5 * 60 * 1000L;
    private static final long SEARCH_STALE_MS = 10 * 60 * 1000L;
    private static final long CANDLES_STALE_MS = 60 * 60 * 1000L;
    private static final long SYMBOL_STALE_MS = 24 * 60 * 60 * 1000L;
    private static final int MAX_QUOTE_CACHE_ENTRIES = 500;
    private static final int MAX_SEARCH_CACHE_ENTRIES = 100;
    private static final int MAX_CANDLES_CACHE_ENTRIES = 100;

    @Value("${finnhub.api.key}")
    private String finnhubToken;

    private final RestTemplate restTemplate =
            ExternalApiHttpClient.create();
    private final ConcurrentHashMap<String, CacheEntry<Map<?, ?>>> quoteCache =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CacheEntry<String>> searchCache =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CacheEntry<String>> candlesCache =
            new ConcurrentHashMap<>();
    private volatile CacheEntry<String> symbolsCache;

    @GetMapping("/quote")
    public ResponseEntity<?> getStockQuote(@RequestParam String symbol) {
        String normalized = normalizeSymbol(symbol);
        if (normalized == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "올바른 종목 심볼을 입력해주세요."
            ));
        }

        CacheEntry<Map<?, ?>> cached = quoteCache.get(normalized);
        if (isFresh(cached)) {
            return ResponseEntity.ok(cached.value());
        }

        try {
            String url = "https://finnhub.io/api/v1/quote?symbol="
                    + normalized
                    + "&token="
                    + finnhubToken;

            ResponseEntity<Map> response =
                    restTemplate.getForEntity(url, Map.class);
            Map<?, ?> body = response.getBody();
            if (body != null) {
                putBounded(
                        quoteCache,
                        normalized,
                        new CacheEntry<>(
                                body,
                                System.currentTimeMillis() + QUOTE_CACHE_MS
                        ),
                        MAX_QUOTE_CACHE_ENTRIES
                );
            }
            return ResponseEntity.ok(body);
        } catch (HttpClientErrorException.TooManyRequests e) {
            log.warn("Finnhub quote rate limit reached for {}", normalized);
            if (isUsableStale(cached, QUOTE_STALE_MS)) {
                return ResponseEntity.ok(cached.value());
            }
            return ResponseEntity.status(429).body(Map.of(
                    "message", "시세 제공사 호출 한도를 초과했습니다. 잠시 후 다시 시도해주세요."
            ));
        } catch (Exception e) {
            log.warn(
                    "Finnhub quote request failed for {}: {}",
                    normalized,
                    e.getClass().getSimpleName()
            );
            if (isUsableStale(cached, QUOTE_STALE_MS)) {
                return ResponseEntity.ok(cached.value());
            }
            return ResponseEntity.status(502).body(Map.of(
                    "message", "주식 데이터를 일시적으로 불러오지 못했습니다."
            ));
        }
    }

    @GetMapping("/candles")
    public ResponseEntity<?> getStockCandles(
            @RequestParam(defaultValue = "AAPL") String symbol,
            @RequestParam(defaultValue = "D") String resolution) {
        String normalized = normalizeSymbol(symbol);
        if (normalized == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "올바른 종목 심볼을 입력해주세요."
            ));
        }

        String normalizedResolution =
                resolution == null
                        ? "D"
                        : resolution.trim().toUpperCase(Locale.ROOT);

        String candlesCacheKey =
                normalized + ":" + normalizedResolution;
        CacheEntry<String> cached =
                candlesCache.get(candlesCacheKey);
        if (isFresh(cached)) {
            return ResponseEntity.ok(cached.value());
        }

        String interval;
        String range;
        switch (normalizedResolution) {
            case "D" -> {
                interval = "1d";
                range = "6mo";
            }
            case "W" -> {
                interval = "1wk";
                range = "2y";
            }
            case "M" -> {
                interval = "1mo";
                range = "5y";
            }
            default -> {
                return ResponseEntity.badRequest().body(Map.of(
                        "message", "resolution은 D, W, M 중 하나여야 합니다."
                ));
            }
        }

        try {
            String url =
                    "https://query1.finance.yahoo.com/v8/finance/chart/"
                            + normalized
                            + "?interval="
                            + interval
                            + "&range="
                            + range;
            HttpHeaders headers = new HttpHeaders();
            headers.set(
                    HttpHeaders.USER_AGENT,
                    "Mozilla/5.0 (compatible; TARDISStock/1.0)"
            );
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response =
                    restTemplate.exchange(
                            url,
                            HttpMethod.GET,
                            entity,
                            String.class
                    );

            String body = response.getBody();
            if (body != null && !body.isBlank()) {
                putBounded(
                        candlesCache,
                        candlesCacheKey,
                        new CacheEntry<>(
                                body,
                                System.currentTimeMillis()
                                        + CANDLES_CACHE_MS
                        ),
                        MAX_CANDLES_CACHE_ENTRIES
                );
            }
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            log.warn(
                    "Yahoo chart request failed for {}: {}",
                    normalized,
                    e.getClass().getSimpleName()
            );
            if (isUsableStale(cached, CANDLES_STALE_MS)) {
                return ResponseEntity.ok(cached.value());
            }
            return ResponseEntity.status(502).body(Map.of(
                    "message", "차트 데이터를 일시적으로 불러오지 못했습니다."
            ));
        }
    }

    @GetMapping("/symbols")
    public ResponseEntity<?> getAllSymbols() {
        CacheEntry<String> cached = symbolsCache;
        if (isFresh(cached)) {
            return ResponseEntity.ok(cached.value());
        }

        try {
            String url =
                    "https://finnhub.io/api/v1/stock/symbol"
                            + "?exchange=US&token="
                            + finnhubToken;
            ResponseEntity<String> response =
                    restTemplate.getForEntity(url, String.class);

            String body = response.getBody();
            if (body != null && !body.isBlank()) {
                symbolsCache = new CacheEntry<>(
                        body,
                        System.currentTimeMillis() + SYMBOL_CACHE_MS
                );
            }
            return ResponseEntity.ok(body);
        } catch (HttpClientErrorException.TooManyRequests e) {
            log.warn("Finnhub symbol-list rate limit reached");
            if (isUsableStale(cached, SYMBOL_STALE_MS)) {
                return ResponseEntity.ok(cached.value());
            }
            return ResponseEntity.status(429).body(Map.of(
                    "message", "종목 목록 제공사 호출 한도를 초과했습니다."
            ));
        } catch (Exception e) {
            log.warn(
                    "Finnhub symbol-list request failed: {}",
                    e.getClass().getSimpleName()
            );
            if (isUsableStale(cached, SYMBOL_STALE_MS)) {
                return ResponseEntity.ok(cached.value());
            }
            return ResponseEntity.status(502).body(Map.of(
                    "message", "전체 종목 목록을 일시적으로 불러오지 못했습니다."
            ));
        }
    }

    @GetMapping("/search")
    public ResponseEntity<?> searchStocks(
            @RequestParam String query) {
        String normalized = normalizeQuery(query);
        if (normalized == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message",
                    "검색어는 1~" + MAX_SEARCH_QUERY_LENGTH
                            + "자로 입력해주세요."
            ));
        }

        String cacheKey = normalized.toLowerCase(Locale.ROOT);
        CacheEntry<String> cached = searchCache.get(cacheKey);
        if (isFresh(cached)) {
            return ResponseEntity.ok(cached.value());
        }

        try {
            URI uri = UriComponentsBuilder
                    .fromUriString("https://finnhub.io/api/v1/search")
                    .queryParam("q", normalized)
                    .queryParam("token", finnhubToken)
                    .build()
                    .encode()
                    .toUri();

            ResponseEntity<String> response =
                    restTemplate.getForEntity(uri, String.class);

            String body = response.getBody();
            if (body != null && !body.isBlank()) {
                putBounded(
                        searchCache,
                        cacheKey,
                        new CacheEntry<>(
                                body,
                                System.currentTimeMillis() + SEARCH_CACHE_MS
                        ),
                        MAX_SEARCH_CACHE_ENTRIES
                );
            }
            return ResponseEntity.ok(body);
        } catch (HttpClientErrorException.TooManyRequests e) {
            log.warn("Finnhub search rate limit reached");
            if (isUsableStale(cached, SEARCH_STALE_MS)) {
                return ResponseEntity.ok(cached.value());
            }
            return ResponseEntity.status(429).body(Map.of(
                    "message",
                    "종목 검색 제공사 호출 한도를 초과했습니다. 잠시 후 다시 시도해주세요."
            ));
        } catch (Exception e) {
            log.warn(
                    "Finnhub search request failed: {}",
                    e.getClass().getSimpleName()
            );
            if (isUsableStale(cached, SEARCH_STALE_MS)) {
                return ResponseEntity.ok(cached.value());
            }
            return ResponseEntity.status(502).body(Map.of(
                    "message",
                    "종목 검색을 일시적으로 사용할 수 없습니다."
            ));
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

    private String normalizeQuery(String query) {
        if (query == null) return null;
        String normalized = query.trim();
        if (normalized.isEmpty()
                || normalized.length() > MAX_SEARCH_QUERY_LENGTH) {
            return null;
        }
        return normalized;
    }

    private boolean isFresh(CacheEntry<?> entry) {
        return entry != null
                && entry.expiresAt() > System.currentTimeMillis();
    }

    private boolean isUsableStale(
            CacheEntry<?> entry,
            long staleAllowanceMs) {
        return entry != null
                && entry.expiresAt() + staleAllowanceMs
                > System.currentTimeMillis();
    }

    private <T> void putBounded(
            ConcurrentHashMap<String, CacheEntry<T>> cache,
            String key,
            CacheEntry<T> value,
            int maxEntries) {
        if (cache.size() >= maxEntries) {
            long now = System.currentTimeMillis();
            cache.entrySet().removeIf(
                    entry -> entry.getValue().expiresAt() <= now
            );
            if (cache.size() >= maxEntries) {
                cache.clear();
            }
        }
        cache.put(key, value);
    }

    private record CacheEntry<T>(
            T value,
            long expiresAt) {}
}
