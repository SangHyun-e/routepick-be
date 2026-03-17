package io.routepickapi.service.recommendation.pipeline;

import io.routepickapi.domain.poi.Poi;
import io.routepickapi.dto.place.KakaoPlaceSearchResponse.KakaoPlaceDocument;
import io.routepickapi.infrastructure.client.tour.dto.TourItem;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PoiNormalizationService {

    private static final double DEFAULT_VIEW_SCORE = 0.4;
    private static final double DEFAULT_DRIVE_SCORE = 0.4;

    public List<Poi> normalize(RawPoiBundle bundle) {
        if (bundle == null) {
            return List.of();
        }

        List<Poi> results = new ArrayList<>();
        results.addAll(normalizeKakao(bundle.kakaoPlaces()));
        results.addAll(normalizeTour(bundle.tourItems()));

        log.info("POI 정규화 완료 - count={}", results.size());
        return results;
    }

    private List<Poi> normalizeKakao(List<KakaoPlaceDocument> documents) {
        if (documents == null) {
            return List.of();
        }

        List<Poi> results = new ArrayList<>();
        for (KakaoPlaceDocument document : documents) {
            if (document == null || document.id() == null || document.placeName() == null) {
                continue;
            }

            Double lat = parseDouble(document.y());
            Double lng = parseDouble(document.x());
            if (lat == null || lng == null) {
                continue;
            }

            Set<String> tags = new HashSet<>();
            tags.addAll(splitTags(document.categoryName()));
            tags.addAll(splitTags(document.categoryGroupName()));
            tags.add("kakao");

            Duration stayDuration = resolveStayDuration(tags);
            double viewScore = resolveViewScore(tags);
            double driveScore = resolveDriveScore(tags);

            results.add(new Poi(
                "KAKAO",
                document.id(),
                document.placeName(),
                lat,
                lng,
                document.categoryGroupName(),
                tags,
                false,
                viewScore,
                0.3,
                stayDuration,
                driveScore
            ));
        }

        return results;
    }

    private List<Poi> normalizeTour(List<TourItem> items) {
        if (items == null) {
            return List.of();
        }

        List<Poi> results = new ArrayList<>();
        for (TourItem item : items) {
            if (item == null || item.contentid() == null || item.title() == null) {
                continue;
            }

            Double lat = parseDouble(item.mapy());
            Double lng = parseDouble(item.mapx());
            if (lat == null || lng == null) {
                continue;
            }

            Set<String> tags = new HashSet<>();
            tags.addAll(splitTags(item.cat1()));
            tags.addAll(splitTags(item.cat2()));
            tags.addAll(splitTags(item.cat3()));
            tags.add("tourapi");

            Duration stayDuration = resolveStayDuration(tags);
            double viewScore = resolveViewScore(tags) + 0.1;
            double driveScore = resolveDriveScore(tags) + 0.1;

            results.add(new Poi(
                "TOURAPI",
                item.contentid(),
                item.title(),
                lat,
                lng,
                item.contenttypeid(),
                tags,
                false,
                clampScore(viewScore),
                0.2,
                stayDuration,
                clampScore(driveScore)
            ));
        }

        return results;
    }

    private Set<String> splitTags(String raw) {
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }

        String normalized = raw.replace(">", ",");
        String[] parts = normalized.split(",");
        Set<String> tags = new HashSet<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isBlank()) {
                tags.add(trimmed);
            }
        }

        return tags;
    }

    private Duration resolveStayDuration(Set<String> tags) {
        if (containsTag(tags, "카페", "cafe")) {
            return Duration.ofMinutes(40);
        }
        if (containsTag(tags, "전망", "viewpoint", "peak", "mountain", "산")) {
            return Duration.ofMinutes(60);
        }
        if (containsTag(tags, "해변", "해안", "beach", "coast")) {
            return Duration.ofMinutes(50);
        }
        return Duration.ofMinutes(35);
    }

    private double resolveViewScore(Set<String> tags) {
        double score = DEFAULT_VIEW_SCORE;
        if (containsTag(tags, "전망", "viewpoint")) {
            score += 0.4;
        }
        if (containsTag(tags, "해변", "해안", "beach", "coast")) {
            score += 0.3;
        }
        if (containsTag(tags, "산", "peak", "mountain")) {
            score += 0.3;
        }
        if (containsTag(tags, "공원", "park")) {
            score += 0.2;
        }
        return clampScore(score);
    }

    private double resolveDriveScore(Set<String> tags) {
        double score = DEFAULT_DRIVE_SCORE;
        if (containsTag(tags, "드라이브", "drive")) {
            score += 0.3;
        }
        if (containsTag(tags, "와인딩", "winding")) {
            score += 0.2;
        }
        if (containsTag(tags, "호수", "lake")) {
            score += 0.15;
        }
        return clampScore(score);
    }

    private boolean containsTag(Set<String> tags, String... keywords) {
        if (tags == null || tags.isEmpty() || keywords == null) {
            return false;
        }

        for (String keyword : keywords) {
            if (keyword == null) {
                continue;
            }
            String normalized = keyword.toLowerCase(Locale.ROOT);
            boolean matched = tags.stream()
                .filter(Objects::nonNull)
                .map(tag -> tag.toLowerCase(Locale.ROOT))
                .anyMatch(tag -> tag.contains(normalized));
            if (matched) {
                return true;
            }
        }

        return false;
    }

    private Double parseDouble(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private double clampScore(double score) {
        return Math.min(1.0, Math.max(0.0, score));
    }
}
