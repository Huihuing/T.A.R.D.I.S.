package com.tardistock.backend.controller;

import tools.jackson.databind.ObjectMapper;
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
    void acceptsReportingApiPayload() {
        String body = """
                [
                  {
                    "age": 12,
                    "type": "csp-violation",
                    "url": "https://tardis-neon.vercel.app/dashboard",
                    "body": {
                      "effectiveDirective": "frame-src",
                      "blockedURL": "https://example.invalid/frame?token=secret#x",
                      "sourceFile": "https://tardis-neon.vercel.app/assets/index.js?build=1",
                      "lineNumber": 44
                    }
                  }
                ]
                """;

        ResponseEntity<Void> response =
                controller.cspReport(body);

        assertEquals(204, response.getStatusCode().value());
    }

    @Test
    void ignoresOversizedPayloadWithoutFailingReporter() {
        String body = "x".repeat(16_385);

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
