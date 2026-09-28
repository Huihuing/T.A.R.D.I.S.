-- Align the legacy schema with the already-supported guest community model.
-- These are widening/relaxing changes only: no user rows are deleted or rewritten.

ALTER TABLE `post`
    MODIFY COLUMN `member_id` bigint NULL;

ALTER TABLE `comment`
    MODIFY COLUMN `member_id` bigint NULL;

ALTER TABLE `comment`
    MODIFY COLUMN `content` text NOT NULL;
