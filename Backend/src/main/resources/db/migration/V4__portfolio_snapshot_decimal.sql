-- Phase 2 of money precision hardening.
-- Snapshot values are rounded to cent precision before persistence.

ALTER TABLE `portfolio_snapshot`
  MODIFY COLUMN `cash_balance` DECIMAL(19,2) NOT NULL,
  MODIFY COLUMN `invested_value` DECIMAL(19,2) NOT NULL,
  MODIFY COLUMN `total_assets` DECIMAL(19,2) NOT NULL;
