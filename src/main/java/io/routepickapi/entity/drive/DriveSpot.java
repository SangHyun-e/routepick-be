package io.routepickapi.entity.drive;

import io.routepickapi.common.model.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
    name = "drive_spots",
    indexes = {
        @Index(name = "idx_drive_spots_region", columnList = "region"),
        @Index(name = "idx_drive_spots_active", columnList = "is_active"),
        @Index(name = "idx_drive_spots_lat_lng", columnList = "lat, lng")
    }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DriveSpot extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false)
    private double lat;

    @Column(nullable = false)
    private double lng;

    @Column(nullable = false, length = 50)
    private String region;

    @Column(name = "spot_type", length = 80)
    private String spotType;

    @Column(length = 255)
    private String themes;

    @Column(name = "view_score", nullable = false)
    private double viewScore;

    @Column(name = "drive_suitability", nullable = false)
    private double driveSuitability;

    @Column(name = "stay_minutes", nullable = false)
    private int stayMinutes;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    private DriveSpotSourceType sourceType;

    public DriveSpot(
        String name,
        double lat,
        double lng,
        String region,
        String spotType,
        String themes,
        double viewScore,
        double driveSuitability,
        int stayMinutes,
        boolean isActive,
        DriveSpotSourceType sourceType
    ) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name required");
        }
        if (region == null || region.isBlank()) {
            throw new IllegalArgumentException("region required");
        }
        if (sourceType == null) {
            throw new IllegalArgumentException("sourceType required");
        }
        this.name = name;
        this.lat = lat;
        this.lng = lng;
        this.region = region;
        this.spotType = spotType;
        this.themes = themes;
        this.viewScore = viewScore;
        this.driveSuitability = driveSuitability;
        this.stayMinutes = stayMinutes;
        this.isActive = isActive;
        this.sourceType = sourceType;
    }
}
