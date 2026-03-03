package io.routepickapi.repository;

import io.routepickapi.entity.post.PostScrap;
import io.routepickapi.entity.post.PostStatus;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostScrapRepository extends JpaRepository<PostScrap, Long> {

    Optional<PostScrap> findByPostIdAndUserId(Long postId, Long userId);

    boolean existsByPostIdAndUserId(Long postId, Long userId);

    @Query("select ps.post.id from PostScrap ps where ps.post.id in :postIds and ps.user.id = :userId")
    Set<Long> findScrappedPostIdsByUserIdAndPostIds(
        @Param("postIds") List<Long> postIds,
        @Param("userId") Long userId);

    @EntityGraph(attributePaths = {"post", "post.author"})
    Page<PostScrap> findByUserIdAndPostStatusOrderByCreatedAtDesc(Long userId,
        PostStatus status, Pageable pageable);
}
