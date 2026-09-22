package com.tardistock.backend.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/stock")
public class StockController {

    private static final Logger log =
            LoggerFactory.getLogger(StockController.class);
    private static final Pattern SYMBOL_PATTERN =
            Pattern.compile("^[A-Z0-9.-]{1,15}$");

    @Value("${finnhub.api.key}")
    private String finnhubToken;

    private final RestTemplate restTemplate =
            new RestTemplateBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .readTimeout(Duration.ofSeconds(10))
                    .build();

    @GetMapping("/quote")
    public ResponseEntity<?> getStockQuote(@RequestParam String symbol) {
        String normalized = normalizeSymbol(symbol);
        if (normalized == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "올바른 종목 심볼을 입력해주세요."
            ));
        }

        try {
            String url = "https://finnhub.io/api/v1/quote?symbol="
                    + normalized
                    + "&token="
                    + finnhubToken;

            ResponseEntity<Map> response =
                    restTemplate.getForEntity(url, Map.class);
            return ResponseEntity.ok(response.getBody());
        } catch (HttpClientErrorException.TooManyRequests e) {
            log.warn("Finnhub quote rate limit reached for {}", normalized);
            return ResponseEntity.status(429).body(Map.of(
                    "message", "시세 제공사 호출 한도를 초과했습니다. 잠시 후 다시 시도해주세요."
            ));
        } catch (Exception e) {
            log.warn(
                    "Finnhub quote request failed for {}: {}",
                    normalized,
                    e.getClass().getSimpleName()
            );
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

            return ResponseEntity.ok(response.getBody());
        } catch (Exception e) {
            log.warn(
                    "Yahoo chart request failed for {}: {}",
                    normalized,
                    e.getClass().getSimpleName()
            );
            return ResponseEntity.status(502).body(Map.of(
                    "message", "차트 데이터를 일시적으로 불러오지 못했습니다."
            ));
        }
    }

    @GetMapping("/symbols")
    public ResponseEntity<?> getAllSymbols() {
        try {
            String url =
                    "https://finnhub.io/api/v1/stock/symbol"
                            + "?exchange=US&token="
                            + finnhubToken;
            ResponseEntity<String> response =
                    restTemplate.getForEntity(url, String.class);

            return ResponseEntity.ok(response.getBody());
        } catch (HttpClientErrorException.TooManyRequests e) {
            log.warn("Finnhub symbol-list rate limit reached");
            return ResponseEntity.status(429).body(Map.of(
                    "message", "종목 목록 제공사 호출 한도를 초과했습니다."
            ));
        } catch (Exception e) {
            log.warn(
                    "Finnhub symbol-list request failed: {}",
                    e.getClass().getSimpleName()
            );
            return ResponseEntity.status(502).body(Map.of(
                    "message", "전체 종목 목록을 일시적으로 불러오지 못했습니다."
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
}
