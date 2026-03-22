package io.routepickapi.service.recommendation;

import io.routepickapi.dto.place.KakaoPlaceSearchResponse.KakaoPlaceDocument;
import io.routepickapi.dto.recommendation.CandidatePlace;
import io.routepickapi.dto.recommendation.CandidateSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class KakaoPlaceNormalizer {

    public CandidatePlace normalize(KakaoPlaceDocument document) {
        if (document == null) {
            return null;
        }

        Double x = parseDouble(document.x());
        Double y = parseDouble(document.y());
        if (x == null || y == null) {
            return null;
        }

        String address = resolveAddress(document);
        if (document.placeName() == null || document.placeName().isBlank() || address.isBlank()) {
            return null;
        }

        return new CandidatePlace(
            document.id(),
            document.placeName(),
            address,
            x,
            y,
            document.categoryName(),
            document.categoryGroupCode(),
            document.categoryGroupName(),
            document.phone(),
            document.placeUrl(),
            CandidateSource.KAKAO,
            buildTags(document)
        );
    }

    private String resolveAddress(KakaoPlaceDocument document) {
        if (document == null) {
            return "";
        }

        if (document.roadAddressName() != null && !document.roadAddressName().isBlank()) {
            return document.roadAddressName();
        }

        return document.addressName() == null ? "" : document.addressName();
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

    private List<String> buildTags(KakaoPlaceDocument document) {
        List<String> tags = new ArrayList<>();
        addTag(tags, document.categoryName());
        addTag(tags, document.categoryGroupName());
        addTag(tags, document.categoryGroupCode());
        addTag(tags, "kakao");
        return tags;
    }

    private void addTag(List<String> tags, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        tags.add(normalized);
    }
}
