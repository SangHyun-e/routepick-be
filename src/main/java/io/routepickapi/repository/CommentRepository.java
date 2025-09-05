package io.routepickapi.repository;

import io.routepickapi.entity.comment.Comment;
import io.routepickapi.entity.comment.CommentStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 댓글 기본 CRUD + 조회용 쿼리 메서드
 * - 스프링 데이터 JPA의 쿼리 메서드 규칙으로 페이징/정렬/리스트 조회 처리
 */
public interface CommentRepository extends JpaRepository<Comment, Long>, CommentRepositoryCustom {
    

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
