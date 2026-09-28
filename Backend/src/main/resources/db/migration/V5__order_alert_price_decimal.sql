-- Phase 3 of money precision hardening.
-- Limit-order and alert prices are normalized to cent precision before persistence.

ALTER TABLE `limit_order`
  MODIFY COLUMN `limit_price` DECIMAL(19,2) NOT NULL,
  MODIFY COLUMN `fill_price` DECIMAL(19,2) NULL;

ALTER TABLE `price_alert`
  MODIFY COLUMN `target_price` DECIMAL(19,2) NOT NULL;
