package io.routepickapi.repository;

import io.routepickapi.entity.comment.Comment;
import io.routepickapi.entity.comment.CommentStatus;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CommentRepositoryCustom {

    /**
     * 루트 댓글 페이지:
     * - ACTIVE/DELETED 모두 노출
     * - 최신순
     */
    Page<Comment> findRootsForList(Long postId, Pageable pageable);

    /**
     * 여러 부모의 대댓글 일괄 로딩:
     * - ACTIVE + DELETED (삭제된 대댓글도 마스킹 필요)
     * - 작성시간 오름차순
     */
    List<Comment> findRepliesForList(List<Long> parentIds);

    /**
     * 베스트 댓글(상단 노출용)
     * - 루트/대댓글 구분 없이 후보
     * - status = ACTIVE 만 (삭제 댓글은 베스트에서 제외)
     * - likeCount >= minLikes
     * - likeCount DESC, createAt DESC
     * - limit 개수만 반환
     */
    List<Comment> findBestComments(Long postId, int minLikes, int limit);

    /**
     * 관리자 댓글 목록
     * - 상태 필터 + 키워드(댓글/게시글/작성자) 검색
     */
    Page<Comment> findForAdmin(List<CommentStatus> statuses, String keyword, Pageable pageable);

    /**
     * 루트 댓글의 위치 계산용
     * - 최신순 기준으로 createdAt 이후 댓글 수
     */
    long countRootsBefore(Long postId, java.time.LocalDateTime createdAt);
}
