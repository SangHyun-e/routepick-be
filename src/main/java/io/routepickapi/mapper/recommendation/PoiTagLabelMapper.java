package io.routepickapi.mapper.recommendation;

import io.routepickapi.domain.poi.Poi;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class PoiTagLabelMapper {

    private static final Map<String, String> TAG_LABELS = Map.ofEntries(
        Map.entry("12", "관광지"),
        Map.entry("14", "문화시설"),
        Map.entry("15", "축제/공연"),
        Map.entry("28", "레포츠"),
        Map.entry("32", "숙박"),
        Map.entry("38", "쇼핑"),
        Map.entry("39", "맛집"),
        Map.entry("a01", "자연"),
        Map.entry("a02", "인문"),
        Map.entry("a03", "레포츠"),
        Map.entry("a04", "쇼핑"),
        Map.entry("a05", "맛집"),
        Map.entry("viewpoint", "전망대"),
        Map.entry("attraction", "명소"),
        Map.entry("peak", "산"),
        Map.entry("mountain", "산"),
        Map.entry("coast", "해안"),
        Map.entry("coastline", "해안"),
        Map.entry("beach", "해변"),
        Map.entry("park", "공원"),
        Map.entry("cafe", "카페"),
        Map.entry("bakery", "베이커리"),
        Map.entry("dessert", "디저트"),
        Map.entry("restaurant", "맛집"),
        Map.entry("food", "맛집"),
        Map.entry("walk", "산책"),
        Map.entry("trail", "산책"),
        Map.entry("lake", "호수"),
        Map.entry("river", "강변"),
        Map.entry("museum", "박물관"),
        Map.entry("exhibit", "전시"),
        Map.entry("market", "시장"),
        Map.entry("shopping", "쇼핑"),
        Map.entry("forest", "숲"),
        Map.entry("valley", "계곡"),
        Map.entry("nature", "자연"),
        Map.entry("scenic", "풍경")
    );

    private static final Set<String> HIDDEN_TAGS = Set.of("osm", "kakao", "tourapi", "tour", "curated");

    public List<String> toDisplayTags(Poi poi) {
        if (poi == null) {
            return List.of();
        }

        LinkedHashSet<String> labels = new LinkedHashSet<>();
        appendLabel(labels, poi.type());
        if (poi.tags() != null) {
            poi.tags().forEach(tag -> appendLabel(labels, tag));
        }

        return labels.stream().limit(5).toList();
    }

    public String toDisplayType(Poi poi) {
        if (poi == null) {
            return null;
        }

        String typeLabel = mapLabel(poi.type());
        if (typeLabel != null) {
            return typeLabel;
        }

        List<String> tags = toDisplayTags(poi);
        return tags.isEmpty() ? null : tags.getFirst();
    }

    private void appendLabel(Set<String> labels, String raw) {
        if (raw == null || raw.isBlank()) {
            return;
        }

        String trimmed = raw.trim();
        String normalized = trimmed.toLowerCase(Locale.ROOT);

        if (HIDDEN_TAGS.contains(normalized)) {
            return;
        }

        String mapped = TAG_LABELS.get(normalized);
        if (mapped != null && !mapped.isBlank()) {
            labels.add(mapped);
            return;
        }

        if (containsKorean(trimmed)) {
            labels.add(trimmed);
        }
    }

    private boolean containsKorean(String value) {
        return value.chars().anyMatch(ch -> Character.UnicodeBlock.of(ch) == Character.UnicodeBlock.HANGUL_SYLLABLES);
    }

    private String mapLabel(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if (HIDDEN_TAGS.contains(normalized)) {
            return null;
        }
        String mapped = TAG_LABELS.get(normalized);
        if (mapped != null && !mapped.isBlank()) {
            return mapped;
        }
        return containsKorean(raw) ? raw.trim() : null;
    }
}
