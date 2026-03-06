-- V2__add_comment.sql
-- 계층형 댓글: post_id로 소속, parent_id로 대댓글 표현

CREATE TABLE IF NOT EXISTS comments (
    id          BIGINT NOT NULL AUTO_INCREMENT,
    post_id     BIGINT NOT NULL,
    parent_id   BIGINT NULL,
    depth       TINYINT NOT NULL DEFAULT 0,     -- 0: 본댓글, 1+: 대댓글
    content     VARCHAR(1000) NOT NULL,
    like_count  INT NOT NULL DEFAULT 0,
    status      VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',

    created_at  DATETIME(6) NOT NULL,
    updated_at  DATETIME(6) NOT NULL,
    created_by  VARCHAR(100) NULL,
    updated_by  VARCHAR(100) NULL,

    PRIMARY KEY (id),

    CONSTRAINT fk_comments_post
        FOREIGN KEY (post_id) REFERENCES posts(id)
        ON DELETE CASCADE
        ON UPDATE RESTRICT,

    CONSTRAINT fk_comments_parent
        FOREIGN KEY (parent_id) REFERENCES comments(id)
        ON DELETE CASCADE
        ON UPDATE RESTRICT
)   ENGINE=InnoDB
    DEFAULT CHARSET = utf8mb4
    COLLATE = utf8mb4_general_ci;

-- 조회 최적화 서비스
CREATE INDEX idx_comments_post_parent_created
    ON comments (post_id, parent_id, created_at);

CREATE INDEX idx_comments_post_created
    ON comments (post_id, created_at);