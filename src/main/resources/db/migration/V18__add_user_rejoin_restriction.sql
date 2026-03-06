-- V18__add_user_rejoin_restriction.sql

ALTER TABLE users
    ADD COLUMN deleted_email_hash VARCHAR(64) NULL,
    ADD COLUMN rejoin_restricted_until DATETIME(6) NULL,
    ADD COLUMN rejoin_restriction_released_at DATETIME(6) NULL,
    ADD COLUMN rejoin_restriction_released_by BIGINT NULL,
    ADD INDEX idx_users_deleted_email_rejoin (deleted_email_hash, rejoin_restricted_until);
