package com.tardistock.backend.controller;

import com.tardistock.backend.entity.Bookmark;
import com.tardistock.backend.entity.Member;
import com.tardistock.backend.repository.BookmarkRepository;
import com.tardistock.backend.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookmarkControllerTest {

    @Mock
    private BookmarkRepository bookmarkRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private Authentication authentication;

    @Mock
    private Member member;

    private BookmarkController controller;

    @BeforeEach
    void setUp() {
        controller = new BookmarkController(bookmarkRepository, memberRepository);
        when(authentication.getName()).thenReturn("alice");
        when(memberRepository.findByUsernameForUpdate("alice"))
                .thenReturn(Optional.of(member));
    }

    @Test
    void rejectsMalformedPrice() {
        ResponseEntity<?> response = controller.toggleBookmark(
                Map.of("symbol", "AAPL", "price", "not-a-number"),
                authentication
        );

        assertEquals(400, response.getStatusCode().value());
        assertEquals("잘못된 가격입니다.", responseMessage(response));
        verify(bookmarkRepository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"NaN", "Infinity", "-Infinity"})
    void rejectsNonFinitePrice(String price) {
        ResponseEntity<?> response = controller.toggleBookmark(
                Map.of("symbol", "AAPL", "price", price),
                authentication
        );

        assertEquals(400, response.getStatusCode().value());
        assertEquals("잘못된 가격입니다.", responseMessage(response));
        verify(bookmarkRepository, never()).save(any());
    }

    @Test
    void rejectsNegativePrice() {
        ResponseEntity<?> response = controller.toggleBookmark(
                Map.of("symbol", "AAPL", "price", -0.01),
                authentication
        );

        assertEquals(400, response.getStatusCode().value());
        assertEquals("잘못된 가격입니다.", responseMessage(response));
        verify(bookmarkRepository, never()).save(any());
    }

    @Test
    void acceptsFiniteNonNegativePriceWithoutChangingApiContract() {
        when(bookmarkRepository.findByMemberAndSymbol(member, "AAPL"))
                .thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.toggleBookmark(
                Map.of("symbol", "aapl", "price", 123.456789),
                authentication
        );

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());

        ArgumentCaptor<Bookmark> bookmarkCaptor =
                ArgumentCaptor.forClass(Bookmark.class);
        verify(bookmarkRepository).save(bookmarkCaptor.capture());
        Bookmark saved = bookmarkCaptor.getValue();
        assertEquals("AAPL", saved.getSymbol());
        assertEquals(123.456789, saved.getPrice(), 0.0000001);
    }

    private String responseMessage(ResponseEntity<?> response) {
        Object body = response.getBody();
        if (!(body instanceof Map<?, ?> map)) {
            return null;
        }
        Object message = map.get("message");
        return message == null ? null : message.toString();
    }
}
