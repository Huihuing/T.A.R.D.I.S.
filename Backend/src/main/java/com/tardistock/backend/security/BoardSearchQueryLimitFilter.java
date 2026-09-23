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

@Component
public class BoardSearchQueryLimitFilter extends OncePerRequestFilter {

    static final int MAX_SEARCH_QUERY_LENGTH = 100;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        if (!isBoardSearchRequest(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String query = request.getParameter("q");
        if (query != null
                && query.strip().length() > MAX_SEARCH_QUERY_LENGTH) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(
                    "{\"message\":\"검색어는 100자 이하로 입력해주세요.\"}"
            );
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isBoardSearchRequest(HttpServletRequest request) {
        return "GET".equals(request.getMethod())
                && "/api/board/posts".equals(request.getRequestURI());
    }
}
