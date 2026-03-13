package io.routepickapi.dto.recommendation;

import java.util.Locale;

public record CandidatePlace(
    String id,
    String name,
    String address,
    double x,
    double y,
    String categoryName,
    String categoryGroupCode,
    String categoryGroupName,
    String phone,
    String placeUrl
) {

    public String key() {
        return String.format("%s|%s", normalizedName(), normalizedAddress());
    }

    public String normalizedName() {
        return normalize(name);
    }

    public String normalizedAddress() {
        return normalize(address);
    }

    private String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.toLowerCase(Locale.ROOT).replace(" ", "");
    }
}
