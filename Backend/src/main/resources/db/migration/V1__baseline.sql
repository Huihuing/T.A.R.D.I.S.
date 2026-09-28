-- Baseline captured from the live Aiven MySQL schema on 2026-09-28.
-- This migration intentionally mirrors the pre-Flyway schema, including legacy constraints.
-- Existing production databases are baselined at version 1; empty databases execute this file.

CREATE TABLE `member` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `email` varchar(255) NOT NULL,
  `last_login_date` date DEFAULT NULL,
  `last_relief_date` date DEFAULT NULL,
  `name` varchar(255) NOT NULL,
  `password` varchar(255) NOT NULL,
  `pin` varchar(255) NOT NULL,
  `username` varchar(255) NOT NULL,
  `pin_configured` bit(1) DEFAULT NULL,
  `social_provider` varchar(255) DEFAULT NULL,
  `social_subject` varchar(255) DEFAULT NULL,
  `password_login_enabled` bit(1) DEFAULT NULL,
  `email_verified` bit(1) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKgc3jmn7c2abyo3wf6syln5t2i` (`username`),
  UNIQUE KEY `uk_member_social_identity` (`social_provider`,`social_subject`)
);

CREATE TABLE `post` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `content` text NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `title` varchar(255) NOT NULL,
  `member_id` bigint NOT NULL,
  `guest_ip` varchar(255) DEFAULT NULL,
  `guest_nickname` varchar(20) DEFAULT NULL,
  `guest_password_hash` varchar(100) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK83s99f4kx8oiqm3ro0sasmpww` (`member_id`),
  CONSTRAINT `FK83s99f4kx8oiqm3ro0sasmpww` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`)
);

CREATE TABLE `comment` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `content` varchar(255) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `member_id` bigint NOT NULL,
  `post_id` bigint NOT NULL,
  `guest_ip` varchar(255) DEFAULT NULL,
  `guest_nickname` varchar(20) DEFAULT NULL,
  `guest_password_hash` varchar(100) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKmrrrpi513ssu63i2783jyiv9m` (`member_id`),
  KEY `FKs1slvnkuemjsq2kj4h3vhx7i1` (`post_id`),
  CONSTRAINT `FKmrrrpi513ssu63i2783jyiv9m` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`),
  CONSTRAINT `FKs1slvnkuemjsq2kj4h3vhx7i1` FOREIGN KEY (`post_id`) REFERENCES `post` (`id`)
);

CREATE TABLE `bookmark` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `price` double NOT NULL,
  `symbol` varchar(255) NOT NULL,
  `member_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK5bm7rup91j277mc7gg63akie2` (`member_id`),
  CONSTRAINT `FK5bm7rup91j277mc7gg63akie2` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`)
);

CREATE TABLE `community_report` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `detail` varchar(500) DEFAULT NULL,
  `reason` varchar(32) NOT NULL,
  `reporter_ip` varchar(64) DEFAULT NULL,
  `reporter_username` varchar(100) DEFAULT NULL,
  `resolved_at` datetime(6) DEFAULT NULL,
  `resolved_by` varchar(100) DEFAULT NULL,
  `status` varchar(16) NOT NULL,
  `target_author` varchar(100) DEFAULT NULL,
  `target_id` bigint NOT NULL,
  `target_preview` varchar(500) NOT NULL,
  `target_type` varchar(16) NOT NULL,
  PRIMARY KEY (`id`)
);

CREATE TABLE `email_verification` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `attempts` int NOT NULL,
  `code_hash` varchar(100) NOT NULL,
  `email` varchar(254) NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `last_sent_at` datetime(6) DEFAULT NULL,
  `verified_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_email_verification_email` (`email`)
);

CREATE TABLE `ledger_entry` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `amount` double NOT NULL,
  `balance_after` double NOT NULL,
  `counterparty` varchar(80) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `description` varchar(255) DEFAULT NULL,
  `symbol` varchar(20) DEFAULT NULL,
  `type` varchar(40) NOT NULL,
  `member_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_ledger_member_time` (`member_id`,`created_at`),
  CONSTRAINT `FK7hntj556ibsrok9lglh1gmh7o` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`)
);

CREATE TABLE `limit_order` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `amount` int NOT NULL,
  `completed_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `fill_price` double DEFAULT NULL,
  `limit_price` double NOT NULL,
  `result_message` varchar(255) DEFAULT NULL,
  `side` varchar(8) NOT NULL,
  `status` varchar(16) NOT NULL,
  `symbol` varchar(20) NOT NULL,
  `member_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_limit_order_status_symbol` (`status`,`symbol`),
  KEY `idx_limit_order_member_created` (`member_id`,`created_at`),
  CONSTRAINT `FKrau55vuremwe77at88okkve1u` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`)
);

CREATE TABLE `notifications` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `message` varchar(500) NOT NULL,
  `read_at` datetime(6) DEFAULT NULL,
  `type` varchar(40) NOT NULL,
  `member_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKkyibpt8pebr6opuuxke0w5kuk` (`member_id`),
  CONSTRAINT `FKkyibpt8pebr6opuuxke0w5kuk` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`)
);

CREATE TABLE `password_reset_code` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `attempts` int NOT NULL,
  `code_hash` varchar(100) NOT NULL,
  `email` varchar(254) NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `last_sent_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_password_reset_email` (`email`)
);

CREATE TABLE `portfolio` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `amount` int NOT NULL,
  `average_price` double NOT NULL,
  `symbol` varchar(255) DEFAULT NULL,
  `member_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKhkjiiwx38ctlby4yt4y82tua7` (`member_id`),
  CONSTRAINT `FKhkjiiwx38ctlby4yt4y82tua7` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`)
);

CREATE TABLE `portfolio_snapshot` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `captured_at` datetime(6) NOT NULL,
  `cash_balance` double NOT NULL,
  `invested_value` double NOT NULL,
  `total_assets` double NOT NULL,
  `member_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_portfolio_snapshot_member_time` (`member_id`,`captured_at`),
  CONSTRAINT `FKibxddg1wxt1bkvuuflkf640ql` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`)
);

CREATE TABLE `price_alert` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `active` bit(1) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `direction` varchar(10) NOT NULL,
  `symbol` varchar(20) NOT NULL,
  `target_price` double NOT NULL,
  `triggered_at` datetime(6) DEFAULT NULL,
  `member_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_price_alert_active_symbol` (`active`,`symbol`),
  KEY `FK6k2hmkex3nfnro4n0wtsqjip2` (`member_id`),
  CONSTRAINT `FK6k2hmkex3nfnro4n0wtsqjip2` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`)
);

CREATE TABLE `refresh_token` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `token_hash` varchar(64) NOT NULL,
  `member_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_refresh_token_hash` (`token_hash`),
  KEY `idx_refresh_token_member` (`member_id`),
  CONSTRAINT `FK5gdbafb2i76hk1ai18ah6an4w` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`)
);

CREATE TABLE `trade_history` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `amount` int NOT NULL,
  `price` double NOT NULL,
  `symbol` varchar(255) DEFAULT NULL,
  `trade_time` datetime(6) DEFAULT NULL,
  `trade_type` varchar(255) DEFAULT NULL,
  `member_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK6yiof7sx65qa405v1tvsppfu6` (`member_id`),
  CONSTRAINT `FK6yiof7sx65qa405v1tvsppfu6` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`)
);

CREATE TABLE `user_economy` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `attendance_streak` int NOT NULL,
  `last_bankruptcy_claim` datetime(6) DEFAULT NULL,
  `last_check_in_date` date DEFAULT NULL,
  `post_quest_claimed_date` date DEFAULT NULL,
  `trade_quest_claimed_date` date DEFAULT NULL,
  `member_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKiqhr6sagorv68iumyw2k7oytt` (`member_id`),
  CONSTRAINT `FKplovgyr0g8tnovwtr1u5qqjq9` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`)
);

CREATE TABLE `wallet` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `balance` double NOT NULL,
  `member_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK2q49dhf6p4dy5hr57jkm2co7x` (`member_id`),
  CONSTRAINT `FK7sbx5mwwy4ug7g05vl3yt518` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`)
);