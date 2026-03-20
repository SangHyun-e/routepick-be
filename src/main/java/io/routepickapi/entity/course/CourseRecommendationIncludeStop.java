package io.routepickapi.entity.course;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CourseRecommendationIncludeStop {

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "lat", nullable = false)
    private Double lat;

    @Column(name = "lng", nullable = false)
    private Double lng;

    public CourseRecommendationIncludeStop(String name, Double lat, Double lng) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name required");
        }
        if (lat == null) {
            throw new IllegalArgumentException("lat required");
        }
        if (lng == null) {
            throw new IllegalArgumentException("lng required");
        }
        this.name = name;
        this.lat = lat;
        this.lng = lng;
    }
}
