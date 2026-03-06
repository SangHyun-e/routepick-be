CREATE TABLE IF NOT EXISTS notifications
(
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    user_id         BIGINT       NOT NULL,
    actor_id        BIGINT       NULL,
    actor_nickname  VARCHAR(40)  NULL,
    type            VARCHAR(40)  NOT NULL,
    title           VARCHAR(120) NOT NULL,
    message         VARCHAR(255) NOT NULL,
    resource_type   VARCHAR(40)  NULL,
    resource_id     BIGINT       NULL,
    reason          VARCHAR(255) NULL,
    is_read         TINYINT(1)   NOT NULL DEFAULT 0,
    read_at         DATETIME(6)  NULL,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_notifications_user_created (user_id, created_at),
    KEY idx_notifications_user_read (user_id, is_read, created_at),
    CONSTRAINT fk_notifications_user
        FOREIGN KEY (user_id) REFERENCES users (id)
            ON DELETE RESTRICT
            ON UPDATE RESTRICT,
    CONSTRAINT fk_notifications_actor
        FOREIGN KEY (actor_id) REFERENCES users (id)
            ON DELETE RESTRICT
            ON UPDATE RESTRICT
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci;
