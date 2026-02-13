-- OAuth 계정 연동 테이블
CREATE TABLE IF NOT EXISTS user_identities
(
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    user_id          BIGINT       NOT NULL,
    provider         VARCHAR(20)  NOT NULL,
    provider_user_id VARCHAR(100) NOT NULL,
    email            VARCHAR(255) NULL,

    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,
    created_by       VARCHAR(100) NULL,
    updated_by       VARCHAR(100) NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_user_identities_provider_user_id (provider_user_id),
    KEY idx_user_identities_user_provider (user_id, provider),
    CONSTRAINT fk_user_identities_user_id FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci;
