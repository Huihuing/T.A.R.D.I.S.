package com.tardistock.backend.controller;

import com.tardistock.backend.entity.CommunityReport;
import com.tardistock.backend.entity.Post;
import com.tardistock.backend.repository.CommentRepository;
import com.tardistock.backend.repository.CommunityReportRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommunityReportControllerTest {

    @Mock
    private CommunityReportRepository reportRepository;
    @Mock
    private PostRepository postRepository;
    @Mock
    private CommentRepository commentRepository;

    private CommunityReportController controller;

    @BeforeEach
    void setUp() {
        controller = new CommunityReportController(
                reportRepository,
                postRepository,
                commentRepository
        );
    }

    @Test
    void rejectsInvalidTarget() {
        ResponseEntity<?> response = controller.createReport(
                Map.of(
                        "targetType", "UNKNOWN",
                        "targetId", 1,
                        "reason", "SPAM"
                ),
                new MockHttpServletRequest(),
                null
        );

        assertEquals(400, response.getStatusCode().value());
        verifyNoInteractions(reportRepository);
    }

    @Test
    void rejectsMissingPost() {
        when(postRepository.findById(77L)).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.createReport(
                Map.of(
                        "targetType", "POST",
                        "targetId", 77,
                        "reason", "SPAM"
                ),
                new MockHttpServletRequest(),
                null
        );

        assertEquals(404, response.getStatusCode().value());
        verify(reportRepository, never()).save(any());
    }

    @Test
    void createsGuestPostReportWithMaskedIp() {
        Post post = new Post(
                "198.51.*.*",
                "작성손님",
                "encoded-password",
                "테스트 글",
                "테스트 본문"
        );
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));

        CommunityReport saved = mock(CommunityReport.class);
        when(saved.getId()).thenReturn(42L);
        when(reportRepository.save(any(CommunityReport.class)))
                .thenReturn(saved);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "203.0.113.44, 10.0.0.1");

        ResponseEntity<?> response = controller.createReport(
                Map.of(
                        "targetType", "POST",
                        "targetId", 1,
                        "reason", "SPAM",
                        "detail", "반복 게시물입니다."
                ),
                request,
                null
        );

        assertEquals(200, response.getStatusCode().value());

        ArgumentCaptor<CommunityReport> captor =
                ArgumentCaptor.forClass(CommunityReport.class);
        verify(reportRepository).save(captor.capture());

        CommunityReport report = captor.getValue();
        assertEquals("POST", report.getTargetType());
        assertEquals(1L, report.getTargetId());
        assertEquals("SPAM", report.getReason());
        assertEquals("203.0.*.*", report.getReporterIp());
        assertNull(report.getReporterUsername());
        assertEquals("작성손님", report.getTargetAuthor());
        assertTrue(report.getTargetPreview().contains("테스트 글"));
    }
    @Test
    void rejectsDuplicateAuthenticatedReport() {
        Post post = new Post(
                "198.51.*.*",
                "작성자",
                "encoded-password",
                "테스트 글",
                "테스트 본문"
        );
        when(postRepository.findById(5L)).thenReturn(Optional.of(post));
        when(reportRepository
                .existsByTargetTypeAndTargetIdAndStatusAndReporterUsername(
                        "POST",
                        5L,
                        "OPEN",
                        "alice"
                ))
                .thenReturn(true);

        var authentication =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        "alice",
                        null,
                        java.util.List.of()
                );

        ResponseEntity<?> response = controller.createReport(
                Map.of(
                        "targetType", "POST",
                        "targetId", 5,
                        "reason", "SPAM"
                ),
                new MockHttpServletRequest(),
                authentication
        );

        assertEquals(409, response.getStatusCode().value());
        verify(reportRepository, never()).save(any());
    }

}
