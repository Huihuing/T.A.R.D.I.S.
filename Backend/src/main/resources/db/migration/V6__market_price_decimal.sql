-- Phase 4 of money precision hardening.
-- Market-derived prices preserve sub-cent precision up to six decimal places.

ALTER TABLE `portfolio`
  MODIFY COLUMN `average_price` DECIMAL(19,6) NOT NULL;

ALTER TABLE `trade_history`
  MODIFY COLUMN `price` DECIMAL(19,6) NOT NULL;
