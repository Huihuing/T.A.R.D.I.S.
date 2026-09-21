package com.tardistock.backend.controller;

import com.tardistock.backend.entity.Comment;
import com.tardistock.backend.entity.Post;
import com.tardistock.backend.repository.CommentRepository;
import com.tardistock.backend.repository.MemberRepository;
import com.tardistock.backend.repository.PostRepository;
import com.tardistock.backend.service.NotificationService;
import com.tardistock.backend.entity.Member;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BoardControllerTest {

    @Mock
    private PostRepository postRepository;
    @Mock
    private CommentRepository commentRepository;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private NotificationService notificationService;

    private BoardController controller;

    @BeforeEach
    void setUp() {
        controller = new BoardController(
                postRepository,
                commentRepository,
                memberRepository,
                passwordEncoder,
                notificationService
        );
    }

    @Test
    void returnsPagedSearchResults() {
        Post post = new Post("203.0.*.*", "검색손님", "encoded-pass", "테슬라 이야기", "본문");

        when(postRepository.search(eq("테슬라"), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(
                        java.util.List.of(post),
                        PageRequest.of(0, 10),
                        1
                ));

        ResponseEntity<?> response = controller.getPosts("테슬라", 0, 10);

        assertEquals(200, response.getStatusCode().value());
        assertInstanceOf(Map.class, response.getBody());

        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertEquals(1L, body.get("totalElements"));
        assertEquals(1, body.get("totalPages"));
        assertEquals("테슬라", body.get("query"));

        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> items =
                (java.util.List<Map<String, Object>>) body.get("items");
        assertEquals(1, items.size());
        assertEquals("테슬라 이야기", items.get(0).get("title"));
        assertEquals("검색손님", items.get(0).get("author"));
    }

    @Test
    void guestCanCreatePostWithMaskedIp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "203.0.113.44");

        when(passwordEncoder.encode("guest-pass")).thenReturn("encoded-guest-pass");

        ResponseEntity<?> response = controller.createPost(
                Map.of(
                        "title", "게스트 글",
                        "content", "본문",
                        "guestNickname", "손님",
                        "guestPassword", "guest-pass"
                ),
                request,
                null
        );

        assertEquals(200, response.getStatusCode().value());

        ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(captor.capture());
        Post saved = captor.getValue();

        assertNull(saved.getMember());
        assertEquals("203.0.*.*", saved.getGuestIp());
        assertEquals("손님", saved.getGuestNickname());
        assertEquals("encoded-guest-pass", saved.getGuestPasswordHash());
        assertEquals("게스트 글", saved.getTitle());
    }

    @Test
    void guestCanCreateComment() {
        Post post = new Post("203.0.*.*", "제목", "본문");
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "198.51.100.23");

        when(passwordEncoder.encode("comment-pass")).thenReturn("encoded-comment-pass");

        ResponseEntity<?> response = controller.createComment(
                Map.of(
                        "postId", 1L,
                        "content", "게스트 댓글",
                        "guestNickname", "댓글손님",
                        "guestPassword", "comment-pass"
                ),
                request,
                null
        );

        assertEquals(200, response.getStatusCode().value());

        ArgumentCaptor<Comment> captor =
                ArgumentCaptor.forClass(Comment.class);
        verify(commentRepository).save(captor.capture());
        Comment saved = captor.getValue();

        assertNull(saved.getMember());
        assertEquals("198.51.*.*", saved.getGuestIp());
        assertEquals("댓글손님", saved.getGuestNickname());
        assertEquals("encoded-comment-pass", saved.getGuestPasswordHash());
        assertEquals("게스트 댓글", saved.getContent());
    }

    @Test
    void rejectsOversizedGuestPost() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        String tooLongTitle = "x".repeat(121);

        ResponseEntity<?> response = controller.createPost(
                Map.of(
                        "title", tooLongTitle,
                        "content", "본문",
                        "guestNickname", "손님",
                        "guestPassword", "guest-pass"
                ),
                request,
                null
        );

        assertEquals(400, response.getStatusCode().value());
        verify(postRepository, never()).save(any());
    }

    @Test
    void guestPostRequiresNicknameAndPassword() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        ResponseEntity<?> response = controller.createPost(
                Map.of("title", "제목", "content", "본문"),
                request,
                null
        );

        assertEquals(400, response.getStatusCode().value());
        verify(postRepository, never()).save(any());
    }

    @Test
    void guestCanUpdatePostWithCorrectPassword() {
        Post post = new Post(
                "203.0.*.*",
                "손님",
                "encoded-pass",
                "기존 제목",
                "기존 본문"
        );
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        when(passwordEncoder.matches("guest-pass", "encoded-pass"))
                .thenReturn(true);

        ResponseEntity<?> response = controller.updatePost(
                1L,
                Map.of(
                        "title", "수정 제목",
                        "content", "수정 본문",
                        "guestPassword", "guest-pass"
                ),
                null
        );

        assertEquals(200, response.getStatusCode().value());
        assertEquals("수정 제목", post.getTitle());
        verify(postRepository).save(post);
    }

    @Test
    void guestCannotDeletePostWithWrongPassword() {
        Post post = new Post(
                "203.0.*.*",
                "손님",
                "encoded-pass",
                "제목",
                "본문"
        );
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        when(passwordEncoder.matches("wrong", "encoded-pass"))
                .thenReturn(false);

        ResponseEntity<?> response = controller.deletePost(
                1L,
                Map.of("guestPassword", "wrong"),
                null
        );

        assertEquals(403, response.getStatusCode().value());
        verify(postRepository, never()).delete(any());
    }
    @Test
    void commentOnMemberPostCreatesNotificationForOwner() {
        Member owner = new Member(
                "owner",
                "encoded",
                "Owner",
                "owner@example.test",
                "encoded-pin"
        );
        Member commenter = new Member(
                "commenter",
                "encoded",
                "Commenter",
                "commenter@example.test",
                "encoded-pin"
        );
        Post post = new Post(owner, "제목", "본문");

        when(postRepository.findById(1L))
                .thenReturn(Optional.of(post));
        when(memberRepository.findByUsername("commenter"))
                .thenReturn(Optional.of(commenter));

        org.springframework.security.core.Authentication authentication =
                mock(org.springframework.security.core.Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("commenter");

        ResponseEntity<?> response = controller.createComment(
                Map.of(
                        "postId", 1L,
                        "content", "댓글"
                ),
                new MockHttpServletRequest(),
                authentication
        );

        assertEquals(200, response.getStatusCode().value());
        verify(notificationService).create(
                eq(owner),
                eq("COMMENT"),
                contains("commenter")
        );
    }

}
