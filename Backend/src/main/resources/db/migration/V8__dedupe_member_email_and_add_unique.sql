-- One-time cleanup for pre-Flyway member.email duplicates.
-- member has no created_at column, so the highest member.id is used as the
-- best available proxy for the newest account in each case-insensitive group.
-- Older duplicate accounts are preserved, but their email is moved to a
-- non-routable unique placeholder before the database UNIQUE constraint is
-- added. No account, wallet, trade, post, or ledger data is deleted.

CREATE TEMPORARY TABLE `_member_email_dedupe` (
    `id` BIGINT NOT NULL PRIMARY KEY,
    `replacement_email` VARCHAR(255) NOT NULL UNIQUE
);

INSERT INTO `_member_email_dedupe` (`id`, `replacement_email`)
SELECT
    m.`id`,
    CONCAT('deduped-member-', m.`id`, '@example.invalid')
FROM `member` m
JOIN (
    SELECT LOWER(`email`) AS `normalized_email`, MAX(`id`) AS `keep_id`
    FROM `member`
    GROUP BY LOWER(`email`)
    HAVING COUNT(*) > 1
) duplicate_group
    ON LOWER(m.`email`) = duplicate_group.`normalized_email`
WHERE m.`id` <> duplicate_group.`keep_id`;

UPDATE `member` m
JOIN `_member_email_dedupe` duplicate_member
    ON duplicate_member.`id` = m.`id`
SET m.`email` = duplicate_member.`replacement_email`;

ALTER TABLE `member`
    ADD CONSTRAINT `uk_member_email` UNIQUE (`email`);

DROP TEMPORARY TABLE `_member_email_dedupe`;
