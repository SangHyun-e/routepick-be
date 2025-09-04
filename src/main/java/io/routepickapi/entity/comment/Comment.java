package io.routepickapi.entity.comment;

import io.routepickapi.common.model.BaseEntity;
import io.routepickapi.entity.post.Post;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * 댓글 엔티티
 * - 계층 구조: parent(부모) - children(자식)로 대댓글 표현
 * - depth: 0 = 본댓글, 1+ = 대댓글
 * - 소프트 삭제: status=DELETED 로만 전환, 실제 행은 남겨져있음
 */
@Getter
@ToString(exclude = {"post", "parent", "children"})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "comments")
public class Comment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 어떤 게시글의 댓글인지
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    // 부모 댓글(본댓글이면 null)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Comment parent;

    // 자식 댓글 목록*양방향 편의용: 깊은 트리 로딩은 주의)
    @OneToMany(mappedBy = "parent")
    private List<Comment> children = new ArrayList<>();

    // 0: 본댓글, 1+: 대댓글
    @Column(nullable = false)
    private int depth = 0;

    // 본문
    @NotBlank
    @Size(max = 1000)
    @Column(nullable = false, length = 1000)
    private String content;

    // 좋아요 수
    @Column(name = "like_count", nullable = false)
    private int likeCount = 0;

    // 상태(소프트 삭제 등)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CommentStatus status = CommentStatus.ACTIVE;

    @Builder
    private Comment(Post post, Comment parent, String content) {
        setPostAndParent(post, parent);
        changeContent(content);
    }

    // 도메인 메서드
    public void setPostAndParent(Post post, Comment parent) {
        if (post == null) {
            throw new IllegalArgumentException("post must not be null");
        }
        if (parent != null && parent.getPost() != null && !parent.getPost().getId()
            .equals(post.getId())) {
            throw new IllegalArgumentException("parent comment belongs to a different post");
        }
        this.post = post;
        this.parent = parent;
        this.depth = (parent == null) ? 0 : parent.depth + 1;
    }

    public void changeContent(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content must not be blank");
        }
        if (content.length() > 1000) {
            throw new IllegalArgumentException("content length must be <= 1000");
        }
        this.content = content;
    }

    public void increaseLike() {
        this.likeCount++;
    }

    public void softDelete() {
        this.status = CommentStatus.DELETED;
    }

}
