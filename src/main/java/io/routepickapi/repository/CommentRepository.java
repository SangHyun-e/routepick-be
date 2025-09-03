package io.routepickapi.repository;

import io.routepickapi.entity.comment.Comment;
import io.routepickapi.entity.comment.CommentStatus;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
