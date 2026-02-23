-- V17__create_user_status_history.sql
-- 관리자 사용자 상태 변경 이력 테이블

CREATE TABLE user_status_history (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    from_status VARCHAR(20) NOT NULL,
    to_status VARCHAR(20) NOT NULL,
    reason VARCHAR(255) NULL,
    admin_user_id BIGINT NULL,
    created_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_user_status_history_user_created (user_id, created_at DESC)
);
