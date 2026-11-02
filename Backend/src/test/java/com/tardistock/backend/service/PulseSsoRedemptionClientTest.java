package com.tardistock.backend.service;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PulseSsoRedemptionClientTest {
    private static final String WORLD = "tardis";
    private static final String SERVICE_KEY = "I".repeat(48);
    private static final String TICKET = "t".repeat(43);

    @Test
    void onlyTrustedLocalEndpointCanRedeemWithServerAuthentication() throws Exception {
        var receivedKey = new AtomicReference<String>();
        var receivedBody = new AtomicReference<String>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/pulse-id/sso/redeem", exchange -> {
            receivedKey.set(exchange.getRequestHeaders().getFirst("Authorization"));
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = ("{\"world\":\"" + WORLD + "\",\"legacyUserId\":\"42\"}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            String endpoint = "http://127.0.0.1:" + server.getAddress().getPort()
                    + "/api/pulse-id/sso/redeem";
            var client = new PulseSsoRedemptionClient(endpoint, SERVICE_KEY, true);
            assertTrue(client.isConfigured());
            assertEquals(Optional.of("42"), client.redeem(WORLD, TICKET));
            assertEquals("Bearer " + SERVICE_KEY, receivedKey.get());
            assertTrue(receivedBody.get().contains("\"world\":\"" + WORLD + "\""));
            assertTrue(receivedBody.get().contains("\"ticket\":\"" + TICKET + "\""));
            assertTrue(client.redeem(WORLD, "invalid").isEmpty());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void badEndpointsAndKeysFailClosed() {
        var client = new PulseSsoRedemptionClient(
            "http://evil.example/api/pulse-id/sso/redeem", SERVICE_KEY, true);
        assertFalse(client.isConfigured());
        assertFalse(new PulseSsoRedemptionClient(
            "https://portal.example/other/path", SERVICE_KEY, false).isConfigured());
        assertFalse(new PulseSsoRedemptionClient(
            "https://portal.example/api/pulse-id/sso/redeem", "weak", false).isConfigured());
        assertTrue(new PulseSsoRedemptionClient(
            "https://portal.example/api/pulse-id/sso/redeem", SERVICE_KEY, false).isConfigured());
    }

    @Test
    void mismatchedWorldFromCentralServerMustBeRejected() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/pulse-id/sso/redeem", exchange -> {
            byte[] response = "{\"world\":\"other\",\"legacyUserId\":\"42\"}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            var client = new PulseSsoRedemptionClient(
                "http://127.0.0.1:" + server.getAddress().getPort()
                + "/api/pulse-id/sso/redeem", SERVICE_KEY, true);
            assertTrue(client.redeem(WORLD, TICKET).isEmpty());
        } finally {
            server.stop(0);
        }
    }
}
