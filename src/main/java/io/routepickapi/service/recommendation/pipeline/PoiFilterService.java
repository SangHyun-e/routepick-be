package io.routepickapi.service.recommendation.pipeline;

import io.routepickapi.domain.poi.Poi;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PoiFilterService {

    private static final Set<String> ALLOWLIST = Set.of(
        "전망대",
        "해안",
        "해변",
        "산",
        "자연",
        "호수",
        "공원",
        "국립공원",
        "드라이브",
        "카페",
        "뷰카페",
        "계곡",
        "휴게소",
        "관광지",
        "자연휴양림"
    );

    private static final Set<String> BLACKLIST = Set.of(
        "마트",
        "학원",
        "약국",
        "병원",
        "편의점",
        "부동산",
        "사무실",
        "은행",
        "오피스",
        "의원",
        "치과",
        "상가"
    );

    private static final int DEDUP_SCALE = 10000;

    public List<Poi> filter(List<Poi> pois) {
        return filter(pois, null);
    }

    public List<Poi> filter(List<Poi> pois, String requestId) {
        if (pois == null || pois.isEmpty()) {
            return List.of();
        }

        Map<String, Poi> deduplicated = new LinkedHashMap<>();
        Map<String, Integer> removalCounts = new LinkedHashMap<>();
        removalCounts.put("REMOVED_BY_BLACKLIST", 0);
        removalCounts.put("REMOVED_BY_NOT_ALLOWLIST", 0);
        removalCounts.put("REMOVED_BY_DUPLICATE", 0);
        removalCounts.put("REMOVED_BY_NULL", 0);
        for (Poi poi : pois) {
            if (poi == null) {
                increment(removalCounts, "REMOVED_BY_NULL");
                continue;
            }

            if (isBlacklisted(poi)) {
                increment(removalCounts, "REMOVED_BY_BLACKLIST");
                continue;
            }

            if (isKakaoSource(poi) && !isAllowlisted(poi)) {
                increment(removalCounts, "REMOVED_BY_NOT_ALLOWLIST");
                continue;
            }

            String key = buildDedupKey(poi);
            if (deduplicated.containsKey(key)) {
                increment(removalCounts, "REMOVED_BY_DUPLICATE");
                continue;
            }
            deduplicated.put(key, poi);
        }

        List<Poi> results = List.copyOf(deduplicated.values());
        log.info(
            "POI 필터링 결과 - requestId={}, kept={}, removed={}",
            requestId,
            results.size(),
            removalCounts
        );
        return results;
    }

    private void increment(Map<String, Integer> counts, String key) {
        counts.put(key, counts.getOrDefault(key, 0) + 1);
    }

    private boolean isBlacklisted(Poi poi) {
        return matchesKeywords(poi, BLACKLIST);
    }

    private boolean isAllowlisted(Poi poi) {
        return matchesKeywords(poi, ALLOWLIST);
    }

    private boolean matchesKeywords(Poi poi, Set<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return false;
        }

        String name = normalize(poi.name());
        String type = normalize(poi.type());
        Set<String> tags = new LinkedHashSet<>();
        if (poi.tags() != null) {
            poi.tags().stream()
                .filter(value -> value != null && !value.isBlank())
                .map(this::normalize)
                .forEach(tags::add);
        }

        for (String keyword : keywords) {
            String normalizedKeyword = normalize(keyword);
            if (normalizedKeyword.isBlank()) {
                continue;
            }
            if (name.contains(normalizedKeyword) || type.contains(normalizedKeyword)) {
                return true;
            }
            boolean tagMatched = tags.stream().anyMatch(tag -> tag.contains(normalizedKeyword));
            if (tagMatched) {
                return true;
            }
        }

        return false;
    }

    private String buildDedupKey(Poi poi) {
        String name = normalize(poi.name());
        long latKey = Math.round(poi.lat() * DEDUP_SCALE);
        long lngKey = Math.round(poi.lng() * DEDUP_SCALE);
        return name + "|" + latKey + "|" + lngKey;
    }

    private boolean isKakaoSource(Poi poi) {
        return "KAKAO".equalsIgnoreCase(poi.source());
    }

    private String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.toLowerCase(Locale.ROOT).replace(" ", "");
    }
}
