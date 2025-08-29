package io.routepickapi.entity.post;

// 게시글 노출 상태 관리용 Enum
public enum PostStatus {
    ACTIVE, // 정상 노출
    HIDDEN, // 관리자/작성자등에 의해 숨김처리
    DELETED // 소프트 삭제(복구/보관 목적)
}
