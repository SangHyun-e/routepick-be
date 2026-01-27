CREATE TABLE comment_likes
(
    id         BIGINT   NOT NULL AUTO_INCREMENT,
    comment_id BIGINT   NOT NULL,
    user_id    BIGINT   NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,

    PRIMARY KEY (id),

    CONSTRAINT uk_comment_likes_comment_user
        UNIQUE (comment_id, user_id),

    CONSTRAINT fk_comment_likes_comment
        FOREIGN KEY (comment_id)
            REFERENCES comments (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_comment_likes_user
        FOREIGN KEY (user_id)
            REFERENCES users (id)
            ON DELETE CASCADE
);

CREATE INDEX idx_comment_likes_comment
    ON comment_likes (comment_id);

CREATE INDEX idx_comment_likes_user
    ON comment_likes (user_id);