package com.tardistock.backend.controller;

import org.springframework.messaging.MessagingException;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Controller
public class ChatController {

    public record ChatRequest(String text, String clientMessageId) {}

    public record ChatMessage(
            String username,
            String text,
            String time,
            String clientMessageId) {}

    private static final int MAX_TEXT_LENGTH = 300;
    private static final int MAX_CLIENT_MESSAGE_ID_LENGTH = 64;
    private static final int DEFAULT_MAX_MESSAGES_PER_WINDOW = 8;
    private static final long DEFAULT_WINDOW_MILLIS = 10_000L;
    private static final int DEFAULT_MAX_SESSIONS = 5_000;
    private static final long CLEANUP_EVERY_MESSAGES = 256L;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm");

    private final ConcurrentHashMap<String, WindowCounter> counters =
            new ConcurrentHashMap<>();
    private final AtomicLong messageCount = new AtomicLong();
    private final Object counterInsertionLock = new Object();
    private final int maxMessagesPerWindow;
    private final long windowMillis;
    private final int maxSessions;

    public ChatController() {
        this(
                DEFAULT_MAX_MESSAGES_PER_WINDOW,
                DEFAULT_WINDOW_MILLIS,
                DEFAULT_MAX_SESSIONS
        );
    }

    ChatController(
            int maxMessagesPerWindow,
            long windowMillis,
            int maxSessions) {
        if (maxMessagesPerWindow <= 0
                || windowMillis <= 0
                || maxSessions <= 0) {
            throw new IllegalArgumentException(
                    "Chat rate-limit settings must be positive"
            );
        }
        this.maxMessagesPerWindow = maxMessagesPerWindow;
        this.windowMillis = windowMillis;
        this.maxSessions = maxSessions;
    }

    @MessageMapping("/chat")
    @SendTo("/topic/chat")
    public ChatMessage chat(
            ChatRequest request,
            Principal principal,
            @Header("simpSessionId") String sessionId) {
        String text = normalizeText(request == null ? null : request.text());
        String normalizedSessionId = normalizeSessionId(sessionId);
        enforceRateLimit(normalizedSessionId, System.currentTimeMillis());

        String username = principal != null
                && principal.getName() != null
                && !principal.getName().isBlank()
                ? principal.getName()
                : guestUsername(normalizedSessionId);

        return new ChatMessage(
                username,
                text,
                LocalTime.now(KST).format(TIME_FORMAT),
                normalizeClientMessageId(
                        request == null ? null : request.clientMessageId()
                )
        );
    }

    private String normalizeText(String candidate) {
        if (candidate == null) {
            throw new MessagingException("Chat message is required");
        }

        String text = candidate.trim();
        if (text.isEmpty()) {
            throw new MessagingException("Chat message is required");
        }
        if (text.length() > MAX_TEXT_LENGTH) {
            throw new MessagingException("Chat message is too long");
        }
        for (int i = 0; i < text.length(); i++) {
            if (Character.isISOControl(text.charAt(i))) {
                throw new MessagingException(
                        "Chat message contains unsupported control characters"
                );
            }
        }
        return text;
    }

    private String normalizeSessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new MessagingException("WebSocket session is required");
        }
        return sessionId;
    }

    private String normalizeClientMessageId(String candidate) {
        if (candidate == null) return "";

        String value = candidate.trim();
        if (value.isEmpty()
                || value.length() > MAX_CLIENT_MESSAGE_ID_LENGTH) {
            return "";
        }

        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            boolean allowed = (ch >= 'a' && ch <= 'z')
                    || (ch >= 'A' && ch <= 'Z')
                    || (ch >= '0' && ch <= '9')
                    || ch == '-'
                    || ch == '_';
            if (!allowed) return "";
        }
        return value;
    }

    private String guestUsername(String sessionId) {
        int suffix = Math.floorMod(sessionId.hashCode(), 10_000);
        return "Guest_" + String.format("%04d", suffix);
    }

    private void enforceRateLimit(String sessionId, long now) {
        long currentMessage = messageCount.incrementAndGet();
        if (currentMessage % CLEANUP_EVERY_MESSAGES == 0) {
            cleanupExpired(now);
        }

        WindowCounter counter = getOrCreateCounter(sessionId, now);
        if (counter == null) {
            throw new MessagingException("Chat is temporarily busy");
        }

        synchronized (counter) {
            if (now - counter.windowStart >= windowMillis) {
                counter.windowStart = now;
                counter.count = 0;
            }

            counter.count++;
            if (counter.count > maxMessagesPerWindow) {
                throw new MessagingException(
                        "Too many chat messages. Please wait a moment"
                );
            }
        }
    }

    private WindowCounter getOrCreateCounter(String sessionId, long now) {
        WindowCounter existing = counters.get(sessionId);
        if (existing != null) return existing;

        synchronized (counterInsertionLock) {
            existing = counters.get(sessionId);
            if (existing != null) return existing;

            if (counters.size() >= maxSessions) {
                cleanupExpired(now);
                if (counters.size() >= maxSessions) {
                    return null;
                }
            }

            WindowCounter created = new WindowCounter(now);
            counters.put(sessionId, created);
            return created;
        }
    }

    private void cleanupExpired(long now) {
        counters.entrySet().removeIf(entry ->
                now - entry.getValue().windowStart >= windowMillis
        );
    }

    private static final class WindowCounter {
        private long windowStart;
        private int count;

        private WindowCounter(long windowStart) {
            this.windowStart = windowStart;
        }
    }
}
