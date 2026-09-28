-- One-time cleanup for pre-Flyway member.email duplicates.
-- member has no created_at column, so the highest member.id is used as the
-- best available proxy for the newest account in each case-insensitive group.
-- Older duplicate accounts are preserved, but their email is moved to a
-- non-routable unique placeholder before the database UNIQUE constraint is
-- added. No account, wallet, trade, post, or ledger data is deleted.

UPDATE `member` m
JOIN (
    SELECT LOWER(`email`) AS `normalized_email`, MAX(`id`) AS `keep_id`
    FROM `member`
    GROUP BY LOWER(`email`)
    HAVING COUNT(*) > 1
) duplicate_group
    ON LOWER(m.`email`) = duplicate_group.`normalized_email`
SET m.`email` = CONCAT('deduped-', m.`id`, '@invalid.local')
WHERE m.`id` <> duplicate_group.`keep_id`;

ALTER TABLE `member`
    ADD CONSTRAINT `uk_member_email` UNIQUE (`email`);
