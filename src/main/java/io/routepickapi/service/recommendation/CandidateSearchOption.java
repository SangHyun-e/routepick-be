package io.routepickapi.service.recommendation;

public record CandidateSearchOption(
    int searchRadiusMeters,
    boolean applyStopTypeFilter
) {

    public static final int DEFAULT_SEARCH_RADIUS_METERS = 4000;

    public static CandidateSearchOption defaultOption() {
        return new CandidateSearchOption(DEFAULT_SEARCH_RADIUS_METERS, true);
    }

    public CandidateSearchOption withSearchRadiusMeters(int radiusMeters) {
        return new CandidateSearchOption(radiusMeters, applyStopTypeFilter);
    }

    public CandidateSearchOption withStopTypeFilter(boolean enabled) {
        return new CandidateSearchOption(searchRadiusMeters, enabled);
    }
}
