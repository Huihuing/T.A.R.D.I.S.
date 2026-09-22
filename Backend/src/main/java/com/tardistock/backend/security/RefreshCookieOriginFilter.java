package com.tardistock.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class RefreshCookieOriginFilter extends OncePerRequestFilter {

    private static final Set<String> COOKIE_MUTATION_PATHS = Set.of(
            "/api/auth/refresh",
            "/api/auth/logout",
            "/api/auth/sessions/revoke-others"
    );

    private final Set<String> allowedOrigins;

    public RefreshCookieOriginFilter(
            @Value("${app.frontend-url:https://tardis-neon.vercel.app}")
            String frontendUrl) {
        this.allowedOrigins = Set.of(
                normalize(frontendUrl),
                "http://localhost:5173",
                "http://127.0.0.1:5173"
        );
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equalsIgnoreCase(request.getMethod())
                || !COOKIE_MUTATION_PATHS.contains(
                        request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        String origin = normalize(request.getHeader("Origin"));

        // Non-browser clients may omit Origin. Browser cross-site requests
        // include it, so reject only an explicit untrusted Origin.
        if (!origin.isBlank() && !allowedOrigins.contains(origin)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(
                    "{\"message\":\"허용되지 않은 요청 출처입니다.\"}"
            );
            return;
        }

        filterChain.doFilter(request, response);
    }

    private static String normalize(String value) {
        if (value == null) return "";
        String normalized = value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(
                    0,
                    normalized.length() - 1
            );
        }
        return normalized;
    }
}
