package com.tardistock.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SecurityReportControllerTest {

    private final SecurityReportController controller =
            new SecurityReportController(new ObjectMapper());

    @Test
    void acceptsLegacyCspReportPayload() {
        String body = """
                {
                  "csp-report": {
                    "effective-directive": "script-src-elem",
                    "blocked-uri": "https://example.invalid/script.js?token=secret#x",
                    "source-file": "https://tardis-neon.vercel.app/login?next=/dashboard",
                    "line-number": 12
                  }
                }
                """;

        ResponseEntity<Void> response =
                controller.cspReport(body);

        assertEquals(204, response.getStatusCode().value());
    }

    @Test
    void acceptsMalformedPayloadWithoutFailingReporter() {
        ResponseEntity<Void> response =
                controller.cspReport("{not-json");

        assertEquals(204, response.getStatusCode().value());
    }

    @Test
    void acceptsEmptyPayload() {
        ResponseEntity<Void> response =
                controller.cspReport("");

        assertEquals(204, response.getStatusCode().value());
    }
}
