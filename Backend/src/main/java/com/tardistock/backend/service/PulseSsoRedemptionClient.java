package com.tardistock.backend.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Server-to-server only: redeem a one-use central PULSE ticket for an
 * existing legacy account ID. Never trust client-provided legacy IDs.
 */
@Component
public final class PulseSsoRedemptionClient {
    private static final Pattern TICKET = Pattern.compile("^[A-Za-z0-9_-]{43}$");
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final URI endpoint;
    private final String serviceKey;

    public PulseSsoRedemptionClient(
            @Value("${PULSE_SSO_REDEEM_URL:}") String redeemUrl,
            @Value("${PULSE_SSO_SERVICE_KEY:}") String secret,
            @Value("${PULSE_SSO_ALLOW_LOCAL_HTTP:false}") boolean allowLocalHttp) {
        this.serviceKey = secret == null ? "" : secret.trim();
        URI parsed = null;
        try {
            if (redeemUrl != null && !redeemUrl.isBlank()) {
                URI value = URI.create(redeemUrl);
                String host = value.getHost() == null ? "" : value.getHost().toLowerCase(Locale.ROOT);
                boolean secure = "https".equalsIgnoreCase(value.getScheme());
                boolean loopback = allowLocalHttp && "http".equalsIgnoreCase(value.getScheme())
                        && (host.equals("localhost") || host.equals("127.0.0.1")
                                || host.equals("::1") || host.equals("[::1]"));
                if ((secure || loopback) && !host.isBlank() && value.getUserInfo() == null
                        && value.getRawQuery() == null && value.getRawFragment() == null
                        && "/api/pulse-id/sso/redeem".equals(value.getPath())) {
                    parsed = value;
                }
            }
        } catch (IllegalArgumentException ignored) {
            // Misconfigured endpoint leaves SSO disabled.
        }
        this.endpoint = parsed;
    }

    public boolean isConfigured() {
        return endpoint != null &&
                serviceKey.getBytes(StandardCharsets.UTF_8).length >= 32 &&
                !serviceKey.startsWith("REPLACE_WITH_");
    }

    public Optional<String> redeem(String world, String ticket) {
        if (!isConfigured()) throw new IllegalStateException("PULSE_SSO_DISABLED");
        if (!TICKET.matcher(ticket == null ? "" : ticket).matches()) return Optional.empty();
        // Ticket is strictly base64url; JSON string interpolation cannot inject fields.
        String body = "{\"world\":\"" + world + "\",\"ticket\":\"" + ticket + "\"}";
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(8))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + serviceKey)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<String> response = http.send(
                    request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == 401 || response.statusCode() == 403)
                return Optional.empty();
            if (response.statusCode() != 200 || response.body().length() > 2048)
                throw new IllegalStateException("PULSE_SSO_UPSTREAM_UNAVAILABLE");
            JsonNode data = mapper.readTree(response.body());
            if (!world.equals(data.path("world").asText())) return Optional.empty();
            String legacyId = data.path("legacyUserId").asText("");
            return legacyId.isBlank() ? Optional.empty() : Optional.of(legacyId);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("PULSE_SSO_UPSTREAM_UNAVAILABLE");
        } catch (Exception exception) {
            throw new IllegalStateException("PULSE_SSO_UPSTREAM_UNAVAILABLE");
        }
    }
}
