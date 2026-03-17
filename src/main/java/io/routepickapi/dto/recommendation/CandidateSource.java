package io.routepickapi.dto.recommendation;

public enum CandidateSource {
    TOURAPI(3),
    KAKAO(2),
    OVERPASS(1);

    private final int priority;

    CandidateSource(int priority) {
        this.priority = priority;
    }

    public int priority() {
        return priority;
    }
}
