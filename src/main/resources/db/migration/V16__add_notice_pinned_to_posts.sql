-- V16__add_notice_pinned_to_posts.sql
-- 공지 고정 여부 및 고정 시각 컬럼 추가

ALTER TABLE posts
    ADD COLUMN notice_pinned BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN notice_pinned_at DATETIME(6) NULL;

CREATE INDEX idx_posts_notice_pinned_notice_created
    ON posts (notice_pinned, is_notice, created_at);
