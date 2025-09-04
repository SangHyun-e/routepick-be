package io.routepickapi.repository;

import io.routepickapi.entity.comment.Comment;
import io.routepickapi.entity.comment.CommentStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 댓글 기본 CRUD + 조회용 쿼리 메서드
 * - 스프링 데이터 JPA의 쿼리 메서드 규칙으로 페이징/정렬/리스트 조회 처리
 */
public interface CommentRepository extends JpaRepository<Comment, Long> {

    // 1) 본댓글 페이지: 항상 ACTIVE, 최신순
    Page<Comment> findByPostIdAndParentIsNullAndStatusOrderByCreatedAtDesc(
        Long postId, CommentStatus status, Pageable pageable
    );

    // 2) 특정 부모의 대댓글: 항상 ACTIVE, 작성시간 오름차순
    List<Comment> findByParentIdAndStatusOrderByCreatedAtAsc(
        Long parentId, CommentStatus status
    );

    // 3) 여러 부모의 대댓글을 한번에 로딩(본댓글 페이지는 일괄로딩)
    List<Comment> findByParentIdInAndStatusOrderByCreatedAtAsc(
        List<Long> parentIds, CommentStatus status
    );

    // JPQL 로 like_count 증가
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Comment c "
        + "set c.likeCount = c.likeCount +1 "
        + "where c.id = :commentId "
        + "and c.post.id = :postId "
        + "and c.status = :status")
    int incrementLikeCount(
        @Param("postId") Long postId,
        @Param("commentId") Long commentId,
        @Param("status") CommentStatus status);

    // 소프트 삭제 (ACTIVE -> DELETED)
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Comment c "
        + "set c.status = :toStatus "
        + "where c.id = :commentId "
        + "and c.post.id = :postId "
        + "and c.status = :fromStatus")
    int updateStatus(
        @Param("postId") Long postId,
        @Param("commentId") Long commentId,
        @Param("fromStatus") CommentStatus fromStatus,
        @Param("toStatus") CommentStatus toStatus);

    // 필요 시 조회용
    Optional<Comment> findByIdAndPostIdAndStatus(Long id, Long postId, CommentStatus status);

}
