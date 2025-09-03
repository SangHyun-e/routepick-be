package io.routepickapi.repository;

import io.routepickapi.entity.comment.Comment;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 댓글 기본 CRUD + 조회용 쿼리 메서드
 * - 스프링 데이터 JPA의 쿼리 메서드 규칙으로 페이징/정렬/리스트 조회 처리
 */
public interface CommentRepository extends JpaRepository<Comment, Long> {
    // 특정 게시글의 본댓글을 페이지로 조회
    // 대댓글은 별도 API로 children 조회
    Page<Comment> findByPostIdAndParentIsNull(Long postId, Pageable pageable);

    // 특정 부모 댓글의 자식들(대댓글) 목록을 작성시간 오름차순으로 조회
    // 트리 출력 시 자연스러운 시간순 정렬
    List<Comment> findByParentIdOrderByCreatedAtAsc(Long parentId);

    // 여러 부모 댓글에 대한 자식들을 한 번에 조회
    // 본댓글 목록 페이징 후, 그 부모들에 대한 대댓글을 일괄 로딩할 때 사용
    List<Comment> findByParentIdInOrderByCreatedAtAsc(List<Long> parentId);
}
