package com.tardistock.backend.security;

import com.tardistock.backend.entity.Member;
import com.tardistock.backend.repository.MemberRepository;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtAuthenticationFilterTest {

    private static final String SECRET =
            "filter-test-secret-key-that-is-at-least-thirty-two-bytes";

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validBearerTokenPopulatesSecurityContext() throws Exception {
        JwtTokenProvider provider = new JwtTokenProvider(SECRET, 60_000);
        MemberRepository members = mock(MemberRepository.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(provider, members);

        MockHttpServletRequest request = new MockHttpServletRequest();
        Member member = mock(Member.class);
        when(members.findByUsername("alice")).thenReturn(Optional.of(member));
        request.addHeader("Authorization", "Bearer " + provider.createToken("alice"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.doFilter(request, response, (req, res) -> {
            chainCalled.set(true);
            assertNotNull(SecurityContextHolder.getContext().getAuthentication());
            assertEquals("alice", SecurityContextHolder.getContext().getAuthentication().getName());
            assertTrue(SecurityContextHolder.getContext().getAuthentication().isAuthenticated());
        });

        assertTrue(chainCalled.get());
    }

    @Test
    void invalidBearerTokenDoesNotAuthenticateRequest() throws Exception {
        JwtTokenProvider provider = new JwtTokenProvider(SECRET, 60_000);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(provider, mock(MemberRepository.class));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer invalid.token.value");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.doFilter(request, response, (req, res) -> {
            chainCalled.set(true);
            assertNull(SecurityContextHolder.getContext().getAuthentication());
        });

        assertTrue(chainCalled.get());
    }

    @Test
    void requestWithoutBearerTokenRemainsAnonymousAtThisFilter() throws Exception {
        JwtTokenProvider provider = new JwtTokenProvider(SECRET, 60_000);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(provider, mock(MemberRepository.class));

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) ->
                assertNull(SecurityContextHolder.getContext().getAuthentication()));

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
    @Test
    void globalLogoutGenerationBlocksOldBearerJwtImmediately() throws Exception {
        JwtTokenProvider provider = new JwtTokenProvider(SECRET, 60_000);
        MemberRepository members = mock(MemberRepository.class);
        Member member = mock(Member.class);
        when(members.findByUsername("alice")).thenReturn(Optional.of(member));
        when(member.getSessionGeneration()).thenReturn(1L);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(provider, members);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + provider.createToken("alice", 0L));
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) ->
                assertNull(SecurityContextHolder.getContext().getAuthentication()));
        assertNull(SecurityContextHolder.getContext().getAuthentication());

        // Normal password login after revocation may mint the updated version.
        request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + provider.createToken("alice", 1L));
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) ->
                assertEquals("alice", SecurityContextHolder.getContext()
                        .getAuthentication().getName()));
    }

}
