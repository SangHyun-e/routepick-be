package io.routepickapi.repository;

import io.routepickapi.entity.post.Post;
import io.routepickapi.entity.post.PostStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostRepository extends JpaRepository<Post, Long> {

    // 최신순 페이지 조회
    @EntityGraph(attributePaths = {"author"})
    Page<Post> findByStatusOrderByCreatedAtDesc(PostStatus status, Pageable pageable);

    // 지역 + 상태 필터 최신순 페이지 조회
    @EntityGraph(attributePaths = {"author"})
    Page<Post> findByRegionAndStatusOrderByCreatedAtDesc(String region, PostStatus status,
        Pageable pageable);

    // 상세 화면: 다건 조회 시 태그까지 로딩 (N+1 방지)
    @EntityGraph(attributePaths = {"tags", "author"})
    Optional<Post> findWithTagsById(Long id);

    // 상태까지 같이 확인하는 단건 조회(상세에서 숨김/삭제 걸러낼 떄)
    Optional<Post> findByIdAndStatus(Long id, PostStatus status);

    // 좋아요 +1 (동시성 안전)
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Post p set p.likeCount = p.likeCount + 1 where p.id = :id and p.status = :status")
    int incrementLikeCount(@Param("id") Long id, @Param("status") PostStatus status);

    // 좋아요 -1 (0 미만 방지)
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update Post p
           set p.likeCount = case when p.likeCount > 0 then p.likeCount - 1 else 0 end
         where p.id = :id
           and p.status = :status
        """)
    int decrementLikeCount(@Param("id") Long id, @Param("status") PostStatus status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Post p set p.viewCount = p.viewCount + 1 "
        + "where p.id = :id and p.status = :status")
    int incrementViewCount(@Param("id") Long id, @Param("status") PostStatus status);

    // 작성자(owner)인지 여부를 PK로 판단
    boolean existsByIdAndAuthorId(Long id, Long authorId);

    // 토글 응답에서 최신 likeCount 반환용
    @Query("select p.likeCount from Post p where p.id = :id")
    Optional<Integer> findLikeCountById(@Param("id") Long id);
}
