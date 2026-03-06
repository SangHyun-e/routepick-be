-- V1__init.post.sql
-- 최초 스키마 : posts, post_tags
-- 문자셋/Collaction은 도커 MySQL의 기본과 맞춤

CREATE TABLE IF NOT EXISTS posts (
    id            BIGINT NOT NULL AUTO_INCREMENT,
    title         VARCHAR(120) NOT NULL,
    content       VARCHAR(4000) NOT NULL,
    latitude      DOUBLE NULL,
    longitude     DOUBLE NULL,
    region        VARCHAR(120) NULL,
    status        VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    like_count    INT NOT NULL DEFAULT 0,
    view_count    INT NOT NULL DEFAULT 0,

    -- BaseEntity(Auditing)
    created_at    DATETIME(6) NOT NULL,
    updated_at    DATETIME(6) NOT NULL,
    created_by    VARCHAR(100) NULL,
    updated_by    VARCHAR(100) NULL,

    PRIMARY KEY (id)
)   ENGINE = InnoDB
    DEFAULT CHARSET = utf8mb4
    COLLATE = utf8mb4_general_ci;

-- 인덱스
CREATE INDEX idx_posts_created_at ON posts (created_at);
CREATE INDEX idx_posts_region_created_at ON posts (region, created_at);
CREATE INDEX idx_posts_lat_lon ON posts (latitude, longitude);

-- ElementCollection(tags) 저장 테이블
CREATE TABLE IF NOT EXISTS post_tags (
    post_id   BIGINT NOT NULL,
    tag       VARCHAR(40) NOT NULL,
    PRIMARY KEY (post_id, tag),                -- 중복 태그 방지
    CONSTRAINT fk_post_tags_post
    FOREIGN KEY (post_id) REFERENCES posts(id)
    ON DELETE CASCADE
    ON UPDATE RESTRICT
    ) ENGINE=InnoDB
    DEFAULT CHARSET = utf8mb4
    COLLATE = utf8mb4_general_ci;
