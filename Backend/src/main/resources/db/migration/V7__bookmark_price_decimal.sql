-- Phase 5 of money precision hardening.
-- Bookmark prices are market-derived snapshots and preserve six decimal places.

ALTER TABLE `bookmark`
  MODIFY COLUMN `price` DECIMAL(19,6) NOT NULL;
