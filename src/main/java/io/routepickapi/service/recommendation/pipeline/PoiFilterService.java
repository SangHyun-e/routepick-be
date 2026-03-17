package io.routepickapi.service.recommendation.pipeline;

import io.routepickapi.domain.poi.Poi;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
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
        "교회",
        "성당",
        "편의점",
        "아파트",
        "빌라",
        "부동산",
        "사무실",
        "은행",
        "오피스",
        "의원",
        "치과",
        "상가",
        "회사",
        "공장"
    );

    private static final Map<String, String> VENUE_GROUPS = Map.ofEntries(
        Map.entry("덕수궁", "덕수궁"),
        Map.entry("대한문", "덕수궁"),
        Map.entry("돈덕전", "덕수궁"),
        Map.entry("경복궁", "경복궁"),
        Map.entry("광화문", "경복궁"),
        Map.entry("근정전", "경복궁"),
        Map.entry("창덕궁", "창덕궁"),
        Map.entry("후원", "창덕궁"),
        Map.entry("창경궁", "창경궁"),
        Map.entry("남산", "남산")
    );

    private static final int DEDUP_SCALE = 10000;

    private final PoiThemePolicy poiThemePolicy;

    public List<Poi> filter(List<Poi> pois) {
        return filter(pois, DriveTheme.DEFAULT, null);
    }

    public List<Poi> filter(List<Poi> pois, String requestId) {
        return filter(pois, DriveTheme.DEFAULT, requestId);
    }

    public List<Poi> filter(List<Poi> pois, DriveTheme theme, String requestId) {
        return filterInternal(pois, theme, requestId, false);
    }

    public List<Poi> filterRelaxed(List<Poi> pois, DriveTheme theme, String requestId) {
        return filterInternal(pois, theme, requestId, true);
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

    private List<Poi> applyThemeFilter(List<Poi> pois, DriveTheme theme) {
        if (theme == null || theme == DriveTheme.DEFAULT) {
            return pois;
        }
        return pois.stream()
            .filter(poi -> poiThemePolicy.isAllowed(poi, theme))
            .toList();
    }

    private List<Poi> applyRelaxedThemeFilter(List<Poi> pois, DriveTheme theme) {
        if (theme == null || theme == DriveTheme.DEFAULT) {
            return pois;
        }
        return pois.stream()
            .filter(poi -> poiThemePolicy.isRelaxedAllowed(poi, theme))
            .toList();
    }

    private List<Poi> filterInternal(List<Poi> pois, DriveTheme theme, String requestId, boolean relaxed) {
        if (pois == null || pois.isEmpty()) {
            return List.of();
        }

        List<Poi> themeFiltered = relaxed ? applyRelaxedThemeFilter(pois, theme) : applyThemeFilter(pois, theme);
        int themeRemoved = pois.size() - themeFiltered.size();
        if (theme != null && theme != DriveTheme.DEFAULT && themeRemoved > 0) {
            String label = relaxed ? "Theme filter relaxed" : "Theme filter removed";
            log.info("{} - requestId={}, theme={}, removed={}", label, requestId, theme, themeRemoved);
        }
        List<Poi> candidates = themeFiltered.isEmpty() ? pois : themeFiltered;

        Map<String, Poi> deduplicated = new LinkedHashMap<>();
        Map<String, Integer> removalCounts = new LinkedHashMap<>();
        removalCounts.put("REMOVED_BY_BLACKLIST", 0);
        removalCounts.put("REMOVED_BY_NOT_ALLOWLIST", 0);
        removalCounts.put("REMOVED_BY_DUPLICATE", 0);
        removalCounts.put("REMOVED_BY_NULL", 0);
        removalCounts.put("REMOVED_BY_THEME", pois.size() - candidates.size());
        removalCounts.put("REMOVED_BY_GROUP", 0);
        Set<String> venueGroups = new LinkedHashSet<>();

        for (Poi poi : candidates) {
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

            String venueGroup = resolveVenueGroup(poi);
            if (venueGroup != null && venueGroups.contains(venueGroup)) {
                increment(removalCounts, "REMOVED_BY_GROUP");
                continue;
            }

            String key = buildDedupKey(poi);
            if (deduplicated.containsKey(key)) {
                increment(removalCounts, "REMOVED_BY_DUPLICATE");
                continue;
            }
            if (venueGroup != null) {
                venueGroups.add(venueGroup);
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

    private String buildDedupKey(Poi poi) {
        String name = normalize(poi.name());
        long latKey = Math.round(poi.lat() * DEDUP_SCALE);
        long lngKey = Math.round(poi.lng() * DEDUP_SCALE);
        return name + "|" + latKey + "|" + lngKey;
    }

    private boolean isKakaoSource(Poi poi) {
        return "KAKAO".equalsIgnoreCase(poi.source());
    }

    private String resolveVenueGroup(Poi poi) {
        if (poi == null || poi.name() == null) {
            return null;
        }
        String normalized = normalize(poi.name());
        for (Map.Entry<String, String> entry : VENUE_GROUPS.entrySet()) {
            String keyword = normalize(entry.getKey());
            if (!keyword.isBlank() && normalized.contains(keyword)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.toLowerCase(Locale.ROOT).replace(" ", "");
    }
}
