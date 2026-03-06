-- V19__add_user_withdraw_reason.sql

ALTER TABLE users
    ADD COLUMN withdraw_reason VARCHAR(255) NULL,
    ADD COLUMN rejoin_restriction_release_reason VARCHAR(255) NULL;
