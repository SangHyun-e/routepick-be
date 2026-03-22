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
    private String title;

    @Column(nullable = false, length = 30)
    private String theme;

    @Column(name = "origin_lat", nullable = false)
    private Double originLat;

    @Column(name = "origin_lng", nullable = false)
    private Double originLng;

    @Column(name = "destination_lat", nullable = false)
    private Double destinationLat;

    @Column(name = "destination_lng", nullable = false)
    private Double destinationLng;

    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes;

    @Column(name = "max_stops", nullable = false)
    private Integer maxStops;

    @Column(name = "total_distance_km", nullable = false)
    private Double totalDistanceKm;

    @Column(name = "total_duration_minutes")
    private Long totalDurationMinutes;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "explain_text", columnDefinition = "TEXT")
    private String explainText;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
        name = "course_recommendation_stops",
        joinColumns = @JoinColumn(name = "course_recommendation_id")
    )
    @OrderColumn(name = "stop_order")
    private List<CourseRecommendationStop> stops = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
        name = "course_recommendation_include_stops",
        joinColumns = @JoinColumn(name = "course_recommendation_id")
    )
    @OrderColumn(name = "include_order")
    private List<CourseRecommendationIncludeStop> includeStops = new ArrayList<>();

    public CourseRecommendationSave(
        User user,
        String title,
        String theme,
        Double originLat,
        Double originLng,
        Double destinationLat,
        Double destinationLng,
        Integer durationMinutes,
        Integer maxStops,
        Double totalDistanceKm,
        Long totalDurationMinutes,
        String description,
        String explainText,
        List<CourseRecommendationStop> stops,
        List<CourseRecommendationIncludeStop> includeStops
    ) {
        if (user == null) {
            throw new IllegalArgumentException("user required");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title required");
        }
        if (theme == null || theme.isBlank()) {
            throw new IllegalArgumentException("theme required");
        }
        if (originLat == null) {
            throw new IllegalArgumentException("originLat required");
        }
        if (originLng == null) {
            throw new IllegalArgumentException("originLng required");
        }
        if (destinationLat == null) {
            throw new IllegalArgumentException("destinationLat required");
        }
        if (destinationLng == null) {
            throw new IllegalArgumentException("destinationLng required");
        }
        if (durationMinutes == null) {
            throw new IllegalArgumentException("durationMinutes required");
        }
        if (maxStops == null) {
            throw new IllegalArgumentException("maxStops required");
        }
        if (totalDistanceKm == null) {
            throw new IllegalArgumentException("totalDistanceKm required");
        }
        if (totalDurationMinutes == null) {
            throw new IllegalArgumentException("totalDurationMinutes required");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("description required");
        }
        this.user = user;
        this.title = title;
        this.theme = theme;
        this.originLat = originLat;
        this.originLng = originLng;
        this.destinationLat = destinationLat;
        this.destinationLng = destinationLng;
        this.durationMinutes = durationMinutes;
        this.maxStops = maxStops;
        this.totalDistanceKm = totalDistanceKm;
        this.totalDurationMinutes = totalDurationMinutes;
        this.description = description;
        this.explainText = explainText;
        if (stops != null) {
            this.stops.addAll(stops);
        }
        if (includeStops != null) {
            this.includeStops.addAll(includeStops);
        }
    }
}
