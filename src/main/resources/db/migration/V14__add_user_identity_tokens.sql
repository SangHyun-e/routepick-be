-- OAuth 토큰 저장 컬럼 추가
ALTER TABLE user_identities
    ADD COLUMN access_token VARCHAR(512) NULL,
    ADD COLUMN refresh_token VARCHAR(512) NULL,
    ADD COLUMN access_token_expires_at DATETIME(6) NULL,
    ADD COLUMN refresh_token_expires_at DATETIME(6) NULL;
