package io.routepickapi.service;

import io.routepickapi.common.error.CustomException;
import io.routepickapi.common.error.ErrorType;
import io.routepickapi.dto.course.CourseExplainContent;
import io.routepickapi.dto.course.CourseExplainResponse;
import io.routepickapi.entity.course.CourseRecommendationSave;
import io.routepickapi.entity.course.CourseRecommendationStop;
import io.routepickapi.repository.CourseRecommendationSaveRepository;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CourseExplainService {

    private static final String FALLBACK_MESSAGE = "드라이브에 어울리는 코스를 준비했어요.";
    private static final int STOP_LIMIT = 3;

    private final CourseRecommendationSaveRepository courseRecommendationSaveRepository;
    private final CourseExplainUsageService usageService;
    private final LlmClient llmClient;

    public CourseExplainResponse explain(long courseId, long userId) {
        CourseRecommendationSave course = courseRecommendationSaveRepository
            .findByIdAndUserId(courseId, userId)
            .orElseThrow(() -> new CustomException(ErrorType.COMMON_NOT_FOUND, "코스를 찾을 수 없습니다."));

        int remaining = usageService.consume(userId);
        CourseExplainContent content = requestExplain(course)
            .orElseGet(() -> fallbackContent(course));

        return new CourseExplainResponse(
            content.title(),
            content.description(),
            content.reason(),
            remaining
        );
    }

    private Optional<CourseExplainContent> requestExplain(CourseRecommendationSave course) {
        String prompt = buildPrompt(course);
        return llmClient.requestJson(prompt, CourseExplainContent.class)
            .filter(this::isValidContent);
    }

    private boolean isValidContent(CourseExplainContent content) {
        return content != null
            && content.title() != null
            && !content.title().isBlank()
            && content.description() != null
            && !content.description().isBlank()
            && content.reason() != null
            && !content.reason().isBlank();
    }

    private String buildPrompt(CourseRecommendationSave course) {
        String theme = normalizeValue(course.getTheme(), "드라이브");
        String duration = formatDuration(course.getTotalDurationMinutes());
        String stopSummary = buildStopSummary(course.getStops());

        return """
            다음 드라이브 코스를 자연스럽게 설명해줘.
            - 한국어
            - 1~2문장
            - 감성적이지만 과장하지 말 것

            테마: %s
            총 시간: %s
            경유지: %s

            출력은 아래 JSON 형식으로 작성해줘.
            {"title":"코스 제목","description":"설명","reason":"추천 이유"}
            """.formatted(theme, duration, stopSummary);
    }

    private String buildStopSummary(List<CourseRecommendationStop> stops) {
        if (stops == null || stops.isEmpty()) {
            return "경유지 정보 없음";
        }

        String summary = stops.stream()
            .map(CourseRecommendationStop::getName)
            .filter(name -> name != null && !name.isBlank())
            .limit(STOP_LIMIT)
            .collect(Collectors.joining(", "));

        return summary.isBlank() ? "경유지 정보 없음" : summary;
    }

    private CourseExplainContent fallbackContent(CourseRecommendationSave course) {
        String theme = normalizeValue(course.getTheme(), "드라이브");
        String title = theme + "에 어울리는 추천 코스";
        return new CourseExplainContent(title, FALLBACK_MESSAGE, FALLBACK_MESSAGE);
    }

    private String formatDuration(Long minutes) {
        if (minutes == null || minutes <= 0) {
            return "알 수 없음";
        }
        return minutes + "분";
    }

    private String normalizeValue(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }
}
