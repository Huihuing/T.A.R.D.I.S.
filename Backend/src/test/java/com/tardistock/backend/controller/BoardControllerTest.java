package com.tardistock.backend.controller;

import com.tardistock.backend.entity.Comment;
import com.tardistock.backend.entity.Post;
import com.tardistock.backend.repository.CommentRepository;
import com.tardistock.backend.repository.MemberRepository;
import com.tardistock.backend.repository.PostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

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

    private BoardController controller;

    @BeforeEach
    void setUp() {
        controller = new BoardController(
                postRepository,
                commentRepository,
                memberRepository
        );
    }

    @Test
    void guestCanCreatePostWithMaskedIp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "203.0.113.44");

        ResponseEntity<?> response = controller.createPost(
                Map.of("title", "게스트 글", "content", "본문"),
                request,
                null
        );

        assertEquals(200, response.getStatusCode().value());

        ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(captor.capture());
        Post saved = captor.getValue();

        assertNull(saved.getMember());
        assertEquals("203.0.*.*", saved.getGuestIp());
        assertEquals("게스트 글", saved.getTitle());
    }

    @Test
    void guestCanCreateComment() {
        Post post = new Post("203.0.*.*", "제목", "본문");
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "198.51.100.23");

        ResponseEntity<?> response = controller.createComment(
                Map.of("postId", 1L, "content", "게스트 댓글"),
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
        assertEquals("게스트 댓글", saved.getContent());
    }

    @Test
    void rejectsOversizedGuestPost() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        String tooLongTitle = "x".repeat(121);

        ResponseEntity<?> response = controller.createPost(
                Map.of("title", tooLongTitle, "content", "본문"),
                request,
                null
        );

        assertEquals(400, response.getStatusCode().value());
        verify(postRepository, never()).save(any());
    }
}
