package io.routepickapi.entity.comment;

/*
 * 댓글 좋아요 엔티티
 * - 특정 댓글(Comment)에 대해 특정 사용자가 좋아요를 눌렀는지 기록하는 교차 테이블 엔티티
 */

import io.routepickapi.common.model.BaseTimeEntity;
import io.routepickapi.entity.user.User;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "comment_likes",
    // (comment_id, user_id) 조합은 유니크 -> 같은 유저가 같은 댓글에 좋아요 2번누르는 것을 DB 레벨에서 방어
    uniqueConstraints = @UniqueConstraint(columnNames = {"comment_id", "user_id"}),
    indexes = {
        @Index(name = "idx_comment_likes_comment", columnList = "comment_id"),
        @Index(name = "idx_comment_likes_user", columnList = "user_id")
    }
)

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommentLike extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /*
     * 좋아요 대상 댓글
     * - @ManyToOne: 좋아요 여러개가 하나의 댓글에 달림 (N:1)
     * - fetch = LAZY: CommentLike 조회 시 Comment 를 즉시 가져오지 않고, 필요할 때 로딩(성능)
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "comment_id", nullable = false)
    private Comment comment;

    /*
     * 좋아요 누른 사용자
     * - @ManyToOne: 좋아요 어러개가 하나의 사용자에 달림 (N:1)
     * - fetch = LAZY: User도 필요할때만 로딩
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    public CommentLike(Comment comment, User user) {
        if (comment == null || user == null) {
            throw new IllegalArgumentException("comment and user required");
        }
        this.comment = comment;
        this.user = user;
    }
}
