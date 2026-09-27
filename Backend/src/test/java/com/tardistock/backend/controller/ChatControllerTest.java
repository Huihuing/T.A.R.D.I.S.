package com.tardistock.backend.controller;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.MessagingException;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatControllerTest {

    @Test
    void guestMessageUsesServerAssignedGuestNameAndTrimsText() {
        ChatController controller = new ChatController(8, 10_000, 100);

        ChatController.ChatMessage message = controller.chat(
                new ChatController.ChatRequest(
                        "  hello world  ",
                        "client-message-1"
                ),
                null,
                "guest-session-a"
        );

        assertTrue(message.username().matches("Guest_\\d{4}"));
        assertEquals("hello world", message.text());
        assertEquals("client-message-1", message.clientMessageId());
        assertTrue(message.time().matches("\\d{2}:\\d{2}"));
    }

    @Test
    void authenticatedMessageAlwaysUsesPrincipalUsername() {
        ChatController controller = new ChatController(8, 10_000, 100);
        Principal principal = () -> "alice";

        ChatController.ChatMessage message = controller.chat(
                new ChatController.ChatRequest("hi", "message-2"),
                principal,
                "member-session"
        );

        assertEquals("alice", message.username());
    }

    @Test
    void rejectsBlankOversizedAndControlCharacterMessages() {
        ChatController controller = new ChatController(8, 10_000, 100);

        assertThrows(
                MessagingException.class,
                () -> controller.chat(
                        new ChatController.ChatRequest("   ", "blank"),
                        null,
                        "session-a"
                )
        );
        assertThrows(
                MessagingException.class,
                () -> controller.chat(
                        new ChatController.ChatRequest(
                                "x".repeat(301),
                                "too-long"
                        ),
                        null,
                        "session-b"
                )
        );
        assertThrows(
                MessagingException.class,
                () -> controller.chat(
                        new ChatController.ChatRequest(
                                "hello\nworld",
                                "control"
                        ),
                        null,
                        "session-c"
                )
        );
    }

    @Test
    void rateLimitsEachWebSocketSessionIndependently() {
        ChatController controller = new ChatController(2, 60_000, 100);

        controller.chat(
                new ChatController.ChatRequest("one", "1"),
                null,
                "session-a"
        );
        controller.chat(
                new ChatController.ChatRequest("two", "2"),
                null,
                "session-a"
        );

        assertThrows(
                MessagingException.class,
                () -> controller.chat(
                        new ChatController.ChatRequest("three", "3"),
                        null,
                        "session-a"
                )
        );

        ChatController.ChatMessage otherSession = controller.chat(
                new ChatController.ChatRequest("allowed", "4"),
                null,
                "session-b"
        );
        assertEquals("allowed", otherSession.text());
    }

    @Test
    void invalidClientMessageIdIsNotReflected() {
        ChatController controller = new ChatController(8, 10_000, 100);

        ChatController.ChatMessage message = controller.chat(
                new ChatController.ChatRequest("hello", "<script>"),
                null,
                "session-a"
        );

        assertEquals("", message.clientMessageId());
    }
}
