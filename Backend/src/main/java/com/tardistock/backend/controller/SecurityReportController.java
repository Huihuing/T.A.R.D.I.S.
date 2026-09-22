package com.tardistock.backend.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/security")
public class SecurityReportController {

    private static final Logger log =
            LoggerFactory.getLogger(SecurityReportController.class);
    private static final int MAX_LOG_LENGTH = 2000;

    @PostMapping("/csp-report")
    public ResponseEntity<Void> cspReport(
            @RequestBody(required = false) String body) {
        if (body != null && !body.isBlank()) {
            String sanitized = body
                    .replaceAll("[\\r\\n\\t]", " ")
                    .trim();
            if (sanitized.length() > MAX_LOG_LENGTH) {
                sanitized = sanitized.substring(0, MAX_LOG_LENGTH);
            }
            log.warn("CSP violation report: {}", sanitized);
        }

        return ResponseEntity.noContent().build();
    }
}
