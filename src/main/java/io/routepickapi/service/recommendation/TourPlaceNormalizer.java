package io.routepickapi.service.recommendation;

import io.routepickapi.dto.recommendation.CandidatePlace;
import io.routepickapi.dto.recommendation.CandidateSource;
import io.routepickapi.infrastructure.client.tour.dto.TourItem;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class TourPlaceNormalizer {

    public CandidatePlace normalize(TourItem item) {
        if (item == null || item.contentid() == null || item.title() == null) {
            return null;
        }

        Double x = parseDouble(item.mapx());
        Double y = parseDouble(item.mapy());
        if (x == null || y == null) {
            return null;
        }

        String address = buildAddress(item.addr1(), item.addr2());
        if (address.isBlank()) {
            address = "";
        }

        return new CandidatePlace(
            item.contentid(),
            item.title(),
            address,
            x,
            y,
            resolveCategory(item),
            "TOUR",
            "TourAPI",
            "",
            "",
            CandidateSource.TOURAPI,
            buildTags(item)
        );
    }

    private String buildAddress(String addr1, String addr2) {
        String primary = addr1 == null ? "" : addr1.trim();
        String secondary = addr2 == null ? "" : addr2.trim();
        if (primary.isBlank()) {
            return secondary;
        }
        if (secondary.isBlank()) {
            return primary;
        }
        return primary + " " + secondary;
    }

    private String resolveCategory(TourItem item) {
        if (item.cat3() != null && !item.cat3().isBlank()) {
            return item.cat3();
        }
        if (item.cat2() != null && !item.cat2().isBlank()) {
            return item.cat2();
        }
        if (item.cat1() != null && !item.cat1().isBlank()) {
            return item.cat1();
        }
        return item.contenttypeid();
    }

    private List<String> buildTags(TourItem item) {
        List<String> tags = new ArrayList<>();
        addTag(tags, item.cat1());
        addTag(tags, item.cat2());
        addTag(tags, item.cat3());
        addTag(tags, item.contenttypeid());
        addTag(tags, item.title());
        addTag(tags, "tourapi");
        return tags;
    }

    private void addTag(List<String> tags, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        tags.add(normalized);
    }

    private Double parseDouble(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
