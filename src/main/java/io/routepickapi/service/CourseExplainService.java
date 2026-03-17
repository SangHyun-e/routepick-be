package io.routepickapi.service;

import io.routepickapi.common.error.CustomException;
import io.routepickapi.common.error.ErrorType;
import io.routepickapi.dto.course.CourseExplainContent;
import io.routepickapi.dto.course.CourseExplainResponse;
import io.routepickapi.dto.parking.NearbyParkingItemResponse;
import io.routepickapi.entity.course.CourseRecommendationSave;
import io.routepickapi.entity.course.CourseRecommendationStop;
import io.routepickapi.repository.CourseRecommendationSaveRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private static final int PARKING_STOP_LIMIT = 2;
    private static final int PARKING_ITEM_LIMIT = 3;

    private final CourseRecommendationSaveRepository courseRecommendationSaveRepository;
    private final CourseExplainUsageService usageService;
    private final LlmClient llmClient;
    private final ParkingService parkingService;

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
        List<NearbyParkingItemResponse> parkingItems = loadNearbyParking(course.getStops());
        String parkingHint = buildParkingHint(parkingItems);
        String prompt = buildPrompt(course, parkingItems);
        return llmClient.requestJson(prompt, CourseExplainContent.class)
            .filter(this::isValidContent)
            .map(content -> applyParkingHint(content, parkingHint));
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

    private String buildPrompt(
        CourseRecommendationSave course,
        List<NearbyParkingItemResponse> parkingItems
    ) {
        String theme = normalizeValue(course.getTheme(), "드라이브");
        String duration = formatDuration(course.getTotalDurationMinutes());
        String stopSummary = buildStopSummary(course.getStops());
        String parkingSection = buildParkingSection(parkingItems);

        if (parkingSection.isBlank()) {
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

        return """
            다음 드라이브 코스를 자연스럽게 설명해줘.
            - 한국어
            - 1~2문장
            - 감성적이지만 과장하지 말 것
            - 주차는 참고용으로만 안내할 것 (확정 표현 금지)

            테마: %s
            총 시간: %s
            경유지: %s

            %s

            출력은 아래 JSON 형식으로 작성해줘.
            {"title":"코스 제목","description":"설명","reason":"추천 이유"}
            """.formatted(theme, duration, stopSummary, parkingSection);
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

    private CourseExplainContent applyParkingHint(
        CourseExplainContent content,
        String parkingHint
    ) {
        if (content == null || parkingHint.isBlank()) {
            return content;
        }
        String combinedText = (content.description() + " " + content.reason()).toLowerCase();
        if (combinedText.contains("주차")) {
            return content;
        }
        String reason = content.reason();
        String merged = reason == null || reason.isBlank()
            ? parkingHint
            : reason + " " + parkingHint;
        return new CourseExplainContent(content.title(), content.description(), merged);
    }

    private List<NearbyParkingItemResponse> loadNearbyParking(List<CourseRecommendationStop> stops) {
        if (stops == null || stops.isEmpty()) {
            return List.of();
        }

        Map<String, NearbyParkingItemResponse> collected = new LinkedHashMap<>();

        stops.stream()
            .filter(stop -> stop.getX() != 0 && stop.getY() != 0)
            .limit(PARKING_STOP_LIMIT)
            .forEach(stop -> {
                if (collected.size() >= PARKING_ITEM_LIMIT) {
                    return;
                }
                List<NearbyParkingItemResponse> items =
                    parkingService.findNearby(stop.getY(), stop.getX());
                for (NearbyParkingItemResponse item : items) {
                    if (item == null) {
                        continue;
                    }
                    String key = item.name() + "|" + item.address();
                    collected.putIfAbsent(key, item);
                    if (collected.size() >= PARKING_ITEM_LIMIT) {
                        break;
                    }
                }
            });

        return List.copyOf(collected.values());
    }

    private String buildParkingSection(List<NearbyParkingItemResponse> items) {
        if (items == null || items.isEmpty()) {
            return "";
        }

        String list = items.stream()
            .map(item -> String.format("- %s (%dm)", item.name(), item.distanceMeters()))
            .collect(Collectors.joining("\n"));

        return """
            근처 주차장:
            %s
            """.formatted(list).trim();
    }

    private String buildParkingHint(List<NearbyParkingItemResponse> items) {
        if (items == null || items.isEmpty()) {
            return "";
        }

        String summary = items.stream()
            .limit(2)
            .map(this::formatParkingItem)
            .collect(Collectors.joining(", "));

        if (summary.isBlank()) {
            return "";
        }

        return String.format("근처에 %s 같은 주차장이 있어 참고용으로 살펴볼 수 있어요.", summary);
    }

    private String formatParkingItem(NearbyParkingItemResponse item) {
        if (item == null || item.name() == null || item.name().isBlank()) {
            return "";
        }
        if (item.distanceMeters() > 0) {
            return String.format("%s(약 %dm)", item.name(), item.distanceMeters());
        }
        return item.name();
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
