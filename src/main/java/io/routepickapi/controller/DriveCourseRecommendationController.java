package io.routepickapi.controller;

import io.routepickapi.common.error.ApiErrorResponse;
import io.routepickapi.dto.recommendation.RecommendationRequest;
import io.routepickapi.dto.recommendation.RecommendationResponse;
import io.routepickapi.mapper.recommendation.RecommendationResponseMapper;
import io.routepickapi.service.recommendation.pipeline.DriveCourseCommand;
import io.routepickapi.service.recommendation.pipeline.DriveCourseResult;
import io.routepickapi.service.recommendation.pipeline.RecommendationFacade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springdoc.core.annotations.ParameterObject;

/**
 * 드라이브 코스 추천 API Controller.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/recommendations")
public class DriveCourseRecommendationController {

    private final RecommendationFacade recommendationFacade;
    private final RecommendationResponseMapper recommendationResponseMapper;

    /**
     * 출발지 기준 드라이브 코스를 추천한다.
     */
    @Operation(
        summary = "드라이브 코스 추천",
        description = "출발지 좌표를 기준으로 2~4개의 정차 지점을 포함한 드라이브 코스를 추천합니다."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "추천 코스 응답",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = RecommendationResponse.class),
                examples = @ExampleObject(
                    name = "추천 응답 예시",
                    value = """
                        {
                          \"requestId\": \"req-20241001-001\",
                          \"originLat\": 37.5665,
                          \"originLng\": 126.978,
                          \"departureTime\": \"2024-10-01T09:30:00\",
                          \"generatedAt\": \"2024-10-01T09:29:58\",
                          \"courses\": [
                            {
                              \"courseId\": null,
                              \"region\": \"서울특별시 중구\",
                              \"theme\": \"coastal\",
                              \"totalDistanceKm\": 78.4,
                              \"totalDurationMinutes\": 210,
                              \"totalScore\": 72.0,
                              \"scoreBreakdown\": {
                                \"sceneryScore\": 24.0,
                                \"driveScore\": 20.0,
                                \"diversityScore\": 11.0,
                                \"routeSmoothnessScore\": 12.0,
                                \"weatherScore\": 10.0,
                                \"penaltyScore\": 5.0,
                                \"totalScore\": 72.0,
                                \"penaltyReasons\": [\"too-long\"]
                              },
                              \"stops\": [
                                {
                                  \"order\": 0,
                                  \"name\": \"북한산 전망대\",
                                  \"lat\": 37.658,
                                  \"lng\": 126.974,
                                  \"type\": \"전망대\",
                                  \"tags\": [\"전망대\", \"mountain\", \"osm\"],
                                  \"stayMinutes\": 60,
                                  \"viewScore\": 0.9,
                                  \"driveSuitability\": 0.7,
                                  \"segmentDistanceKm\": 0.0,
                                  \"segmentDurationMinutes\": 0
                                },
                                {
                                  \"order\": 1,
                                  \"name\": \"한강 카페거리\",
                                  \"lat\": 37.545,
                                  \"lng\": 126.93,
                                  \"type\": \"카페\",
                                  \"tags\": [\"카페\", \"kakao\"],
                                  \"stayMinutes\": 40,
                                  \"viewScore\": 0.6,
                                  \"driveSuitability\": 0.5,
                                  \"segmentDistanceKm\": 18.5,
                                  \"segmentDurationMinutes\": 35
                                }
                              ]
                            }
                          ]
                        }
                        """
                )
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "요청 파라미터 검증 실패",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ApiErrorResponse.class),
                examples = @ExampleObject(
                    name = "검증 오류 예시",
                    value = """
                        {
                          \"timestamp\": \"2024-10-01T09:30:00\",
                          \"status\": 400,
                          \"code\": \"CMN-001\",
                          \"message\": \"유효하지 않은 입력입니다.\",
                          \"path\": \"/api/recommendations/drive-courses\",
                          \"requestId\": \"req-20241001-001\",
                          \"errors\": [
                            {
                              \"field\": \"originLat\",
                              \"reason\": \"originLat는 -90 이상이어야 합니다.\",
                              \"rejectedValue\": -120
                            }
                          ]
                        }
                        """
                )
            )
        ),
        @ApiResponse(
            responseCode = "500",
            description = "내부 처리 실패",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ApiErrorResponse.class),
                examples = @ExampleObject(
                    name = "내부 오류 예시",
                    value = """
                        {
                          \"timestamp\": \"2024-10-01T09:30:00\",
                          \"status\": 500,
                          \"code\": \"CMN-500\",
                          \"message\": \"서버 오류가 발생했습니다.\",
                          \"path\": \"/api/recommendations/drive-courses\",
                          \"requestId\": \"req-20241001-001\"
                        }
                        """
                )
            )
        )
    })
    @GetMapping("/drive-courses")
    public RecommendationResponse recommendDriveCourses(
        @Valid @ParameterObject @ModelAttribute RecommendationRequest request
    ) {
        DriveCourseCommand facadeRequest = new DriveCourseCommand(
            null,
            request.originLat(),
            request.originLng(),
            request.theme(),
            request.durationMinutes(),
            request.maxStops(),
            request.departureTime(),
            request.weatherAware()
        );

        DriveCourseResult result = recommendationFacade.recommend(facadeRequest);
        return recommendationResponseMapper.map(result);
    }
}
