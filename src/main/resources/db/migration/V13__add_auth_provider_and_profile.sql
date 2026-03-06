-- OAuth 계정 구분 및 프로필 완료 플래그 추가
ALTER TABLE users
    ADD COLUMN auth_provider VARCHAR(20) NOT NULL DEFAULT 'LOCAL',
    ADD COLUMN profile_complete TINYINT(1) NOT NULL DEFAULT 1;

ALTER TABLE users
    MODIFY password_hash VARCHAR(255) NULL;
