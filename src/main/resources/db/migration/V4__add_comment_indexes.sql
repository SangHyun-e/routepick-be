-- 목적
-- 1) 루트 목록: post_id + parent_id(NULL) + status 필터 후 created_at 정렬
-- 2) 대댓글 조회: parent_id IN (...) + status=ACTIVE 후 created_at 정렬

-- 기존 인텍스 교체 (루트 목록에 특화된 새 인덱스로 대체)
DROP INDEX idx_comments_post_parent_created ON comments;

-- 루트 목록 최적화
CREATE INDEX idx_comments_posts_parent_status_created
    ON comments (post_id, parent_id, status, created_at);

-- 대댓글 로딩 최적화
CREATE INDEX idx_comments_parent_status_created
    ON comments (parent_id, status, created_at);