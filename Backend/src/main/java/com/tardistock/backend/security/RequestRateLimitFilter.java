package com.tardistock.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class RequestRateLimitFilter extends OncePerRequestFilter {

    private record Policy(int maxRequests, long windowSeconds) {}

    private static final Map<String, Policy> POLICIES = Map.ofEntries(
            Map.entry("POST:/api/auth/login", new Policy(10, 60)),
            Map.entry("POST:/api/auth/register", new Policy(5, 3600)),
            Map.entry("POST:/api/auth/google", new Policy(20, 60)),
            Map.entry("POST:/api/auth/refresh", new Policy(60, 60)),
            Map.entry("POST:/api/auth/sessions/revoke-others", new Policy(5, 600)),
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
            Map.entry("POST:/api/limit-orders", new Policy(30, 60)),
            Map.entry("POST:/api/security/csp-report", new Policy(30, 60))
    );

    private static final Policy STOCK_READ_POLICY = new Policy(120, 60);
    private static final Policy NEWS_READ_POLICY = new Policy(60, 60);
    private static final Policy PROFILE_READ_POLICY = new Policy(60, 60);
    private static final Policy LEADERBOARD_READ_POLICY = new Policy(20, 60);
    private static final Policy BOARD_READ_POLICY = new Policy(120, 60);
    private static final int MAX_COUNTERS = 20_000;
    private static final long MAX_COUNTER_AGE_SECONDS = 3_700;
    private static final long CLEANUP_EVERY_REQUESTS = 512;
    private static final int MAX_CLIENT_IP_LENGTH = 45;

    private final ConcurrentHashMap<String, WindowCounter> counters =
            new ConcurrentHashMap<>();
    private final AtomicLong rateLimitedRequestCount = new AtomicLong();

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
        String key = request.getMethod()
                + ":"
                + rateLimitBucket(request)
                + ":"
                + clientIp;
        long now = Instant.now().getEpochSecond();

        long requestNumber = rateLimitedRequestCount.incrementAndGet();
        if (requestNumber % CLEANUP_EVERY_REQUESTS == 0) {
            cleanupExpired(now);
        }

        if (!counters.containsKey(key) && counters.size() >= MAX_COUNTERS) {
            cleanupExpired(now);
            if (counters.size() >= MAX_COUNTERS) {
                writeRateLimitResponse(response, 60);
                return;
            }
        }

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

        if (current > policy.maxRequests()) {
            writeRateLimitResponse(response, retryAfter);
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
        if ("GET".equals(method) && uri.startsWith("/api/profile/")) {
            return PROFILE_READ_POLICY;
        }
        if ("GET".equals(method) && "/api/leaderboard".equals(uri)) {
            return LEADERBOARD_READ_POLICY;
        }
        if ("GET".equals(method)
                && ("/api/board".equals(uri)
                || uri.startsWith("/api/board/"))) {
            return BOARD_READ_POLICY;
        }
        return null;
    }

    private String rateLimitBucket(HttpServletRequest request) {
        String method = request.getMethod();
        String uri = request.getRequestURI();

        if ("GET".equals(method) && uri.startsWith("/api/stock/")) {
            return "/api/stock/*";
        }
        if ("GET".equals(method) && uri.startsWith("/api/news/")) {
            return "/api/news/*";
        }
        if ("GET".equals(method) && uri.startsWith("/api/profile/")) {
            return "/api/profile/*";
        }
        if ("GET".equals(method)
                && ("/api/board".equals(uri)
                || uri.startsWith("/api/board/"))) {
            return "/api/board/*";
        }
        return uri;
    }

    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            String firstHop = comma >= 0
                    ? forwarded.substring(0, comma)
                    : forwarded;
            String normalizedForwarded = normalizeIpLiteral(firstHop);
            if (!normalizedForwarded.isBlank()) {
                return normalizedForwarded;
            }
        }

        String normalizedRemote = normalizeIpLiteral(request.getRemoteAddr());
        return normalizedRemote.isBlank() ? "unknown" : normalizedRemote;
    }

    private String normalizeIpLiteral(String candidate) {
        if (candidate == null) return "";

        String value = candidate.trim();
        if (value.isEmpty() || value.length() > MAX_CLIENT_IP_LENGTH) {
            return "";
        }

        String ipv4 = normalizeIpv4(value);
        if (!ipv4.isBlank()) {
            return ipv4;
        }

        if (!looksLikeIpv6Literal(value)) {
            return "";
        }

        try {
            InetAddress address = InetAddress.getByName(value);
            return address instanceof Inet6Address
                    ? address.getHostAddress()
                    : "";
        } catch (Exception ignored) {
            return "";
        }
    }

    private String normalizeIpv4(String value) {
        String[] parts = value.split("\\.", -1);
        if (parts.length != 4) return "";

        int[] octets = new int[4];
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.isEmpty() || part.length() > 3) return "";
            if (part.length() > 1 && part.charAt(0) == '0') return "";

            int octet = 0;
            for (int j = 0; j < part.length(); j++) {
                char ch = part.charAt(j);
                if (ch < '0' || ch > '9') return "";
                octet = octet * 10 + (ch - '0');
            }
            if (octet > 255) return "";
            octets[i] = octet;
        }

        return octets[0] + "."
                + octets[1] + "."
                + octets[2] + "."
                + octets[3];
    }

    private boolean looksLikeIpv6Literal(String value) {
        if (!value.contains(":")) return false;

        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            boolean allowed = (ch >= '0' && ch <= '9')
                    || (ch >= 'a' && ch <= 'f')
                    || (ch >= 'A' && ch <= 'F')
                    || ch == ':'
                    || ch == '.';
            if (!allowed) return false;
        }
        return true;
    }

    private void cleanupExpired(long now) {
        counters.entrySet().removeIf(entry ->
                now - entry.getValue().windowStart
                        > MAX_COUNTER_AGE_SECONDS);
    }

    private void writeRateLimitResponse(
            HttpServletResponse response,
            long retryAfter) throws IOException {
        response.setStatus(429);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(
                "Retry-After",
                Long.toString(Math.max(1, retryAfter))
        );
        response.getWriter().write(
                "{\"message\":\"요청이 너무 많습니다. 잠시 후 다시 시도해주세요.\"}"
        );
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
