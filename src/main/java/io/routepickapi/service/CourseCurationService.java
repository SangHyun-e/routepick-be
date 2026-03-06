package io.routepickapi.service;

import io.routepickapi.common.error.CustomException;
import io.routepickapi.common.error.ErrorType;
import io.routepickapi.dto.course.CourseCurationRequest;
import io.routepickapi.dto.course.CourseCurationResponse;
import io.routepickapi.dto.course.CourseStopRequest;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CourseCurationService {

    private final CruiserLlmClient cruiserLlmClient;
    private final CourseCurationRateLimiter rateLimiter;

    public CourseCurationResponse curate(CourseCurationRequest request, String rateLimitKey) {
        if (request == null) {
            throw new CustomException(ErrorType.COMMON_INVALID_INPUT, "요청값이 비어있습니다.");
        }

        rateLimiter.validate(rateLimitKey);

        String prompt = buildPrompt(request);
        return cruiserLlmClient.requestCuration(prompt)
            .orElseThrow(() -> new CustomException(ErrorType.COMMON_INTERNAL,
                "크루저 큐레이션을 생성하지 못했습니다."));
    }

    private String buildPrompt(CourseCurationRequest request) {
        String stopsDescription = request.stops().stream()
            .map(this::formatStop)
            .collect(Collectors.joining("\n"));

        return """
            Role: 대한민국 최고의 드라이브 코스 큐레이터 '크루저(Cruiser)'
            Persona & Goal:
            - 사용자의 감성, 날씨, 교통 상황을 고려해 최고의 드라이브 경험을 설계하는 전문 에이전트
            - 단순 길 안내가 아니라 그날의 기분과 풍경이 어우러지는 여정을 제안

            Response Guidelines:
            - 감성적 가치 전달과 실용적 팁 포함
            - 실시간 데이터가 없으므로 추정/일반적 표현을 사용
            - JSON 외 텍스트는 출력하지 말 것

            Output Format (JSON):
            {
              "course_title": "코스 이름",
              "vibe_summary": "코스 핵심 감성 한 문장",
              "route_details": {
                "start": "출발지",
                "stopover": "경유지 (이유 포함)",
                "destination": "목적지"
              },
              "drive_info": {
                "duration": "예상 소요 시간",
                "difficulty": "운전 난이도",
                "best_time": "추천 출발 시간"
              },
              "curator_tips": ["주차 팁", "추천 음악", "준비물"]
            }

            Input Data:
            - 출발지: %s
            - 도착지: %s
            - 테마: %s
            - 경로 요약: %s
            - 추천 설명: %s
            - 추천 정차 장소:
            %s
            """.formatted(
            request.origin(),
            request.destination(),
            request.theme(),
            request.routeSummary(),
            request.explanation(),
            stopsDescription
        );
    }

    private String formatStop(CourseStopRequest stop) {
        if (stop == null) {
            return "- (알 수 없음)";
        }

        return String.format("- %s (%s) / %s", stop.name(), stop.category(), stop.address());
    }
}
