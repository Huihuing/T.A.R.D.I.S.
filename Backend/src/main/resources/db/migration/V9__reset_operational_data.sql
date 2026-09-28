-- One-time operational data reset authorized on 2026-09-28.
-- Preserves schema and Flyway history while removing all application data.
-- Deletion order follows foreign-key dependencies; no FK checks are disabled.

DELETE FROM `comment`;
DELETE FROM `bookmark`;
DELETE FROM `ledger_entry`;
DELETE FROM `limit_order`;
DELETE FROM `notifications`;
DELETE FROM `portfolio`;
DELETE FROM `portfolio_snapshot`;
DELETE FROM `price_alert`;
DELETE FROM `refresh_token`;
DELETE FROM `trade_history`;
DELETE FROM `user_economy`;
DELETE FROM `wallet`;
DELETE FROM `post`;
DELETE FROM `community_report`;
DELETE FROM `email_verification`;
DELETE FROM `password_reset_code`;
DELETE FROM `member`;

ALTER TABLE `comment` AUTO_INCREMENT = 1;
ALTER TABLE `bookmark` AUTO_INCREMENT = 1;
ALTER TABLE `ledger_entry` AUTO_INCREMENT = 1;
ALTER TABLE `limit_order` AUTO_INCREMENT = 1;
ALTER TABLE `notifications` AUTO_INCREMENT = 1;
ALTER TABLE `portfolio` AUTO_INCREMENT = 1;
ALTER TABLE `portfolio_snapshot` AUTO_INCREMENT = 1;
ALTER TABLE `price_alert` AUTO_INCREMENT = 1;
ALTER TABLE `refresh_token` AUTO_INCREMENT = 1;
ALTER TABLE `trade_history` AUTO_INCREMENT = 1;
ALTER TABLE `user_economy` AUTO_INCREMENT = 1;
ALTER TABLE `wallet` AUTO_INCREMENT = 1;
ALTER TABLE `post` AUTO_INCREMENT = 1;
ALTER TABLE `community_report` AUTO_INCREMENT = 1;
ALTER TABLE `email_verification` AUTO_INCREMENT = 1;
ALTER TABLE `password_reset_code` AUTO_INCREMENT = 1;
ALTER TABLE `member` AUTO_INCREMENT = 1;
