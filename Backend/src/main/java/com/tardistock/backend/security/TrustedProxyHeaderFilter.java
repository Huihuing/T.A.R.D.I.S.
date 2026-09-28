package com.tardistock.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * Sanitizes X-Forwarded-For before downstream rate-limit/community code uses it.
 *
 * When TRUSTED_PROXY_SECRET is configured, Vercel can attach the matching
 * X-Tardis-Proxy-Secret header to external rewrite requests. Only those
 * requests may supply the Vercel-sanitized X-Forwarded-For value. Direct
 * Render traffic ignores caller-supplied X-Forwarded-For and falls back to
 * Render/Cloudflare's CF-Connecting-IP (or the socket peer as a last resort).
 *
 * With no configured secret the filter is intentionally inactive so existing
 * production traffic is not changed before both proxy and backend settings are
 * rolled out together.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TrustedProxyHeaderFilter extends OncePerRequestFilter {

    static final String FORWARDED_HEADER = "X-Forwarded-For";
    static final String CLOUDFLARE_IP_HEADER = "CF-Connecting-IP";
    static final String PROXY_SECRET_HEADER = "X-Tardis-Proxy-Secret";
    private static final int MAX_IP_LENGTH = 64;

    private final String trustedProxySecret;

    public TrustedProxyHeaderFilter(
            @Value("${security.trusted-proxy-secret:}")
            String trustedProxySecret) {
        this.trustedProxySecret = trustedProxySecret == null
                ? ""
                : trustedProxySecret.trim();
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (trustedProxySecret.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp;
        if (hasTrustedProxySecret(request)) {
            clientIp = firstForwardedIp(
                    request.getHeader(FORWARDED_HEADER));
        } else {
            clientIp = normalizeIpLiteral(
                    request.getHeader(CLOUDFLARE_IP_HEADER));
        }

        if (clientIp.isBlank()) {
            clientIp = normalizeIpLiteral(request.getRemoteAddr());
        }
        if (clientIp.isBlank()) {
            clientIp = "unknown";
        }

        String sanitizedClientIp = clientIp;
        HttpServletRequestWrapper sanitizedRequest =
                new HttpServletRequestWrapper(request) {
                    @Override
                    public String getHeader(String name) {
                        if (FORWARDED_HEADER.equalsIgnoreCase(name)) {
                            return sanitizedClientIp;
                        }
                        return super.getHeader(name);
                    }

                    @Override
                    public Enumeration<String> getHeaders(String name) {
                        if (FORWARDED_HEADER.equalsIgnoreCase(name)) {
                            return Collections.enumeration(
                                    List.of(sanitizedClientIp));
                        }
                        return super.getHeaders(name);
                    }
                };

        filterChain.doFilter(sanitizedRequest, response);
    }

    private boolean hasTrustedProxySecret(HttpServletRequest request) {
        String supplied = request.getHeader(PROXY_SECRET_HEADER);
        if (supplied == null || supplied.isBlank()) {
            return false;
        }

        return MessageDigest.isEqual(
                trustedProxySecret.getBytes(StandardCharsets.UTF_8),
                supplied.getBytes(StandardCharsets.UTF_8)
        );
    }

    private String firstForwardedIp(String forwarded) {
        if (forwarded == null || forwarded.isBlank()) {
            return "";
        }
        int comma = forwarded.indexOf(',');
        String first = comma >= 0
                ? forwarded.substring(0, comma)
                : forwarded;
        return normalizeIpLiteral(first);
    }

    private String normalizeIpLiteral(String candidate) {
        if (candidate == null) return "";

        String value = candidate.trim();
        if (value.isEmpty() || value.length() > MAX_IP_LENGTH) {
            return "";
        }

        if (!value.contains(":")) {
            return normalizeIpv4(value);
        }

        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            boolean allowed = (ch >= '0' && ch <= '9')
                    || (ch >= 'a' && ch <= 'f')
                    || (ch >= 'A' && ch <= 'F')
                    || ch == ':'
                    || ch == '.';
            if (!allowed) return "";
        }

        try {
            return InetAddress.getByName(value).getHostAddress();
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
}
