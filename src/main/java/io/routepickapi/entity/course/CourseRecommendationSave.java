package io.routepickapi.entity.course;

import io.routepickapi.common.model.BaseTimeEntity;
import io.routepickapi.entity.user.User;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
    name = "course_recommendation_saves",
    indexes = {
        @Index(name = "idx_course_recommendation_saves_user", columnList = "user_id"),
        @Index(name = "idx_course_recommendation_saves_created_at", columnList = "created_at")
    }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CourseRecommendationSave extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 120)
    private String origin;

    @Column(nullable = false, length = 120)
    private String destination;

    @Column(nullable = false, length = 20)
    private String theme;

    @Column(name = "total_duration_minutes")
    private Long totalDurationMinutes;

    @Column(name = "route_summary", nullable = false, length = 500)
    private String routeSummary;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String explanation;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
        name = "course_recommendation_stops",
        joinColumns = @JoinColumn(name = "course_recommendation_id")
    )
    @OrderColumn(name = "stop_order")
    private List<CourseRecommendationStop> stops = new ArrayList<>();

    public CourseRecommendationSave(
        User user,
        String origin,
        String destination,
        String theme,
        Long totalDurationMinutes,
        String routeSummary,
        String explanation,
        List<CourseRecommendationStop> stops
    ) {
        if (user == null) {
            throw new IllegalArgumentException("user required");
        }
        if (origin == null || origin.isBlank()) {
            throw new IllegalArgumentException("origin required");
        }
        if (destination == null || destination.isBlank()) {
            throw new IllegalArgumentException("destination required");
        }
        if (theme == null || theme.isBlank()) {
            throw new IllegalArgumentException("theme required");
        }
        if (routeSummary == null || routeSummary.isBlank()) {
            throw new IllegalArgumentException("routeSummary required");
        }
        if (explanation == null || explanation.isBlank()) {
            throw new IllegalArgumentException("explanation required");
        }
        this.user = user;
        this.origin = origin;
        this.destination = destination;
        this.theme = theme;
        this.totalDurationMinutes = totalDurationMinutes;
        this.routeSummary = routeSummary;
        this.explanation = explanation;
        if (stops != null) {
            this.stops.addAll(stops);
        }
    }
}
