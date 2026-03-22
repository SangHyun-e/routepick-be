-- V25: Create drive_spots table for curated drive recommendations

CREATE TABLE drive_spots
(
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    name              VARCHAR(120) NOT NULL,
    lat               DOUBLE       NOT NULL,
    lng               DOUBLE       NOT NULL,
    region            VARCHAR(50)  NOT NULL,
    spot_type         VARCHAR(80)  NULL,
    themes            VARCHAR(255) NULL,
    view_score        DOUBLE       NOT NULL DEFAULT 0.6,
    drive_suitability DOUBLE       NOT NULL DEFAULT 0.6,
    stay_minutes      INT          NOT NULL DEFAULT 30,
    is_active         TINYINT(1)   NOT NULL DEFAULT 1,
    source_type       VARCHAR(20)  NOT NULL DEFAULT 'CURATED',
    created_at        DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at        DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_drive_spots_region ON drive_spots (region);
CREATE INDEX idx_drive_spots_active ON drive_spots (is_active);
CREATE INDEX idx_drive_spots_lat_lng ON drive_spots (lat, lng);
