-- Idempotency receipts for PULSE global logout.
-- Existing member IDs, wallet balances and ledger entries are unmodified.
CREATE TABLE pulse_logout_receipts (
  operation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  member_id BIGINT NOT NULL,
  processed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (operation_id),
  KEY idx_pulse_logout_receipts_member (member_id),
  CONSTRAINT fk_pulse_logout_receipt_member FOREIGN KEY (member_id)
    REFERENCES member(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
