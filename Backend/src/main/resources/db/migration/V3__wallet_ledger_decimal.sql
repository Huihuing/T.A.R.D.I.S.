-- Phase 1 of money precision hardening.
-- Existing application behavior stores wallet/ledger values at cent precision.
-- Convert only cash/ledger columns in this migration; stock-price precision is handled separately.

ALTER TABLE `wallet`
  MODIFY COLUMN `balance` DECIMAL(19,2) NOT NULL;

ALTER TABLE `ledger_entry`
  MODIFY COLUMN `amount` DECIMAL(19,2) NOT NULL,
  MODIFY COLUMN `balance_after` DECIMAL(19,2) NOT NULL;
