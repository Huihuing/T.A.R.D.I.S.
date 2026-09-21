package com.tardistock.backend.config;

import com.tardistock.backend.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.List;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig
        implements WebSocketMessageBrokerConfigurer {

    private final String frontendUrl;
    private final JwtTokenProvider jwtTokenProvider;

    public WebSocketConfig(
            @Value("${app.frontend-url:https://tardis-neon.vercel.app}")
            String frontendUrl,
            JwtTokenProvider jwtTokenProvider) {
        this.frontendUrl = frontendUrl;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    public void configureMessageBroker(
            MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(
            StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-stomp")
                .setAllowedOriginPatterns(
                        frontendUrl,
                        "http://localhost:5173",
                        "http://127.0.0.1:5173"
                )
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(
            ChannelRegistration registration) {
        registration.interceptors(
                new ChannelInterceptor() {
                    @Override
                    public Message<?> preSend(
                            Message<?> message,
                            MessageChannel channel) {
                        StompHeaderAccessor accessor =
                                StompHeaderAccessor.wrap(message);

                        if (StompCommand.CONNECT.equals(
                                accessor.getCommand())) {
                            authenticate(accessor);
                        }

                        if (StompCommand.SUBSCRIBE.equals(
                                accessor.getCommand())) {
                            authorizeSubscription(accessor);
                        }

                        return message;
                    }
                }
        );
    }

    private void authenticate(
            StompHeaderAccessor accessor) {
        String authorization =
                accessor.getFirstNativeHeader("Authorization");

        if (authorization == null
                || !authorization.startsWith("Bearer ")) {
            throw new MessagingException(
                    "WebSocket authentication required");
        }

        String token =
                authorization.substring(7).trim();
        if (!jwtTokenProvider.validateToken(token)) {
            throw new MessagingException(
                    "Invalid WebSocket token");
        }

        String username =
                jwtTokenProvider.getUsername(token);

        accessor.setUser(
                new UsernamePasswordAuthenticationToken(
                        username,
                        null,
                        List.of()
                )
        );
    }

    private void authorizeSubscription(
            StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null
                || !destination.startsWith(
                        "/topic/alerts/")) {
            return;
        }

        if (accessor.getUser() == null) {
            throw new MessagingException(
                    "WebSocket authentication required");
        }

        String targetUsername =
                destination.substring(
                        "/topic/alerts/".length());

        if (!targetUsername.equals(
                accessor.getUser().getName())) {
            throw new MessagingException(
                    "Cannot subscribe to another user's alerts");
        }
    }
}
