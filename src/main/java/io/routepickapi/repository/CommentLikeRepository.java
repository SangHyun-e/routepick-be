package io.routepickapi.repository;

import io.routepickapi.entity.comment.Comment;
import io.routepickapi.entity.comment.CommentLike;
import io.routepickapi.entity.user.User;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentLikeRepository extends JpaRepository<CommentLike, Long> {

    Optional<CommentLike> findByCommentAndUser(Comment comment, User user);

    boolean existsByCommentAndUser(Comment comment, User user);

    void deleteByCommentAndUser(Comment comment, User user);

    @Query("""
            select cl.comment.id
            from CommentLike cl
            where cl.comment.id in :commentIds
              and cl.user = :user
        """)
    Set<Long> findLikedCommentIdsByUserAndCommentIds(
        @Param("commentIds") List<Long> commentIds,
        @Param("user") User user
    );
}
