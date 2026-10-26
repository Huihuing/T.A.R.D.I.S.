-- Additive only. Receipts commit atomically with wallet/order mutations.
-- Retain keys so a delayed retry cannot repeat a previously committed mutation.
CREATE TABLE mutation_request (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(255) NOT NULL,
    request_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    operation VARCHAR(32) NOT NULL,
    fingerprint VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    response_status INT NOT NULL,
    response_body TEXT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_mutation_request_user_key UNIQUE (username, request_key)
);
