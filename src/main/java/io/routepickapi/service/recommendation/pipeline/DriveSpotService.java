package io.routepickapi.service.recommendation.pipeline;

import io.routepickapi.domain.poi.Poi;
import io.routepickapi.entity.drive.DriveSpot;
import io.routepickapi.repository.DriveSpotRepository;
import java.time.Duration;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DriveSpotService {

    private static final int DEFAULT_STAY_MINUTES = 40;
    private static final double DEFAULT_WEATHER_SENSITIVITY = 0.2;
    private static final String CURATED_SOURCE = "CURATED";

    private final DriveSpotRepository driveSpotRepository;

    public List<Poi> collectActiveSpots() {
        List<DriveSpot> spots = driveSpotRepository.findByIsActiveTrue();
        if (spots == null || spots.isEmpty()) {
            return List.of();
        }
        return spots.stream()
            .sorted(Comparator.comparingDouble(this::baseScore).reversed())
            .map(this::toPoi)
            .toList();
    }

    private double baseScore(DriveSpot spot) {
        return clampScore(spot.getViewScore()) + clampScore(spot.getDriveSuitability());
    }

    private Poi toPoi(DriveSpot spot) {
        String externalId = spot.getId() == null ? spot.getName() : String.valueOf(spot.getId());
        Set<String> tags = new HashSet<>();
        appendTag(tags, spot.getSpotType());
        appendThemes(tags, spot.getThemes());
        tags.add("curated");

        return new Poi(
            CURATED_SOURCE,
            externalId,
            spot.getName(),
            spot.getLat(),
            spot.getLng(),
            spot.getSpotType(),
            tags,
            false,
            clampScore(spot.getViewScore()),
            DEFAULT_WEATHER_SENSITIVITY,
            Duration.ofMinutes(resolveStayMinutes(spot.getStayMinutes())),
            clampScore(spot.getDriveSuitability())
        );
    }

    private int resolveStayMinutes(int stayMinutes) {
        return stayMinutes > 0 ? stayMinutes : DEFAULT_STAY_MINUTES;
    }

    private void appendThemes(Set<String> tags, String themes) {
        if (themes == null || themes.isBlank()) {
            return;
        }
        Arrays.stream(themes.split("[,/|;]"))
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .forEach(value -> appendTag(tags, value));
    }

    private void appendTag(Set<String> tags, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        tags.add(value.trim().toLowerCase(Locale.ROOT));
    }

    private double clampScore(double score) {
        return Math.max(0.0, Math.min(1.0, score));
    }

    public DriveSpotStats fetchStats(String themeKey) {
        long total = driveSpotRepository.count();
        long active = driveSpotRepository.countByIsActiveTrue();
        long activeTheme = themeKey == null || themeKey.isBlank()
            ? 0
            : driveSpotRepository.countByIsActiveTrueAndThemesContainingIgnoreCase(themeKey);
        return new DriveSpotStats(total, active, activeTheme);
    }

    public record DriveSpotStats(long total, long active, long activeTheme) {
    }
}
