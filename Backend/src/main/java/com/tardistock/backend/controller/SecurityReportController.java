package com.tardistock.backend.controller;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/security")
public class SecurityReportController {

    private static final Logger log =
            LoggerFactory.getLogger(SecurityReportController.class);
    private static final int MAX_FIELD_LENGTH = 300;

    private final ObjectMapper objectMapper;

    public SecurityReportController(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostMapping("/csp-report")
    public ResponseEntity<Void> cspReport(
            @RequestBody(required = false) String body) {
        if (body == null || body.isBlank()) {
            return ResponseEntity.noContent().build();
        }

        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode report = root.has("csp-report")
                    ? root.get("csp-report")
                    : root;

            String directive = firstText(
                    report,
                    "effective-directive",
                    "violated-directive"
            );
            String blockedUri =
                    sanitizeUri(text(report, "blocked-uri"));
            String sourceFile =
                    sanitizeUri(text(report, "source-file"));
            int lineNumber =
                    report.path("line-number").asInt(0);

            log.warn(
                    "CSP violation directive={} blockedUri={} sourceFile={} line={}",
                    safeField(directive),
                    safeField(blockedUri),
                    safeField(sourceFile),
                    lineNumber
            );
        } catch (Exception e) {
            log.warn(
                    "Malformed CSP violation report: {}",
                    e.getClass().getSimpleName()
            );
        }

        return ResponseEntity.noContent().build();
    }

    private String firstText(
            JsonNode node,
            String primary,
            String fallback) {
        String value = text(node, primary);
        return value.isBlank()
                ? text(node, fallback)
                : value;
    }

    private String text(JsonNode node, String field) {
        if (node == null) return "";
        JsonNode value = node.get(field);
        return value != null && value.isString()
                ? value.asString()
                : "";
    }

    private String sanitizeUri(String value) {
        if (value == null || value.isBlank()) return "";

        int cut = value.length();
        int queryIndex = value.indexOf('?');
        int fragmentIndex = value.indexOf('#');

        if (queryIndex >= 0) cut = Math.min(cut, queryIndex);
        if (fragmentIndex >= 0) cut = Math.min(cut, fragmentIndex);

        return value.substring(0, cut);
    }

    private String safeField(String value) {
        if (value == null || value.isBlank()) return "-";

        String sanitized = value
                .replaceAll("[\\r\\n\\t]", " ")
                .trim();

        return sanitized.length() <= MAX_FIELD_LENGTH
                ? sanitized
                : sanitized.substring(0, MAX_FIELD_LENGTH);
    }
}
