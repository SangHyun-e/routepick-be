-- V15__add_post_notice_flag.sql
-- 공지 여부 플래그 추가

ALTER TABLE posts
    ADD COLUMN is_notice BOOLEAN NOT NULL DEFAULT FALSE;
