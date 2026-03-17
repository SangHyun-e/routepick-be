package io.routepickapi.dto.recommendation;

import java.util.List;

public record CourseCandidate(
    String courseId,
    List<CandidatePlace> stops,
    double totalDistanceKm,
    int estimatedMinutes,
    double score,
    ScoreDetail scoreDetail,
    List<String> warnings
) {

    public CourseCandidate withScore(double score, ScoreDetail scoreDetail) {
        return new CourseCandidate(
            courseId,
            stops,
            totalDistanceKm,
            estimatedMinutes,
            score,
            scoreDetail,
            warnings
        );
    }

    public CourseCandidate withWarnings(List<String> warnings) {
        return new CourseCandidate(
            courseId,
            stops,
            totalDistanceKm,
            estimatedMinutes,
            score,
            scoreDetail,
            warnings
        );
    }

    public CourseCandidate withRouteMetrics(double totalDistanceKm, int estimatedMinutes) {
        return new CourseCandidate(
            courseId,
            stops,
            totalDistanceKm,
            estimatedMinutes,
            score,
            scoreDetail,
            warnings
        );
    }
}
