-- Track native JWT generation for explicit all-device logout.
-- Existing TARDIS accounts and member/wallet foreign keys are preserved.
-- New development-only migration; never run on production before backups.
ALTER TABLE `member`
  ADD COLUMN `session_generation` BIGINT NOT NULL DEFAULT 0;
