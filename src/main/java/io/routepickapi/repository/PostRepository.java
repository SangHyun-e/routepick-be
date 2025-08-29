package io.routepickapi.repository;

import io.routepickapi.entity.post.Post;
import io.routepickapi.entity.post.PostStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostRepository extends JpaRepository<Post, Long> {

    // 최신순 페이지 조회
    Page<Post> findByStatusOrderByCreatedAtDesc(PostStatus status, Pageable pageable);

    // 지역 + 상태 필터 최신순 페이지 조회
    Page<Post> findByRegionAndStatusOrderByCreatedAtDesc(String region, PostStatus status, Pageable pageable);

    // 상세 화면: 다건 조회 시 태그까지 로딩 (N+1 방지)
    @EntityGraph(attributePaths = "tags")
    Optional<Post> findWithTagsById(Long id);

    // 상태까지 같이 확인하는 단건 조회(상세에서 숨김/삭제 걸러낼 떄)
    Optional<Post> findByIdAndStatus(Long id, PostStatus status);
}
