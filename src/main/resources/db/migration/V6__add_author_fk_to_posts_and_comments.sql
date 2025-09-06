-- posts.usr_id 추가 ( + 인텍스 + FK )
ALTER TABLE posts
    ADD COLUMN user_id BIGINT NULL AFTER id,
    ADD INDEX idx_posts_user (user_id),
    ADD CONSTRAINT fk_posts_user
        FOREIGN KEY (user_id) REFERENCES users (id)
            ON DELETE RESTRICT
            ON UPDATE RESTRICT;

-- comments.user_id 추가 ( + 인덱스 + FK )
ALTER TABLE comments
    ADD COLUMN user_id BIGINT NULL AFTER id,
    ADD INDEX idx_comments_user (user_id),
    ADD CONSTRAINT fk_comments_user
        FOREIGN KEY (user_id) REFERENCES users (id)
            ON DELETE RESTRICT
            ON UPDATE RESTRICT;
