package io.routepickapi.domain.poi;

import java.time.Duration;
import java.util.Set;

public record Poi(
    String source,
    String externalId,
    String name,
    double lat,
    double lng,
    String type,
    Set<String> tags,
    boolean parking,
    double viewScore,
    double weatherSensitivity,
    Duration stayDuration,
    double driveSuitability
) {

    public Poi {
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("source required");
        }
        if (externalId == null || externalId.isBlank()) {
            throw new IllegalArgumentException("externalId required");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name required");
        }
        if (lat < -90.0 || lat > 90.0) {
            throw new IllegalArgumentException("lat out of range");
        }
        if (lng < -180.0 || lng > 180.0) {
            throw new IllegalArgumentException("lng out of range");
        }
        tags = tags == null ? Set.of() : Set.copyOf(tags);
        stayDuration = stayDuration == null ? Duration.ZERO : stayDuration;
    }

    public boolean hasTag(String tag) {
        if (tag == null || tag.isBlank()) {
            return false;
        }
        return tags.contains(tag);
    }
}
