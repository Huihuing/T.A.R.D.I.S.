package com.tardistock.backend.controller;

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
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/news")
public class NewsController {

    private static final Logger log =
            LoggerFactory.getLogger(NewsController.class);
    private static final Pattern SYMBOL_PATTERN =
            Pattern.compile("^[A-Z0-9.-]{1,15}$");
    private static final int MAX_QUERY_LENGTH = 120;

    @Value("${naver.api.client-id}")
    private String naverClientId;

    @Value("${naver.api.client-secret}")
    private String naverClientSecret;

    @Value("${finnhub.api.key}")
    private String finnhubToken;

    @GetMapping("/global")
    public ResponseEntity<?> getGlobalNews(
            @RequestParam(defaultValue = "AAPL") String symbol) {
        String normalized = normalizeSymbol(symbol);
        if (normalized == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "올바른 종목 심볼을 입력해주세요."
            ));
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

            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<String> response =
                    restTemplate.getForEntity(url, String.class);

            return ResponseEntity.ok(response.getBody());
        } catch (HttpClientErrorException.TooManyRequests e) {
            log.warn(
                    "Finnhub news rate limit reached for {}",
                    normalized
            );
            return ResponseEntity.status(429).body(Map.of(
                    "message", "뉴스 제공사 호출 한도를 초과했습니다. 잠시 후 다시 시도해주세요."
            ));
        } catch (Exception e) {
            log.warn(
                    "Finnhub news request failed for {}: {}",
                    normalized,
                    e.getClass().getSimpleName()
            );
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

        try {
            String url =
                    "https://naverapihub.apigw.ntruss.com/search/v1/news"
                            + "?query={query}&display=100&sort=date";

            RestTemplate restTemplate = new RestTemplate();
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

            return ResponseEntity.ok(response.getBody());
        } catch (HttpClientErrorException.TooManyRequests e) {
            log.warn("Naver news rate limit reached");
            return ResponseEntity.status(429).body(Map.of(
                    "message", "뉴스 제공사 호출 한도를 초과했습니다. 잠시 후 다시 시도해주세요."
            ));
        } catch (Exception e) {
            log.warn(
                    "Naver news request failed: {}",
                    e.getClass().getSimpleName()
            );
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
}
