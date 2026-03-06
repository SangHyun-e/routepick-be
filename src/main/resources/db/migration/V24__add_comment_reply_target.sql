ALTER TABLE comments
    ADD COLUMN reply_target_id BIGINT NULL AFTER parent_id,
    ADD INDEX idx_comments_reply_target (reply_target_id),
    ADD CONSTRAINT fk_comments_reply_target
        FOREIGN KEY (reply_target_id) REFERENCES comments (id)
            ON DELETE SET NULL
            ON UPDATE RESTRICT;
