package io.routepickapi.entity.post;

import io.routepickapi.common.model.BaseEntity;
import io.routepickapi.entity.user.User;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "posts",
    indexes = {
        @Index(name = "idx_posts_created_at", columnList = "created_at"),
        @Index(name = "idx_posts_region_created_at", columnList = "region, created_at"),
        @Index(name = "idx_posts_lat_lon", columnList = "latitude, longitude")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Post extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 4000)
    private String content;

    // 위치 좌표
    private Double latitude;
    private Double longitude;

    // 지역명(ex: "서울 성수동")
    @Setter
    @Column(length = 120)
    private String region;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PostStatus status = PostStatus.ACTIVE;

    // 좋아요 수 카운터
    @Column(nullable = false)
    private int likeCount = 0;

    // 조회수 카운터
    @Column(nullable = false)
    private int viewCount = 0;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "post_tags", joinColumns = @JoinColumn(name = "post_id"))
    @Column(name = "tag", length = 40)
    private List<String> tags = new ArrayList<>();

    // 글 작성자
    @Setter
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "user_id")
    private User author;

    public Post(String title, String content) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title required");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content required");
        }
        this.title = title;
        this.content = content;
    }

    public void setCoordinates(Double latitude, Double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public void setTags(List<String> tags) {
        this.tags.clear();
        if (tags == null) {
            return;
        }
        for (String t : tags) {
            if (t != null && !t.isBlank() && t.length() <= 40) {
                this.tags.add(t);
            }
        }
    }

    public void changeTitle(String title) {
        if (title == null || title.isBlank() || title.length() > 120) {
            throw new IllegalArgumentException("invalid title");
        }
        this.title = title;
    }

    public void changeContent(String content) {
        if (content == null || content.isBlank() || content.length() > 4000) {
            throw new IllegalArgumentException("invalid content");
        }
        this.content = content;
    }

    public void hide() {
        this.status = PostStatus.HIDDEN;
    }

    public void softDelete() {
        this.status = PostStatus.DELETED;
    }

    public void activated() {
        this.status = PostStatus.ACTIVE;
    }

    public void increaseView() {
        this.viewCount++;
    }

    public void increaseLike() {
        this.likeCount++;
    }
}
