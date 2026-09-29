-- Per-email rolling send quota for emailed 6-digit codes (signup verification,
-- password reset, account security). Kept separate from password_reset_code
-- and email_verification because those rows are deleted on expiry or when the
-- attempt limit is exceeded, which would otherwise reset the quota.
-- Additive only: no existing table or row is modified.

CREATE TABLE `email_code_send_quota` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `email` varchar(254) NOT NULL,
  `send_count` int NOT NULL,
  `window_started_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_email_code_send_quota_email` (`email`)
);
