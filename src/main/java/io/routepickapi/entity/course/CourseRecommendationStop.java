package io.routepickapi.entity.course;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CourseRecommendationStop {

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "address", nullable = false, length = 255)
    private String address;

    @Column(name = "x", nullable = false)
    private double x;

    @Column(name = "y", nullable = false)
    private double y;

    @Column(name = "category", nullable = false, length = 120)
    private String category;

    public CourseRecommendationStop(String name, String address, double x, double y,
        String category) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name required");
        }
        if (address == null || address.isBlank()) {
            throw new IllegalArgumentException("address required");
        }
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("category required");
        }
        this.name = name;
        this.address = address;
        this.x = x;
        this.y = y;
        this.category = category;
    }
}
