package com.tardistock.backend.config;

import com.tardistock.backend.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.security.Principal;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WebSocketConfigTest {
    private JwtTokenProvider tokens;
    private ChannelInterceptor interceptor;
    private final MessageChannel channel = mock(MessageChannel.class);

    @BeforeEach
    void setUp() {
        tokens = mock(JwtTokenProvider.class);
        ChannelRegistration registration = mock(ChannelRegistration.class);
        new WebSocketConfig("https://tardis-neon.vercel.app", tokens)
                .configureClientInboundChannel(registration);
        ArgumentCaptor<ChannelInterceptor> capture =
                ArgumentCaptor.forClass(ChannelInterceptor.class);
        verify(registration).interceptors(capture.capture());
        interceptor = capture.getValue();
    }

    @Test
    void connectBindsPrincipalToTransportAndReturnedFrame() {
        when(tokens.validateToken("valid-token")).thenReturn(true);
        when(tokens.getUsername("valid-token")).thenReturn("alice");
        StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.CONNECT);
        headers.setNativeHeader("Authorization", "Bearer valid-token");
        AtomicReference<Principal> transportUser = new AtomicReference<>();
        headers.setUserChangeCallback(transportUser::set);
        headers.setLeaveMutable(true);
        Message<byte[]> frame = MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());

        Message<?> authenticated = interceptor.preSend(frame, channel);

        assertNotNull(transportUser.get(), "CONNECT must update the transport session user");
        assertEquals("alice", transportUser.get().getName());
        assertEquals("alice", MessageHeaderAccessor.getAccessor(
                authenticated, StompHeaderAccessor.class).getUser().getName());
    }

    @Test
    void authenticatedUserCanSubscribeOnlyToOwnAlerts() {
        assertDoesNotThrow(() -> send(StompCommand.SUBSCRIBE, "/topic/alerts/alice", "alice"));
        assertThrows(MessagingException.class,
                () -> send(StompCommand.SUBSCRIBE, "/topic/alerts/bob", "alice"));
        assertThrows(MessagingException.class,
                () -> send(StompCommand.SUBSCRIBE, "/topic/alerts/alice", null));
    }

    @Test
    void guestsCanUseChatButCannotSendToOtherDestinations() {
        assertDoesNotThrow(() -> send(StompCommand.SUBSCRIBE, "/topic/chat", null));
        assertDoesNotThrow(() -> send(StompCommand.SEND, "/app/chat", null));
        assertThrows(MessagingException.class,
                () -> send(StompCommand.SEND, "/topic/alerts/alice", null));
    }

    @Test
    void invalidConnectTokenIsRejected() {
        StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.CONNECT);
        headers.setNativeHeader("Authorization", "Bearer expired-token");
        Message<byte[]> frame = MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
        assertThrows(MessagingException.class, () -> interceptor.preSend(frame, channel));
    }

    private Message<?> send(StompCommand command, String destination, String username) {
        StompHeaderAccessor headers = StompHeaderAccessor.create(command);
        headers.setDestination(destination);
        if (username != null) headers.setUser(new UsernamePasswordAuthenticationToken(username, null, List.of()));
        return interceptor.preSend(
                MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders()), channel);
    }
}
