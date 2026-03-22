package io.routepickapi.service.recommendation;

import io.routepickapi.dto.recommendation.CandidatePlace;
import io.routepickapi.dto.recommendation.CandidateSource;
import io.routepickapi.infrastructure.client.overpass.dto.OverpassElement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class OverpassPlaceNormalizer {

    private static final Set<String> ALLOWED_TOURISM = Set.of("viewpoint", "attraction");
    private static final Set<String> ALLOWED_NATURAL = Set.of(
        "peak",
        "beach",
        "coastline",
        "bay",
        "wood",
        "cliff",
        "ridge",
        "water",
        "waterfall"
    );

    public CandidatePlace normalize(OverpassElement element) {
        if (element == null || element.tags() == null || element.tags().isEmpty()) {
            return null;
        }

        Map<String, String> tags = element.tags();
        if (!isEligible(tags)) {
            return null;
        }

        String name = resolveName(tags);
        if (name.isBlank()) {
            return null;
        }

        Double x = element.lon();
        Double y = element.lat();
        if (x == null || y == null) {
            return null;
        }

        return new CandidatePlace(
            String.valueOf(element.id()),
            name,
            "",
            x,
            y,
            resolveCategory(tags),
            "OSM",
            "Overpass",
            "",
            "",
            CandidateSource.OVERPASS,
            buildTags(tags)
        );
    }

    private boolean isEligible(Map<String, String> tags) {
        if (tags.size() < 2) {
            return false;
        }
        if (matches(tags, "leisure", "park")) {
            return false;
        }
        if (matches(tags, "amenity", "parking")) {
            return false;
        }
        boolean tourismOk = matchesAny(tags, "tourism", ALLOWED_TOURISM);
        boolean naturalOk = matchesAny(tags, "natural", ALLOWED_NATURAL);
        boolean lookoutOk = matches(tags, "man_made", "lookout") || matches(tags, "tourism", "viewpoint");
        boolean scenicOk = matches(tags, "scenic", "yes");
        return tourismOk || naturalOk || lookoutOk || scenicOk;
    }

    private boolean matches(Map<String, String> tags, String key, String value) {
        if (key == null || value == null) {
            return false;
        }
        String raw = tags.get(key);
        if (raw == null) {
            return false;
        }
        return raw.trim().equalsIgnoreCase(value.trim());
    }

    private boolean matchesAny(Map<String, String> tags, String key, Set<String> allowed) {
        if (allowed == null || allowed.isEmpty()) {
            return false;
        }
        String raw = tags.get(key);
        if (raw == null) {
            return false;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return allowed.contains(normalized);
    }

    private String resolveName(Map<String, String> tags) {
        String name = firstNonBlank(tags.get("name"), tags.get("name:ko"), tags.get("name:en"));
        if (name != null) {
            return name;
        }

        String tourism = tags.get("tourism");
        if (tourism != null && tourism.equalsIgnoreCase("viewpoint")) {
            return "전망 포인트";
        }

        String natural = tags.get("natural");
        if (natural != null) {
            return "자연 포인트";
        }

        if (matches(tags, "man_made", "lookout")) {
            return "전망 포인트";
        }

        return "";
    }

    private String resolveCategory(Map<String, String> tags) {
        String tourism = tags.get("tourism");
        if (tourism != null && !tourism.isBlank()) {
            return tourism;
        }
        String natural = tags.get("natural");
        if (natural != null && !natural.isBlank()) {
            return natural;
        }
        String manMade = tags.get("man_made");
        if (manMade != null && !manMade.isBlank()) {
            return manMade;
        }
        return "scenic";
    }

    private List<String> buildTags(Map<String, String> tags) {
        List<String> result = new ArrayList<>();
        addTag(result, tags.get("tourism"));
        addTag(result, tags.get("natural"));
        addTag(result, tags.get("man_made"));
        addTag(result, tags.get("scenic"));
        addTag(result, tags.get("name"));
        addTag(result, "overpass");
        return result;
    }

    private void addTag(List<String> tags, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        tags.add(value.trim().toLowerCase(Locale.ROOT));
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}
