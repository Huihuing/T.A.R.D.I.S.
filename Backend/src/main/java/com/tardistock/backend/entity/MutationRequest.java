package com.tardistock.backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** A committed receipt is saved in the same transaction as the mutation. */
@Entity
@Table(name = "mutation_request", uniqueConstraints = @UniqueConstraint(
        name = "uk_mutation_request_user_key", columnNames = {"username", "request_key"}))
public class MutationRequest {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 255)
    private String username;
    @Column(name = "request_key", nullable = false, length = 64)
    private String requestKey;
    @Column(nullable = false, length = 32)
    private String operation;
    @Column(nullable = false, length = 64)
    private String fingerprint;
    @Column(name = "response_status", nullable = false)
    private int responseStatus;
    @Column(name = "response_body", columnDefinition = "text")
    private String responseBody;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected MutationRequest() {}

    public MutationRequest(String username, String key, String operation, String fingerprint) {
        this.username = username;
        this.requestKey = key;
        this.operation = operation;
        this.fingerprint = fingerprint;
        this.createdAt = LocalDateTime.now(java.time.ZoneOffset.UTC);
    }

    public boolean matches(String operation, String fingerprint) {
        return this.operation.equals(operation) && this.fingerprint.equals(fingerprint);
    }

    public void complete(int status, String body) {
        responseStatus = status;
        responseBody = body;
    }

    public int getResponseStatus() { return responseStatus; }
    public String getResponseBody() { return responseBody; }
}
