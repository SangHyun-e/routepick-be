package io.routepickapi.service;

import io.routepickapi.common.error.CustomException;
import io.routepickapi.common.error.ErrorType;
import io.routepickapi.dto.course.CourseRecommendationRequest;
import io.routepickapi.dto.course.CourseRecommendationResponse;
import io.routepickapi.dto.course.CourseStopResponse;
import io.routepickapi.dto.course.CourseTheme;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CourseRecommendationService {

    private static final int DEFAULT_MAX_STOPS = 3;

    private static final List<CuratedCourse> CURATED_COURSES = List.of(
        new CuratedCourse(
            CourseTheme.NIGHT_VIEW,
            List.of("서울", "경기", "인천"),
            List.of(
                stop("N서울타워", "서울 중구 남산공원길 105", 126.9882, 37.5512, "전망대"),
                stop("반포한강공원", "서울 서초구 신반포로11길 40", 126.9957, 37.5080, "공원"),
                stop("남산공원", "서울 중구 삼일대로 231", 126.9896, 37.5509, "공원")
            ),
            "도심 야경을 가장 잘 느낄 수 있는 루트를 모았습니다."
        ),
        new CuratedCourse(
            CourseTheme.SEA,
            List.of("강원", "강릉", "속초", "양양", "동해"),
            List.of(
                stop("안목해변", "강원 강릉시 창해로14번길 20", 128.9416, 37.7719, "해변"),
                stop("속초해변", "강원 속초시 해오름로 186", 128.6035, 38.1903, "해변"),
                stop("주문진항", "강원 강릉시 주문진읍 해안로 1750", 128.8237, 37.8903, "항구")
            ),
            "동해안 특유의 탁 트인 바다와 어항 분위기를 함께 즐길 수 있어요."
        ),
        new CuratedCourse(
            CourseTheme.SEA,
            List.of("부산", "거제", "통영", "남해", "경남"),
            List.of(
                stop("해운대해수욕장", "부산 해운대구 해운대해변로 264", 129.1580, 35.1587, "해변"),
                stop("광안리해수욕장", "부산 수영구 광안해변로 219", 129.1180, 35.1532, "해변"),
                stop("이순신공원", "경남 통영시 정량동 600", 128.4307, 34.8447, "공원")
            ),
            "남해안의 드라이브 포인트를 묶은 코스입니다."
        ),
        new CuratedCourse(
            CourseTheme.MOUNTAIN,
            List.of("서울", "경기", "인천"),
            List.of(
                stop("북한산국립공원", "서울 강북구 우이동", 126.9980, 37.6583, "국립공원"),
                stop("남한산성", "경기 광주시 남한산성면 산성리", 127.2107, 37.4783, "문화유산"),
                stop("아차산", "서울 광진구 구의동", 127.1039, 37.5543, "산")
            ),
            "도심 근교에서 산과 전망을 함께 즐길 수 있는 코스입니다."
        ),
        new CuratedCourse(
            CourseTheme.MOUNTAIN,
            List.of("강원", "평창", "설악", "오대산"),
            List.of(
                stop("설악산국립공원", "강원 속초시 설악산로 1091", 128.5167, 38.1196, "국립공원"),
                stop("오대산국립공원", "강원 평창군 진부면 오대산로 374", 128.5436, 37.7913, "국립공원"),
                stop("대관령 양떼목장", "강원 평창군 대관령면 대관령마루길 483-32", 128.7183, 37.6885, "목장")
            ),
            "강원권 산악 드라이브의 풍경 포인트를 모아두었습니다."
        ),
        new CuratedCourse(
            CourseTheme.CAFE,
            List.of("서울", "경기", "인천"),
            List.of(
                stop("성수 카페거리", "서울 성동구 성수동2가", 127.0558, 37.5446, "카페 거리"),
                stop("연남동 카페거리", "서울 마포구 연남동", 126.9223, 37.5641, "카페 거리"),
                stop("헤이리 예술마을", "경기 파주시 탄현면 헤이리마을길 70", 126.7009, 37.7892, "문화마을")
            ),
            "카페와 감성 스폿 위주로 쉬엄쉬엄 이동하기 좋은 루트입니다."
        ),
        new CuratedCourse(
            CourseTheme.CAFE,
            List.of("강릉", "양양", "제주", "부산", "강원"),
            List.of(
                stop("안목 카페거리", "강원 강릉시 창해로14번길", 128.9442, 37.7733, "카페 거리"),
                stop("애월 카페거리", "제주 제주시 애월읍 애월북서길", 126.3118, 33.4624, "카페 거리"),
                stop("흰여울문화마을", "부산 영도구 영선동4가 1042", 129.0456, 35.0783, "문화마을")
            ),
            "바다와 함께하는 카페 드라이브를 좋아하는 분들에게 추천합니다."
        ),
        new CuratedCourse(
            CourseTheme.FOOD,
            List.of("서울", "경기", "인천"),
            List.of(
                stop("광장시장", "서울 종로구 창경궁로 88", 126.9996, 37.5700, "시장"),
                stop("노량진수산시장", "서울 동작구 노들로 674", 126.9427, 37.5133, "시장"),
                stop("을지로 골목", "서울 중구 을지로", 126.9914, 37.5662, "먹자골목")
            ),
            "서울 도심에서 맛집과 시장을 함께 둘러보기 좋은 코스입니다."
        ),
        new CuratedCourse(
            CourseTheme.FOOD,
            List.of("부산", "경상", "거제", "통영"),
            List.of(
                stop("자갈치시장", "부산 중구 자갈치해안로 52", 129.0341, 35.0962, "시장"),
                stop("국제시장", "부산 중구 중구로 36", 129.0320, 35.1020, "시장"),
                stop("통영 중앙시장", "경남 통영시 중앙시장1길 12", 128.4313, 34.8458, "시장")
            ),
            "남부권 대표 먹거리 동선을 담은 코스입니다."
        ),
        new CuratedCourse(
            CourseTheme.WINDING,
            List.of("강원", "인제", "홍천", "양평", "가평"),
            List.of(
                stop("미시령옛길 전망대", "강원 고성군 토성면 미시령옛길", 128.4777, 38.2220, "전망대"),
                stop("구룡령 옛길", "강원 홍천군 서석면 구룡령로", 128.3522, 37.9406, "고개"),
                stop("내린천", "강원 인제군 인제읍", 128.1905, 38.0651, "계곡")
            ),
            "커브가 이어지는 산길과 계곡 뷰를 함께 즐길 수 있는 와인딩 코스입니다."
        ),
        new CuratedCourse(
            CourseTheme.COASTAL,
            List.of("강원", "강릉", "양양", "속초", "동해"),
            List.of(
                stop("경포해변", "강원 강릉시 창해로 514", 128.9086, 37.8044, "해변"),
                stop("정동진 해안", "강원 강릉시 강동면 정동진리", 129.0322, 37.6894, "해안"),
                stop("하조대 전망대", "강원 양양군 현북면 하조대해안길 119", 128.7240, 38.0270, "전망대")
            ),
            "바다 바로 옆을 달리는 동해안 해안길 드라이브 코스입니다."
        ),
        new CuratedCourse(
            CourseTheme.COASTAL,
            List.of("부산", "울산", "남해", "거제", "통영"),
            List.of(
                stop("송정해수욕장", "부산 해운대구 송정해변로 62", 129.2077, 35.1783, "해변"),
                stop("이기대 해안산책로", "부산 남구 용호동", 129.1041, 35.1152, "해안"),
                stop("울산 대왕암공원", "울산 동구 등대로 95", 129.4451, 35.4943, "공원")
            ),
            "남해안의 해안도로와 전망 포인트를 이어주는 코스입니다."
        )
    );

    public CourseRecommendationResponse recommend(CourseRecommendationRequest request) {
        if (request == null) {
            throw new CustomException(ErrorType.COMMON_INVALID_INPUT, "요청값이 비어있습니다.");
        }

        CourseTheme theme = CourseTheme.from(request.theme());
        int maxStops = sanitizeMaxStops(request.maxStops());

        CuratedCourse course = selectCourse(theme, request.origin(), request.destination());
        List<CourseStopResponse> stops = course.stops().stream()
            .limit(maxStops)
            .toList();

        String routeSummary = buildRouteSummary(request.origin(), request.destination(), stops);
        String explanation = buildExplanation(theme, course, stops);

        log.info("추천 코스 생성 완료 - theme={}, stops={}", theme.label(), stops.size());
        return new CourseRecommendationResponse(stops, routeSummary, explanation);
    }

    private CuratedCourse selectCourse(CourseTheme theme, String origin, String destination) {
        List<CuratedCourse> themed = CURATED_COURSES.stream()
            .filter(course -> course.theme() == theme)
            .toList();

        if (themed.isEmpty()) {
            throw new CustomException(ErrorType.COMMON_NOT_FOUND, "추천 코스를 찾을 수 없습니다.");
        }

        String query = normalizeQuery(origin + " " + destination);
        for (CuratedCourse course : themed) {
            if (matchesRegion(course, query)) {
                return course;
            }
        }

        return themed.getFirst();
    }

    private boolean matchesRegion(CuratedCourse course, String query) {
        if (query.isBlank()) {
            return false;
        }
        return course.regionKeywords().stream()
            .map(this::normalizeQuery)
            .anyMatch(query::contains);
    }

    private String buildRouteSummary(
        String origin,
        String destination,
        List<CourseStopResponse> stops
    ) {
        String stopNames = stops.stream()
            .map(CourseStopResponse::name)
            .collect(Collectors.joining(" → "));

        if (stopNames.isBlank()) {
            return String.format("%s → %s", origin, destination);
        }

        return String.format("%s → %s → %s", origin, stopNames, destination);
    }

    private String buildExplanation(
        CourseTheme theme,
        CuratedCourse course,
        List<CourseStopResponse> stops
    ) {
        List<String> lines = new ArrayList<>();
        if (course.description() != null && !course.description().isBlank()) {
            lines.add(course.description());
        }

        if (!stops.isEmpty()) {
            String stopNames = stops.stream()
                .map(CourseStopResponse::name)
                .collect(Collectors.joining(", "));
            lines.add(String.format("%s 테마에 맞춘 추천 경유지는 %s입니다.", theme.label(), stopNames));
        }

        lines.add("출발지와 도착지에 맞춰 경유지 순서는 상황에 따라 조정해보세요.");
        return String.join("\n", lines);
    }

    private int sanitizeMaxStops(Integer maxStops) {
        int requested = maxStops == null ? DEFAULT_MAX_STOPS : maxStops;
        return Math.min(DEFAULT_MAX_STOPS, Math.max(1, requested));
    }

    private String normalizeQuery(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT).replace(" ", "");
    }

    private static CourseStopResponse stop(
        String name,
        String address,
        double x,
        double y,
        String category
    ) {
        return new CourseStopResponse(name, address, x, y, category);
    }

    private record CuratedCourse(
        CourseTheme theme,
        List<String> regionKeywords,
        List<CourseStopResponse> stops,
        String description
    ) {
    }
}
