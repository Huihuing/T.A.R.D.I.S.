package com.tardistock.backend.service;

import com.tardistock.backend.config.ExternalApiHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Instant;
import java.util.Map;

@Service
public class GoogleIdentityService {

    private final RestTemplate restTemplate =
            ExternalApiHttpClient.create();
    private final String clientId;

    public GoogleIdentityService(
            @Value("${google.client-id:}") String clientId) {
        this.clientId = clientId;
    }

    public GoogleIdentity verify(String credential) {
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalStateException(
                    "Google 로그인이 아직 구성되지 않았습니다.");
        }
        if (credential == null || credential.isBlank()
                || credential.length() > 10_000) {
            throw new IllegalArgumentException(
                    "Google 로그인 정보가 올바르지 않습니다.");
        }

        try {
            URI uri = UriComponentsBuilder
                    .fromUriString("https://oauth2.googleapis.com/tokeninfo")
                    .queryParam("id_token", credential)
                    .build()
                    .encode()
                    .toUri();

            @SuppressWarnings("unchecked")
            Map<String, Object> payload =
                    restTemplate.getForObject(uri, Map.class);

            return validatePayload(
                    payload,
                    Instant.now().getEpochSecond()
            );
        } catch (RestClientResponseException e) {
            throw invalidToken();
        } catch (ResourceAccessException e) {
            throw new IllegalStateException(
                    "Google 로그인 서비스를 일시적으로 사용할 수 없습니다."
            );
        }
    }

    GoogleIdentity validatePayload(
            Map<String, Object> payload,
            long nowEpochSecond) {
        if (payload == null) {
            throw invalidToken();
        }

        String audience = stringValue(payload.get("aud"));
        String issuer = stringValue(payload.get("iss"));
        String subject = stringValue(payload.get("sub"));
        String email = stringValue(payload.get("email"));
        String name = stringValue(payload.get("name"));
        String emailVerified =
                stringValue(payload.get("email_verified"));
        long expiresAt = parseLong(payload.get("exp"));

        if (!clientId.equals(audience)
                || !("accounts.google.com".equals(issuer)
                    || "https://accounts.google.com".equals(issuer))
                || subject == null || subject.isBlank()
                || email == null || email.isBlank()
                || !email.contains("@")
                || !"true".equalsIgnoreCase(emailVerified)
                || expiresAt <= nowEpochSecond) {
            throw invalidToken();
        }

        return new GoogleIdentity(
                subject,
                email.trim().toLowerCase(),
                normalizeName(name, email)
        );
    }

    private IllegalArgumentException invalidToken() {
        return new IllegalArgumentException(
                "Google 로그인 정보를 확인할 수 없습니다. 다시 시도해주세요.");
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private long parseLong(Object value) {
        if (value == null) return 0L;
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private String normalizeName(String name, String email) {
        String candidate =
                name == null || name.isBlank()
                        ? email.substring(0, email.indexOf('@'))
                        : name.trim();
        return candidate.length() <= 40
                ? candidate
                : candidate.substring(0, 40);
    }

    public record GoogleIdentity(
            String subject,
            String email,
            String name) {}
}
