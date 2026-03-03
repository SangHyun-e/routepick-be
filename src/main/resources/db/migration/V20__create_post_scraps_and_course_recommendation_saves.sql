-- V20: Add post scraps and course recommendation saves

CREATE TABLE post_scraps
(
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    post_id    BIGINT      NOT NULL,
    user_id    BIGINT      NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_post_scraps_post_user UNIQUE (post_id, user_id),
    CONSTRAINT fk_post_scraps_post FOREIGN KEY (post_id) REFERENCES posts (id) ON DELETE CASCADE,
    CONSTRAINT fk_post_scraps_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_post_scraps_post ON post_scraps (post_id);
CREATE INDEX idx_post_scraps_user ON post_scraps (user_id);

CREATE TABLE course_recommendation_saves
(
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT       NOT NULL,
    origin        VARCHAR(120) NOT NULL,
    destination   VARCHAR(120) NOT NULL,
    theme         VARCHAR(20)  NOT NULL,
    route_summary VARCHAR(500) NOT NULL,
    explanation   TEXT         NOT NULL,
    created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_course_recommendation_saves_user FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_course_recommendation_saves_user ON course_recommendation_saves (user_id);
CREATE INDEX idx_course_recommendation_saves_created_at ON course_recommendation_saves (created_at);

CREATE TABLE course_recommendation_stops
(
    course_recommendation_id BIGINT       NOT NULL,
    stop_order               INT          NOT NULL,
    name                     VARCHAR(120) NOT NULL,
    address                  VARCHAR(255) NOT NULL,
    x                        DOUBLE       NOT NULL,
    y                        DOUBLE       NOT NULL,
    category                 VARCHAR(120) NOT NULL,
    PRIMARY KEY (course_recommendation_id, stop_order),
    CONSTRAINT fk_course_recommendation_stops_course FOREIGN KEY (course_recommendation_id)
        REFERENCES course_recommendation_saves (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
