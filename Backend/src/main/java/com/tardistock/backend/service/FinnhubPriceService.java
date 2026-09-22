package com.tardistock.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Locale;
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

    @Value("${finnhub.api.key}")
    private String apiKey;

    private final RestTemplate restTemplate =
            new RestTemplateBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .readTimeout(Duration.ofSeconds(10))
                    .build();

    // 캐시: symbol -> CachedPrice(price, cachedAt)
    private final ConcurrentHashMap<String, CachedPrice> cache = new ConcurrentHashMap<>();
    private static final int CACHE_SECONDS = 60;

    /**
     * 종목 심볼의 현재가를 반환합니다.
     * 캐시가 유효하면 캐시 값을 반환하고, 만료되었으면 Finnhub API를 호출합니다.
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

        // 캐시가 유효한 경우 즉시 반환
        if (cached != null && cached.cachedAt.plusSeconds(CACHE_SECONDS).isAfter(LocalDateTime.now())) {
            return cached.price;
        }

        // Finnhub REST API 호출 (RestTemplate 사용)
        try {
            String url = "https://finnhub.io/api/v1/quote?symbol=" + normalized + "&token=" + apiKey;
            Map<?, ?> response = restTemplate.getForObject(url, Map.class);

            if (response != null && response.containsKey("c")) {
                Object cVal = response.get("c");
                double currentPrice = Double.parseDouble(cVal.toString());

                if (currentPrice > 0) {
                    cache.put(normalized, new CachedPrice(currentPrice, LocalDateTime.now()));
                    log.debug(
                            "Finnhub price refreshed for {}",
                            normalized
                    );
                    return currentPrice;
                }
            }

        } catch (Exception e) {
            log.warn(
                    "Finnhub price request failed for {}: {}",
                    normalized,
                    e.getClass().getSimpleName()
            );
        }

        // API 호출 실패 시 만료된 캐시라도 반환 (없으면 0.0)
        return cached != null ? cached.price : 0.0;
    }

    private String normalizeSymbol(String symbol) {
        if (symbol == null) return null;
        String normalized =
                symbol.trim().toUpperCase(Locale.ROOT);
        return SYMBOL_PATTERN.matcher(normalized).matches()
                ? normalized
                : null;
    }

    /** 캐시 항목 */
    private static class CachedPrice {
        final double price;
        final LocalDateTime cachedAt;

        CachedPrice(double price, LocalDateTime cachedAt) {
            this.price = price;
            this.cachedAt = cachedAt;
        }
    }
}
