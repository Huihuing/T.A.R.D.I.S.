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
    private static final int MAX_BODY_LENGTH = 16_384;
    private static final int MAX_REPORTS_PER_REQUEST = 8;

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

        if (body.length() > MAX_BODY_LENGTH) {
            log.warn(
                    "Oversized CSP violation report ignored length={}",
                    body.length()
            );
            return ResponseEntity.noContent().build();
        }

        try {
            JsonNode root = objectMapper.readTree(body);
            logReports(root);
        } catch (Exception e) {
            log.warn(
                    "Malformed CSP violation report: {}",
                    e.getClass().getSimpleName()
            );
        }

        return ResponseEntity.noContent().build();
    }

    private void logReports(JsonNode root) {
        if (root == null) {
            return;
        }

        if (root.isArray()) {
            int count = Math.min(
                    root.size(),
                    MAX_REPORTS_PER_REQUEST
            );
            for (int i = 0; i < count; i++) {
                logReport(extractReportBody(root.get(i)));
            }
            return;
        }

        logReport(extractReportBody(root));
    }

    private JsonNode extractReportBody(JsonNode node) {
        if (node == null || !node.isObject()) {
            return node;
        }

        JsonNode legacy = node.get("csp-report");
        if (legacy != null && legacy.isObject()) {
            return legacy;
        }

        JsonNode reportingApiBody = node.get("body");
        if (reportingApiBody != null
                && reportingApiBody.isObject()) {
            return reportingApiBody;
        }

        return node;
    }

    private void logReport(JsonNode report) {
        if (report == null || !report.isObject()) {
            return;
        }

        String directive = firstText(
                report,
                "effective-directive",
                "violated-directive",
                "effectiveDirective"
        );
        String blockedUri = sanitizeUri(firstText(
                report,
                "blocked-uri",
                "blockedURL"
        ));
        String sourceFile = sanitizeUri(firstText(
                report,
                "source-file",
                "sourceFile"
        ));
        int lineNumber = firstInt(
                report,
                "line-number",
                "lineNumber"
        );

        if (directive.isBlank()
                && blockedUri.isBlank()
                && sourceFile.isBlank()) {
            return;
        }

        log.warn(
                "CSP violation directive={} blockedUri={} sourceFile={} line={}",
                safeField(directive),
                safeField(blockedUri),
                safeField(sourceFile),
                lineNumber
        );
    }

    private String firstText(
            JsonNode node,
            String... fields) {
        for (String field : fields) {
            String value = text(node, field);
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private int firstInt(
            JsonNode node,
            String... fields) {
        if (node == null) {
            return 0;
        }

        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && value.isNumber()) {
                return value.asInt(0);
            }
        }
        return 0;
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
