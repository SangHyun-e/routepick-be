package io.routepickapi.entity.comment;

/**
 * 댓글 상태
 * - ACTIVE: 정상 노출
 * - DELETED: 소프트 삭제(내용은 숨김 처리용도)
 */
public enum CommentStatus {
    ACTIVE, DELETED
}
