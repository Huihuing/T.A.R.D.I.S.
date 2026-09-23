package com.tardistock.backend.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BoardSearchQueryLimitFilterTest {

    private final BoardSearchQueryLimitFilter filter =
            new BoardSearchQueryLimitFilter();

    @Test
    void allowsSearchQueryAtLimit() throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/api/board/posts");
        request.addParameter("q", "a".repeat(100));
        MockHttpServletResponse response =
                new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {});

        assertEquals(200, response.getStatus());
    }

    @Test
    void rejectsSearchQueryOverLimit() throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/api/board/posts");
        request.addParameter("q", "a".repeat(101));
        MockHttpServletResponse response =
                new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {});

        assertEquals(400, response.getStatus());
        assertEquals(
                "{\"message\":\"검색어는 100자 이하로 입력해주세요.\"}",
                response.getContentAsString()
        );
    }

    @Test
    void ignoresOtherBoardGetRequests() throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/api/board/posts/1");
        request.addParameter("q", "a".repeat(101));
        MockHttpServletResponse response =
                new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {});

        assertEquals(200, response.getStatus());
    }

    @Test
    void trimsSearchQueryBeforeCheckingLength() throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/api/board/posts");
        request.addParameter("q", "  " + "a".repeat(100) + "  ");
        MockHttpServletResponse response =
                new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {});

        assertEquals(200, response.getStatus());
    }
}
