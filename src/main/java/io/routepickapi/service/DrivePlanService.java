package io.routepickapi.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.routepickapi.common.error.CustomException;
import io.routepickapi.common.error.ErrorType;
import io.routepickapi.dto.drive.DrivePlanLlmResponse;
import io.routepickapi.dto.drive.DrivePlanLlmResponse.DrivePlanLlmStop;
import io.routepickapi.dto.drive.DrivePlanRequest;
import io.routepickapi.dto.drive.DrivePlanResponse;
import io.routepickapi.dto.drive.DrivePlanStopResponse;
import io.routepickapi.dto.place.KakaoPlaceSearchResponse;
import io.routepickapi.dto.place.KakaoPlaceSearchResponse.KakaoPlaceDocument;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DrivePlanService {

    private static final int FIRST_PAGE_SIZE = 15;
    private static final int SECOND_PAGE_SIZE = 5;
    private static final int LLM_CANDIDATE_LIMIT = 15;
    private static final int FALLBACK_STOP_LIMIT = 5;
    private static final String DEFAULT_PLAN_REASON = "후보 장소 중 상위 결과로 기본 코스를 구성했습니다.";
    private static final String DEFAULT_STOP_REASON = "추천 후보 중 상위 장소입니다.";
    private static final List<String> OPEN_NOW_KEYWORDS = List.of(
        "카페",
        "커피",
        "식당",
        "음식",
        "맛집",
        "레스토랑",
        "베이커리",
        "빵",
        "전망",
        "휴게소"
    );

    private final KakaoLocalService kakaoLocalService;
    private final DrivePlanLlmClient drivePlanLlmClient;
    private final ObjectMapper objectMapper;

    public DrivePlanResponse plan(DrivePlanRequest request) {
        if (request == null) {
            throw new CustomException(ErrorType.COMMON_INVALID_INPUT, "요청값이 비어있습니다.");
        }

        String startKeyword = safeTrim(request.startKeyword());
        String endKeyword = safeTrim(request.endKeyword());
        String theme = safeTrim(request.theme());

        if (startKeyword.isBlank() || endKeyword.isBlank() || theme.isBlank()) {
            throw new CustomException(ErrorType.COMMON_INVALID_INPUT, "필수 입력값이 비어있습니다.");
        }

        boolean openNowOnly = Boolean.TRUE.equals(request.openNowOnly());
        List<DrivePlanCandidate> candidates = collectCandidates(startKeyword, endKeyword);

        if (candidates.isEmpty()) {
            return buildFallbackPlan(startKeyword, endKeyword, openNowOnly, candidates);
        }

        List<DrivePlanCandidate> llmCandidates = candidates.stream()
            .limit(LLM_CANDIDATE_LIMIT)
            .toList();

        String candidatesJson = toJson(llmCandidates);
        if (candidatesJson.isBlank()) {
            return buildFallbackPlan(startKeyword, endKeyword, openNowOnly, candidates);
        }

        String prompt = buildPrompt(startKeyword, endKeyword, theme, openNowOnly, candidatesJson);

        Optional<DrivePlanLlmResponse> llmResponse = drivePlanLlmClient.requestPlan(prompt);
        Optional<DrivePlanResponse> planned = llmResponse
            .flatMap(response -> toPlanResponse(response, candidates, startKeyword, endKeyword));

        return planned.orElseGet(() -> buildFallbackPlan(startKeyword, endKeyword, openNowOnly, candidates));
    }

    private Optional<DrivePlanResponse> toPlanResponse(
        DrivePlanLlmResponse response,
        List<DrivePlanCandidate> candidates,
        String startKeyword,
        String endKeyword
    ) {
        if (response == null || response.stops() == null || response.stops().isEmpty()) {
            return Optional.empty();
        }

        Map<String, DrivePlanCandidate> candidateByKey = candidates.stream()
            .collect(Collectors.toMap(DrivePlanCandidate::key, candidate -> candidate, (a, b) -> a,
                LinkedHashMap::new));
        Map<String, DrivePlanCandidate> candidateByUrl = candidates.stream()
            .filter(candidate -> candidate.placeUrl() != null && !candidate.placeUrl().isBlank())
            .collect(Collectors.toMap(
                candidate -> normalize(candidate.placeUrl()),
                candidate -> candidate,
                (a, b) -> a,
                LinkedHashMap::new
            ));

        List<DrivePlanStopResponse> stops = response.stops().stream()
            .filter(Objects::nonNull)
            .sorted(Comparator.comparingInt(DrivePlanLlmStop::order))
            .map(stop -> toStopResponse(stop, candidateByKey, candidateByUrl))
            .filter(Objects::nonNull)
            .toList();

        if (stops.size() < FALLBACK_STOP_LIMIT) {
            return Optional.empty();
        }

        String courseName = defaultIfBlank(response.courseName(), buildCourseName(startKeyword, endKeyword));
        String planReason = defaultIfBlank(response.planReason(), DEFAULT_PLAN_REASON);

        return Optional.of(new DrivePlanResponse(courseName, planReason, stops));
    }

    private DrivePlanStopResponse toStopResponse(
        DrivePlanLlmStop stop,
        Map<String, DrivePlanCandidate> candidateByKey,
        Map<String, DrivePlanCandidate> candidateByUrl
    ) {
        if (stop == null) {
            return null;
        }

        DrivePlanCandidate candidate = null;
        if (stop.placeUrl() != null && !stop.placeUrl().isBlank()) {
            candidate = candidateByUrl.get(normalize(stop.placeUrl()));
        }
        if (candidate == null) {
            candidate = candidateByKey.get(normalizeKey(stop.name(), stop.address()));
        }
        if (candidate == null) {
            return null;
        }

        String reason = defaultIfBlank(stop.reason(), DEFAULT_STOP_REASON);

        return new DrivePlanStopResponse(
            stop.order(),
            candidate.name(),
            candidate.address(),
            candidate.lat(),
            candidate.lng(),
            candidate.placeUrl(),
            reason,
            stop.openNowEstimated()
        );
    }

    private List<DrivePlanCandidate> collectCandidates(String startKeyword, String endKeyword) {
        Map<String, DrivePlanCandidate> unique = new LinkedHashMap<>();
        appendCandidates(unique, fetchCandidates(startKeyword));
        appendCandidates(unique, fetchCandidates(endKeyword));
        return new ArrayList<>(unique.values());
    }

    private void appendCandidates(Map<String, DrivePlanCandidate> unique, List<KakaoPlaceDocument> documents) {
        if (documents == null || documents.isEmpty()) {
            return;
        }
        for (KakaoPlaceDocument document : documents) {
            DrivePlanCandidate candidate = toCandidate(document);
            if (candidate == null) {
                continue;
            }
            unique.putIfAbsent(candidate.key(), candidate);
        }
    }

    private List<KakaoPlaceDocument> fetchCandidates(String keyword) {
        List<KakaoPlaceDocument> results = new ArrayList<>();
        appendDocuments(results, kakaoLocalService.searchKeyword(keyword, 1, FIRST_PAGE_SIZE));
        if (results.size() < FIRST_PAGE_SIZE + SECOND_PAGE_SIZE) {
            appendDocuments(results, kakaoLocalService.searchKeyword(keyword, 2, SECOND_PAGE_SIZE));
        }
        return results;
    }

    private void appendDocuments(List<KakaoPlaceDocument> results, KakaoPlaceSearchResponse response) {
        if (response == null || response.documents() == null || response.documents().isEmpty()) {
            return;
        }
        results.addAll(response.documents());
    }

    private DrivePlanCandidate toCandidate(KakaoPlaceDocument document) {
        if (document == null) {
            return null;
        }

        String name = safeTrim(document.placeName());
        String address = resolveAddress(document);
        if (name.isBlank() || address.isBlank()) {
            return null;
        }

        Double lat = parseDouble(document.y());
        Double lng = parseDouble(document.x());
        if (lat == null || lng == null) {
            return null;
        }

        return new DrivePlanCandidate(
            safeTrim(document.id()),
            name,
            address,
            lat,
            lng,
            safeTrim(document.placeUrl()),
            safeTrim(document.categoryName()),
            safeTrim(document.categoryGroupName())
        );
    }

    private String resolveAddress(KakaoPlaceDocument document) {
        String roadAddress = safeTrim(document.roadAddressName());
        if (!roadAddress.isBlank()) {
            return roadAddress;
        }
        return safeTrim(document.addressName());
    }

    private DrivePlanResponse buildFallbackPlan(
        String startKeyword,
        String endKeyword,
        boolean openNowOnly,
        List<DrivePlanCandidate> candidates
    ) {
        List<DrivePlanStopResponse> stops = new ArrayList<>();
        int limit = Math.min(FALLBACK_STOP_LIMIT, candidates.size());
        for (int index = 0; index < limit; index++) {
            DrivePlanCandidate candidate = candidates.get(index);
            boolean openNowEstimated = openNowOnly && isLikelyOpen(candidate);
            stops.add(new DrivePlanStopResponse(
                index + 1,
                candidate.name(),
                candidate.address(),
                candidate.lat(),
                candidate.lng(),
                candidate.placeUrl(),
                DEFAULT_STOP_REASON,
                openNowEstimated
            ));
        }

        return new DrivePlanResponse(
            buildCourseName(startKeyword, endKeyword),
            DEFAULT_PLAN_REASON,
            stops
        );
    }

    private boolean isLikelyOpen(DrivePlanCandidate candidate) {
        if (candidate == null) {
            return false;
        }

        String combined = (safeTrim(candidate.categoryName()) + " " + safeTrim(candidate.categoryGroupName()))
            .toLowerCase(Locale.ROOT);
        return OPEN_NOW_KEYWORDS.stream().anyMatch(combined::contains);
    }

    private String buildPrompt(
        String startKeyword,
        String endKeyword,
        String theme,
        boolean openNowOnly,
        String candidatesJson
    ) {
        return PROMPT_TEMPLATE
            .replace("{startKeyword}", startKeyword)
            .replace("{endKeyword}", endKeyword)
            .replace("{theme}", theme)
            .replace("{openNowOnly}", String.valueOf(openNowOnly))
            .replace("{candidatesJson}", candidatesJson);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            log.warn("Failed to serialize candidates", ex);
            return "";
        }
    }

    private String defaultIfBlank(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private Double parseDouble(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String buildCourseName(String startKeyword, String endKeyword) {
        return String.format("%s → %s 드라이브 코스", startKeyword, endKeyword);
    }

    private static String normalizeKey(String name, String address) {
        String safeName = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        String safeAddress = address == null ? "" : address.trim().toLowerCase(Locale.ROOT);
        return safeName + "|" + safeAddress;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private record DrivePlanCandidate(
        String id,
        String name,
        String address,
        double lat,
        double lng,
        String placeUrl,
        String categoryName,
        String categoryGroupName
    ) {
        String key() {
            if (id != null && !id.isBlank()) {
                return id;
            }
            return normalizeKey(name, address);
        }
    }

    private static final String PROMPT_TEMPLATE = """
SYSTEM:
너는 대한민국 드라이브 코스 설계자 'Cruiser'다.
반드시 제공된 후보 장소 데이터만 사용한다.
존재하지 않는 정보(날씨/교통/주차/영업시간 등)는 추측하지 않는다.
최종 응답은 JSON만 출력한다. 설명 문장/마크다운 금지.

USER:
사용자 조건:
- 출발 키워드: {startKeyword}
- 도착 키워드: {endKeyword}
- 테마: {theme}
- openNowOnly(추정): {openNowOnly}

후보 장소 목록(JSON):
{candidatesJson}

작업:
- 후보 장소 중 5개를 선택해 드라이브 코스를 구성한다.
- 순서(order)를 1~5로 매긴다.
- courseName(짧은 제목), planReason(1줄), 각 stop.reason(1줄)을 작성한다.
- openNowOnly가 true면 "카페/식당/전망대" 등 일반적으로 지금 시간대에 운영 가능성이 높은 후보를 우선하되, 보장하지 말고 openNowEstimated로만 표시한다.

출력(JSON only):
{
  "courseName": "",
  "planReason": "",
  "stops": [
    {
      "order": 1,
      "name": "",
      "address": "",
      "lat": 0.0,
      "lng": 0.0,
      "placeUrl": "",
      "reason": "",
      "openNowEstimated": true
    }
  ]
}
""";
}
