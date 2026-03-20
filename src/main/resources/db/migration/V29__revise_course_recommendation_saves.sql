ALTER TABLE course_recommendation_saves
    DROP COLUMN origin,
    DROP COLUMN destination,
    DROP COLUMN route_summary,
    DROP COLUMN explanation,
    MODIFY COLUMN theme VARCHAR(30) NOT NULL,
    ADD COLUMN title VARCHAR(120) NOT NULL AFTER user_id,
    ADD COLUMN origin_lat DOUBLE NOT NULL AFTER title,
    ADD COLUMN origin_lng DOUBLE NOT NULL AFTER origin_lat,
    ADD COLUMN destination_lat DOUBLE NOT NULL AFTER origin_lng,
    ADD COLUMN destination_lng DOUBLE NOT NULL AFTER destination_lat,
    ADD COLUMN duration_minutes INT NOT NULL AFTER destination_lng,
    ADD COLUMN max_stops INT NOT NULL AFTER duration_minutes,
    ADD COLUMN total_distance_km DOUBLE NOT NULL AFTER max_stops,
    ADD COLUMN description TEXT NOT NULL AFTER total_distance_km,
    ADD COLUMN explain_text TEXT NULL AFTER description;

DROP TABLE IF EXISTS course_recommendation_stops;

CREATE TABLE course_recommendation_stops
(
    course_recommendation_id BIGINT       NOT NULL,
    stop_order               INT          NOT NULL,
    name                     VARCHAR(120) NOT NULL,
    lat                      DOUBLE       NOT NULL,
    lng                      DOUBLE       NOT NULL,
    type                     VARCHAR(120) NOT NULL,
    tags                     TEXT         NULL,
    stay_minutes             BIGINT       NULL,
    view_score               DOUBLE       NULL,
    drive_suitability        DOUBLE       NULL,
    segment_distance_km      DOUBLE       NULL,
    segment_duration_minutes BIGINT       NULL,
    PRIMARY KEY (course_recommendation_id, stop_order),
    CONSTRAINT fk_course_recommendation_stops_course FOREIGN KEY (course_recommendation_id)
        REFERENCES course_recommendation_saves (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE course_recommendation_include_stops
(
    course_recommendation_id BIGINT       NOT NULL,
    include_order            INT          NOT NULL,
    name                     VARCHAR(120) NOT NULL,
    lat                      DOUBLE       NOT NULL,
    lng                      DOUBLE       NOT NULL,
    PRIMARY KEY (course_recommendation_id, include_order),
    CONSTRAINT fk_course_recommendation_include_stops_course FOREIGN KEY (course_recommendation_id)
        REFERENCES course_recommendation_saves (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
