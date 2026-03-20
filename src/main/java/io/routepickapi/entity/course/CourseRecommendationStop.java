package io.routepickapi.entity.course;

import io.routepickapi.common.jpa.StringListConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CourseRecommendationStop {

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "lat", nullable = false)
    private Double lat;

    @Column(name = "lng", nullable = false)
    private Double lng;

    @Column(name = "type", nullable = false, length = 120)
    private String type;

    @Convert(converter = StringListConverter.class)
    @Column(name = "tags", columnDefinition = "TEXT")
    private List<String> tags = new ArrayList<>();

    @Column(name = "stay_minutes")
    private Long stayMinutes;

    @Column(name = "view_score")
    private Double viewScore;

    @Column(name = "drive_suitability")
    private Double driveSuitability;

    @Column(name = "segment_distance_km")
    private Double segmentDistanceKm;

    @Column(name = "segment_duration_minutes")
    private Long segmentDurationMinutes;

    public CourseRecommendationStop(
        String name,
        Double lat,
        Double lng,
        String type,
        List<String> tags,
        Long stayMinutes,
        Double viewScore,
        Double driveSuitability,
        Double segmentDistanceKm,
        Long segmentDurationMinutes
    ) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name required");
        }
        if (lat == null) {
            throw new IllegalArgumentException("lat required");
        }
        if (lng == null) {
            throw new IllegalArgumentException("lng required");
        }
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("type required");
        }
        this.name = name;
        this.lat = lat;
        this.lng = lng;
        this.type = type;
        if (tags != null) {
            this.tags.addAll(tags);
        }
        this.stayMinutes = stayMinutes;
        this.viewScore = viewScore;
        this.driveSuitability = driveSuitability;
        this.segmentDistanceKm = segmentDistanceKm;
        this.segmentDurationMinutes = segmentDurationMinutes;
    }
}
