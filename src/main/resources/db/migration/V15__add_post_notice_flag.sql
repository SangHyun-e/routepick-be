-- 게시글 공지 여부
ALTER TABLE posts
    ADD COLUMN is_notice TINYINT(1) NOT NULL DEFAULT 0;
