package io.routepickapi.repository;

import io.routepickapi.entity.comment.Comment;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CommentRepositoryCustom {

    /**
     * 루트 댓글 페이지:
     * - ACTIVE 는 항상 노출
     * - DELETED 라도 ACTVIE 자식이 하나로 있으면 노출
     * - 최신순
     */
    Page<Comment> findRootsForList(Long postId, Pageable pageable);

    /**
     * 여러 부모의 대댓글 일괄 로딩:
     * - ACTIVE 만
     * - 작성시간 오름차순
     */
    List<Comment> findActiveReplies(List<Long> parentIds);
}
