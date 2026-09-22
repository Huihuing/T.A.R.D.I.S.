package com.tardistock.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RequestRateLimitFilter extends OncePerRequestFilter {

    private record Policy(int maxRequests, long windowSeconds) {}

    private static final Map<String, Policy> POLICIES = Map.ofEntries(
            Map.entry("POST:/api/auth/login", new Policy(10, 60)),
            Map.entry("POST:/api/auth/register", new Policy(5, 3600)),
            Map.entry("POST:/api/auth/google", new Policy(20, 60)),
            Map.entry("POST:/api/auth/refresh", new Policy(60, 60)),
            Map.entry("POST:/api/auth/email/send", new Policy(5, 600)),
            Map.entry("POST:/api/auth/email/verify", new Policy(20, 600)),
            Map.entry("POST:/api/auth/password/send", new Policy(5, 600)),
            Map.entry("POST:/api/auth/password/reset", new Policy(10, 600)),
            Map.entry("POST:/api/board/posts", new Policy(10, 60)),
            Map.entry("POST:/api/board/comments", new Policy(30, 60)),
            Map.entry("POST:/api/board/reports", new Policy(10, 60)),
            Map.entry("POST:/api/board/upload", new Policy(10, 60)),
            Map.entry("POST:/api/account/transfer", new Policy(30, 60)),
            Map.entry("POST:/api/account/password/change", new Policy(10, 600)),
            Map.entry("POST:/api/account/password/enable", new Policy(5, 600)),
            Map.entry("POST:/api/account/pin/change", new Policy(10, 600)),
            Map.entry("POST:/api/account/pin/reset", new Policy(5, 600)),
            Map.entry("POST:/api/account/security-code/send", new Policy(5, 600)),
            Map.entry("POST:/api/account/google/link", new Policy(10, 600)),
            Map.entry("DELETE:/api/account/google/link", new Policy(5, 600)),
            Map.entry("POST:/api/trade/buy", new Policy(60, 60)),
            Map.entry("POST:/api/trade/sell", new Policy(60, 60)),
            Map.entry("POST:/api/price-alerts", new Policy(20, 60)),
            Map.entry("POST:/api/limit-orders", new Policy(30, 60))
    );

    private static final Policy STOCK_READ_POLICY = new Policy(120, 60);
    private static final Policy NEWS_READ_POLICY = new Policy(60, 60);

    private final ConcurrentHashMap<String, WindowCounter> counters =
            new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        Policy policy = resolvePolicy(request);
        if (policy == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = getClientIp(request);
        String key = request.getMethod() + ":" + request.getRequestURI() + ":" + clientIp;
        long now = Instant.now().getEpochSecond();

        WindowCounter counter = counters.computeIfAbsent(
                key,
                ignored -> new WindowCounter(now, 0)
        );

        int current;
        long retryAfter;
        synchronized (counter) {
            if (now - counter.windowStart >= policy.windowSeconds()) {
                counter.windowStart = now;
                counter.count = 0;
            }

            counter.count++;
            current = counter.count;
            retryAfter = Math.max(
                    1,
                    policy.windowSeconds() - (now - counter.windowStart)
            );
        }

        if (counters.size() > 10_000) {
            cleanupExpired(now);
        }

        if (current > policy.maxRequests()) {
            response.setStatus(429);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("Retry-After", Long.toString(retryAfter));
            response.getWriter().write(
                    "{\"message\":\"요청이 너무 많습니다. 잠시 후 다시 시도해주세요.\"}"
            );
            return;
        }

        filterChain.doFilter(request, response);
    }

    private Policy resolvePolicy(HttpServletRequest request) {
        String method = request.getMethod();
        String uri = request.getRequestURI();

        Policy exact = POLICIES.get(method + ":" + uri);
        if (exact != null) return exact;

        if ("GET".equals(method) && uri.startsWith("/api/stock/")) {
            return STOCK_READ_POLICY;
        }
        if ("GET".equals(method) && uri.startsWith("/api/news/")) {
            return NEWS_READ_POLICY;
        }
        return null;
    }

    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String remote = request.getRemoteAddr();
        return remote == null || remote.isBlank() ? "unknown" : remote;
    }

    private void cleanupExpired(long now) {
        counters.entrySet().removeIf(entry ->
                now - entry.getValue().windowStart > 7200);
    }

    private static final class WindowCounter {
        private long windowStart;
        private int count;

        private WindowCounter(long windowStart, int count) {
            this.windowStart = windowStart;
            this.count = count;
        }
    }
}
