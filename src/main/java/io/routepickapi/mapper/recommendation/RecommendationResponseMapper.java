package io.routepickapi.mapper.recommendation;

import io.routepickapi.dto.recommendation.CourseSummaryResponse;
import io.routepickapi.dto.recommendation.RecommendationResponse;
import io.routepickapi.dto.recommendation.RecommendedStopResponse;
import io.routepickapi.service.recommendation.pipeline.DriveCourseResult;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RecommendationResponseMapper {

    private final CourseSummaryResponseMapper courseSummaryResponseMapper;
    private final RecommendedStopResponseMapper recommendedStopResponseMapper;

    public RecommendationResponse map(DriveCourseResult result) {
        if (result == null) {
            return new RecommendationResponse(null, 0, 0, null, List.of(), List.of(), null);
        }

        List<CourseSummaryResponse> summaries = result.courses() == null
            ? List.of()
            : result.courses().stream().map(courseSummaryResponseMapper::map).toList();

        List<RecommendedStopResponse> recommendedStops = result.recommendedStops() == null
            ? List.of()
            : result.recommendedStops().stream().map(recommendedStopResponseMapper::map).toList();

        return new RecommendationResponse(
            result.requestId(),
            result.originLat(),
            result.originLng(),
            result.departureTime(),
            summaries,
            recommendedStops,
            result.generatedAt()
        );
    }
}
