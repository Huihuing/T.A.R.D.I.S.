package com.tardistock.backend.controller;

import com.tardistock.backend.config.ExternalApiHttpClient;
import com.tardistock.backend.util.BoundedCacheSupport;
import com.tardistock.backend.util.SingleFlightSupport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/news")
public class NewsController {

    private static final Logger log =
            LoggerFactory.getLogger(NewsController.class);
    private static final Pattern SYMBOL_PATTERN =
            Pattern.compile("^[A-Z0-9.-]{1,15}$");
    private static final int MAX_QUERY_LENGTH = 120;
    private static final long NEWS_CACHE_MS = 5 * 60 * 1000L;
    private static final long NEWS_STALE_MS = 60 * 60 * 1000L;
    private static final int MAX_NEWS_CACHE_ENTRIES = 50;

    @Value("${naver.api.client-id}")
    private String naverClientId;

    @Value("${naver.api.client-secret}")
    private String naverClientSecret;

    @Value("${finnhub.api.key}")
    private String finnhubToken;

    private final RestTemplate restTemplate;
    private final ConcurrentHashMap<String, CacheEntry> globalCache =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CacheEntry> koreaCache =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CompletableFuture<ResponseEntity<?>>> inFlight =
            new ConcurrentHashMap<>();

    public NewsController() {
        this(ExternalApiHttpClient.create());
    }

    NewsController(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @GetMapping("/global")
    public ResponseEntity<?> getGlobalNews(
            @RequestParam(defaultValue = "AAPL") String symbol) {
        String normalized = normalizeSymbol(symbol);
        if (normalized == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "올바른 종목 심볼을 입력해주세요."
            ));
        }

        CacheEntry cached = globalCache.get(normalized);
        if (isFresh(cached)) {
            return ResponseEntity.ok(cached.value());
        }

        return SingleFlightSupport.execute(
                inFlight,
                "global:" + normalized,
                () -> loadGlobalNews(normalized)
        );
    }

    private ResponseEntity<?> loadGlobalNews(String normalized) {
        CacheEntry cached = globalCache.get(normalized);
        if (isFresh(cached)) {
            return ResponseEntity.ok(cached.value());
        }

        try {
            LocalDate today = LocalDate.now();
            LocalDate threeDaysAgo = today.minusDays(3);
            DateTimeFormatter formatter =
                    DateTimeFormatter.ISO_LOCAL_DATE;

            String url =
                    "https://finnhub.io/api/v1/company-news?symbol="
                            + normalized
                            + "&from="
                            + threeDaysAgo.format(formatter)
                            + "&to="
                            + today.format(formatter)
                            + "&token="
                            + finnhubToken;
            ResponseEntity<String> response =
                    restTemplate.getForEntity(url, String.class);

            String body = response.getBody();
            if (body != null && !body.isBlank()) {
                BoundedCacheSupport.put(
                        globalCache,
                        normalized,
                        new CacheEntry(
                                body,
                                System.currentTimeMillis() + NEWS_CACHE_MS
                        ),
                        MAX_NEWS_CACHE_ENTRIES,
                        CacheEntry::expiresAt
                );
            }
            return ResponseEntity.ok(body);
        } catch (HttpClientErrorException.TooManyRequests e) {
            log.warn(
                    "Finnhub news rate limit reached for {}",
                    normalized
            );
            if (isUsableStale(cached)) {
                return ResponseEntity.ok(cached.value());
            }
            return ResponseEntity.status(429).body(Map.of(
                    "message", "뉴스 제공사 호출 한도를 초과했습니다. 잠시 후 다시 시도해주세요."
            ));
        } catch (Exception e) {
            log.warn(
                    "Finnhub news request failed for {}: {}",
                    normalized,
                    e.getClass().getSimpleName()
            );
            if (isUsableStale(cached)) {
                return ResponseEntity.ok(cached.value());
            }
            return ResponseEntity.status(502).body(Map.of(
                    "message", "해외 뉴스를 일시적으로 불러오지 못했습니다."
            ));
        }
    }

    @GetMapping("/korea")
    public ResponseEntity<?> getKoreanNews(
            @RequestParam(
                    defaultValue = "증시 시황 특징주 -연예 -정치"
            ) String query) {
        String normalizedQuery = normalizeQuery(query);
        if (normalizedQuery == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "검색어는 1~120자로 입력해주세요."
            ));
        }

        String cacheKey =
                normalizedQuery.toLowerCase(Locale.ROOT);
        CacheEntry cached = koreaCache.get(cacheKey);
        if (isFresh(cached)) {
            return ResponseEntity.ok(cached.value());
        }

        return SingleFlightSupport.execute(
                inFlight,
                "korea:" + cacheKey,
                () -> loadKoreanNews(normalizedQuery, cacheKey)
        );
    }

    private ResponseEntity<?> loadKoreanNews(
            String normalizedQuery,
            String cacheKey) {
        CacheEntry cached = koreaCache.get(cacheKey);
        if (isFresh(cached)) {
            return ResponseEntity.ok(cached.value());
        }

        try {
            String url =
                    "https://naverapihub.apigw.ntruss.com/search/v1/news"
                            + "?query={query}&display=100&sort=date";
            HttpHeaders headers = new HttpHeaders();
            headers.set(
                    "X-NCP-APIGW-API-KEY-ID",
                    naverClientId
            );
            headers.set(
                    "X-NCP-APIGW-API-KEY",
                    naverClientSecret
            );

            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response =
                    restTemplate.exchange(
                            url,
                            HttpMethod.GET,
                            entity,
                            String.class,
                            normalizedQuery
                    );

            String body = response.getBody();
            if (body != null && !body.isBlank()) {
                BoundedCacheSupport.put(
                        koreaCache,
                        cacheKey,
                        new CacheEntry(
                                body,
                                System.currentTimeMillis() + NEWS_CACHE_MS
                        ),
                        MAX_NEWS_CACHE_ENTRIES,
                        CacheEntry::expiresAt
                );
            }
            return ResponseEntity.ok(body);
        } catch (HttpClientErrorException.TooManyRequests e) {
            log.warn("Naver news rate limit reached");
            if (isUsableStale(cached)) {
                return ResponseEntity.ok(cached.value());
            }
            return ResponseEntity.status(429).body(Map.of(
                    "message", "뉴스 제공사 호출 한도를 초과했습니다. 잠시 후 다시 시도해주세요."
            ));
        } catch (Exception e) {
            log.warn(
                    "Naver news request failed: {}",
                    e.getClass().getSimpleName()
            );
            if (isUsableStale(cached)) {
                return ResponseEntity.ok(cached.value());
            }
            return ResponseEntity.status(502).body(Map.of(
                    "message", "국내 뉴스를 일시적으로 불러오지 못했습니다."
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
                || normalized.length() > MAX_QUERY_LENGTH) {
            return null;
        }
        return normalized;
    }

    private boolean isFresh(CacheEntry entry) {
        return entry != null
                && entry.expiresAt() > System.currentTimeMillis();
    }

    private boolean isUsableStale(CacheEntry entry) {
        return entry != null
                && entry.expiresAt() + NEWS_STALE_MS
                > System.currentTimeMillis();
    }

    int inFlightSizeForTest() {
        return inFlight.size();
    }

    private record CacheEntry(
            String value,
            long expiresAt) {}
}
