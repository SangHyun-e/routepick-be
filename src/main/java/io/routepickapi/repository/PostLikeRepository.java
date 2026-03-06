package io.routepickapi.repository;

import io.routepickapi.entity.post.PostLike;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostLikeRepository extends JpaRepository<PostLike, Long> {


    // postId + userId로 기존 좋아요 레코드 조회 (토글용)
    Optional<PostLike> findByPostIdAndUserId(Long postId, Long userId);

    // 상세/수정 응답에서 "내가 좋아요 눌렀는지" 체크용
    boolean existsByPostIdAndUserId(Long postId, Long userId);

    // 목록/검색에서 N+1 없이 "좋아요 누른 글 id들"만 일괄 조회
    @Query("select pl.post.id from PostLike pl where pl.post.id in :postIds and pl.user.id = :userId")
    Set<Long> findLikedPostIdsByUserIdAndPostIds(
        @Param("postIds") List<Long> postIds,
        @Param("userId") Long userId);
}
