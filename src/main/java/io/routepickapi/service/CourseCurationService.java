package io.routepickapi.service;

import io.routepickapi.common.error.CustomException;
import io.routepickapi.common.error.ErrorType;
import io.routepickapi.dto.course.CourseCurationRequest;
import io.routepickapi.dto.course.CourseCurationResponse;
import io.routepickapi.dto.course.CourseStopRequest;
import io.routepickapi.dto.course.CourseStopResponse;
import io.routepickapi.dto.course.CourseTheme;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CourseCurationService {

    private static final int DEFAULT_EXTRA_STOPS = 2;
    private static final int EXTRA_CANDIDATE_LIMIT = 12;

    private final CruiserLlmClient cruiserLlmClient;
    private final CourseCurationRateLimiter rateLimiter;
    private final CourseRecommendationService courseRecommendationService;

    public CourseCurationResponse curate(CourseCurationRequest request, String rateLimitKey) {
        if (request == null) {
            throw new CustomException(ErrorType.COMMON_INVALID_INPUT, "요청값이 비어있습니다.");
        }

        rateLimiter.validate(rateLimitKey);

        CourseTheme theme = CourseTheme.from(request.theme());
        int extraStops = sanitizeExtraStops(request.extraStops());
        List<CourseStopResponse> candidates = loadCandidates(request, theme);
        List<CourseStopResponse> filteredCandidates = filterCandidates(candidates, request.stops());

        String prompt = buildPrompt(request, filteredCandidates, extraStops);
        CourseCurationResponse response = cruiserLlmClient.requestCuration(prompt)
            .orElseThrow(() -> new CustomException(ErrorType.COMMON_INTERNAL,
                "AI 추천 더보기를 생성하지 못했습니다."));

        List<CourseStopResponse> extraStopResults = resolveExtraStops(
            response.extraStops(),
            filteredCandidates,
            extraStops
        );

        return new CourseCurationResponse(
            response.courseTitle(),
            response.vibeSummary(),
            response.routeDetails(),
            response.driveInfo(),
            response.curatorTips(),
            extraStopResults
        );
    }

    private List<CourseStopResponse> loadCandidates(
        CourseCurationRequest request,
        CourseTheme theme
    ) {
        try {
            return courseRecommendationService.recommendCandidates(
                request.origin(),
                request.destination(),
                theme,
                null,
                EXTRA_CANDIDATE_LIMIT
            );
        } catch (CustomException ex) {
            log.warn("AI 추천 더보기 후보 조회 실패: {}", ex.getMessage());
            return List.of();
        }
    }

    private String buildPrompt(
        CourseCurationRequest request,
        List<CourseStopResponse> candidates,
        int extraStops
    ) {
        String stopsDescription = request.stops().stream()
            .map(this::formatStop)
            .collect(Collectors.joining("\n"));
        String candidatesDescription = formatCandidates(candidates);

        return """
            Role: 대한민국 최고의 드라이브 코스 큐레이터 '크루저(Cruiser)'
            Persona & Goal:
            - 사용자의 감성, 날씨, 교통 상황을 고려해 최고의 드라이브 경험을 설계하는 전문 에이전트
            - 단순 길 안내가 아니라 그날의 기분과 풍경이 어우러지는 여정을 제안

            Response Guidelines:
            - 감성적 가치 전달과 실용적 팁 포함
            - 실시간 데이터가 없으므로 추정/일반적 표현을 사용
            - 추가 추천 후보가 없으면 extra_stops는 빈 배열로 응답
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
              "curator_tips": ["주차 팁", "추천 음악", "준비물"],
              "extra_stops": [
                {
                  "name": "추가 추천 장소",
                  "address": "주소",
                  "x": 0.0,
                  "y": 0.0,
                  "category": "카테고리"
                }
              ]
            }

            Input Data:
            - 출발지: %s
            - 도착지: %s
            - 테마: %s
            - 경로 요약: %s
            - 추천 설명: %s
            - 추천 정차 장소:
            %s
            - 추가 추천 후보 (아래 목록에서만 선택, 최대 %d곳):
            %s
            """.formatted(
            request.origin(),
            request.destination(),
            request.theme(),
            request.routeSummary(),
            request.explanation(),
            stopsDescription,
            extraStops,
            candidatesDescription
        );
    }

    private String formatStop(CourseStopRequest stop) {
        if (stop == null) {
            return "- (알 수 없음)";
        }

        return String.format("- %s (%s) / %s", stop.name(), stop.category(), stop.address());
    }

    private String formatCandidates(List<CourseStopResponse> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return "- 후보 없음";
        }

        return candidates.stream()
            .map(this::formatCandidate)
            .collect(Collectors.joining("\n"));
    }

    private String formatCandidate(CourseStopResponse stop) {
        if (stop == null) {
            return "- (알 수 없음)";
        }
        return String.format(
            "- %s | %s | %s | %.6f, %.6f",
            stop.name(),
            stop.category(),
            stop.address(),
            stop.x(),
            stop.y()
        );
    }

    private int sanitizeExtraStops(Integer extraStops) {
        if (extraStops == null) {
            return DEFAULT_EXTRA_STOPS;
        }
        return Math.min(5, Math.max(1, extraStops));
    }

    private List<CourseStopResponse> filterCandidates(
        List<CourseStopResponse> candidates,
        List<CourseStopRequest> baseStops
    ) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        Set<String> baseKeys = baseStops.stream()
            .filter(Objects::nonNull)
            .map(stop -> stopKey(stop.name(), stop.address()))
            .collect(Collectors.toSet());

        return candidates.stream()
            .filter(Objects::nonNull)
            .filter(stop -> !baseKeys.contains(stopKey(stop.name(), stop.address())))
            .toList();
    }

    private List<CourseStopResponse> resolveExtraStops(
        List<CourseStopResponse> fromAi,
        List<CourseStopResponse> candidates,
        int extraStops
    ) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        Map<String, CourseStopResponse> candidateMap = candidates.stream()
            .filter(Objects::nonNull)
            .collect(Collectors.toMap(
                stop -> stopKey(stop.name(), stop.address()),
                stop -> stop,
                (existing, ignored) -> existing,
                LinkedHashMap::new
            ));

        List<CourseStopResponse> resolved = fromAi == null
            ? List.of()
            : fromAi.stream()
                .filter(Objects::nonNull)
                .map(stop -> candidateMap.get(stopKey(stop.name(), stop.address())))
                .filter(Objects::nonNull)
                .distinct()
                .limit(extraStops)
                .toList();

        if (!resolved.isEmpty()) {
            return resolved;
        }

        return candidateMap.values().stream()
            .limit(extraStops)
            .toList();
    }

    private String stopKey(String name, String address) {
        String merged = String.format("%s|%s", name == null ? "" : name, address == null ? "" : address);
        return merged.toLowerCase(Locale.ROOT).replace(" ", "");
    }
}
