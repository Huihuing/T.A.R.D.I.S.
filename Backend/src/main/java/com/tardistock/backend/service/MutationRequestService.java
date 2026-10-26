package com.tardistock.backend.service;

import com.tardistock.backend.entity.MutationRequest;
import com.tardistock.backend.repository.MutationRequestRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;

@Service
public class MutationRequestService {
    private final MutationRequestRepository requests;
    private final TransactionTemplate transactions;
    private final ObjectMapper json;

    public MutationRequestService(MutationRequestRepository requests,
                                  PlatformTransactionManager transactionManager, ObjectMapper json) {
        this.requests = requests;
        this.transactions = new TransactionTemplate(transactionManager);
        this.json = json;
    }

    public ResponseEntity<?> execute(String username, String key, String operation,
                                     Map<String, ?> semanticPayload,
                                     Supplier<ResponseEntity<?>> mutation) {
        if (key != null && !key.matches("[A-Za-z0-9._:-]{8,64}")) {
            throw new IllegalArgumentException("Idempotency-Key는 8~64자의 영문, 숫자 또는 ._:- 이어야 합니다.");
        }
        String fingerprint = digest(json.writeValueAsString(new TreeMap<>(semanticPayload)));
        try {
            return transactions.execute(status -> {
                MutationRequest receipt = null;
                if (key != null) {
                    var existing = requests.findByUsernameAndRequestKey(username, key);
                    if (existing.isPresent()) return replay(existing.get(), operation, fingerprint);
                    receipt = new MutationRequest(username, key, operation, fingerprint);
                    // The unique insert serializes simultaneous retries before any balance changes.
                    requests.saveAndFlush(receipt);
                }
                ResponseEntity<?> response = mutation.get();
                boolean failed = response.getStatusCode().isError()
                        || response.getBody() instanceof Map<?, ?> body
                        && "FAIL".equals(body.get("status"));
                if (failed) {
                    status.setRollbackOnly();
                    return response;
                }
                if (receipt != null) {
                    receipt.complete(response.getStatusCode().value(), json.writeValueAsString(response.getBody()));
                    requests.save(receipt);
                }
                return response;
            });
        } catch (DataIntegrityViolationException conflict) {
            // A competing transaction may have committed while our unique insert waited.
            // Read in a new transaction: the failed transaction/snapshot must not be reused.
            if (key != null) {
                ResponseEntity<?> replayed = transactions.execute(status -> requests
                        .findByUsernameAndRequestKey(username, key)
                        .map(receipt -> replay(receipt, operation, fingerprint)).orElse(null));
                if (replayed != null) return replayed;
            }
            throw conflict;
        }
    }

    private ResponseEntity<?> replay(MutationRequest receipt, String operation, String fingerprint) {
        if (!receipt.matches(operation, fingerprint)) {
            return ResponseEntity.status(409).body(Map.of(
                    "message", "같은 Idempotency-Key를 다른 요청에 사용할 수 없습니다."));
        }
        if (receipt.getResponseBody() == null) {
            return ResponseEntity.status(409).body(Map.of("message", "요청이 처리 중입니다. 같은 키로 다시 확인해주세요."));
        }
        return ResponseEntity.status(receipt.getResponseStatus())
                .body(json.readValue(receipt.getResponseBody(), Map.class));
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
