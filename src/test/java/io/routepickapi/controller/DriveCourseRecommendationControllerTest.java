package io.routepickapi.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.routepickapi.dto.recommendation.CourseStopResponse;
import io.routepickapi.dto.recommendation.CourseSummaryResponse;
import io.routepickapi.dto.recommendation.RecommendationResponse;
import io.routepickapi.dto.recommendation.ScoreBreakdownResponse;
import io.routepickapi.mapper.recommendation.RecommendationResponseMapper;
import io.routepickapi.security.JwtAccessDeniedHandler;
import io.routepickapi.security.JwtAuthenticationEntryPoint;
import io.routepickapi.security.MdcUserFilter;
import io.routepickapi.security.jwt.JwtAuthenticationFilter;
import io.routepickapi.service.recommendation.pipeline.DriveCourseCommand;
import io.routepickapi.service.recommendation.pipeline.DriveCourseResult;
import io.routepickapi.service.recommendation.pipeline.RecommendationFacade;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DriveCourseRecommendationController.class)
@AutoConfigureMockMvc(addFilters = false)
@SuppressWarnings("removal")
class DriveCourseRecommendationControllerTest {

    private static final String BASE_URL = "/api/recommendations/drive-courses";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RecommendationFacade recommendationFacade;

    @MockBean
    private RecommendationResponseMapper recommendationResponseMapper;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private MdcUserFilter mdcUserFilter;

    @MockBean
    private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @MockBean
    private JwtAccessDeniedHandler jwtAccessDeniedHandler;

    @Test
    void driveCourseRecommendation_returnsOk_forValidRequest() throws Exception {
        LocalDateTime departure = LocalDateTime.now().plusDays(1).withNano(0);
        LocalDateTime generated = departure.minusSeconds(2);
        DriveCourseResult result = new DriveCourseResult(
            "req-20241001-001",
            37.5665,
            126.9780,
            departure,
            List.of(),
            List.of(),
            generated
        );
        RecommendationResponse response = sampleResponse(departure, generated);

        when(recommendationFacade.recommend(any(DriveCourseCommand.class))).thenReturn(result);
        when(recommendationResponseMapper.map(result)).thenReturn(response);

        mockMvc.perform(get(BASE_URL)
                .param("originLat", "37.5665")
                .param("originLng", "126.9780")
                .param("destinationLat", "37.45")
                .param("destinationLng", "127.02")
                .param("theme", "coastal")
                .param("durationMinutes", "180")
                .param("departureTime", departure.toString())
                .param("maxStops", "3")
                .param("weatherAware", "true"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.requestId").value("req-20241001-001"))
            .andExpect(jsonPath("$.originLat").value(37.5665))
            .andExpect(jsonPath("$.originLng").value(126.9780))
            .andExpect(jsonPath("$.departureTime").value(departure.toString()))
            .andExpect(jsonPath("$.generatedAt").value(generated.toString()))
            .andExpect(jsonPath("$.courses[0].theme").value("coastal"))
            .andExpect(jsonPath("$.courses[0].scoreBreakdown.totalScore").value(72.0))
            .andExpect(jsonPath("$.courses[0].stops[0].name").value("북한산 전망대"));

        ArgumentCaptor<DriveCourseCommand> captor = ArgumentCaptor.forClass(DriveCourseCommand.class);
        verify(recommendationFacade).recommend(captor.capture());
        verify(recommendationResponseMapper).map(result);
        verifyNoMoreInteractions(recommendationFacade, recommendationResponseMapper);

        DriveCourseCommand command = captor.getValue();
        assertThat(command.originLat()).isEqualTo(37.5665);
        assertThat(command.originLng()).isEqualTo(126.9780);
        assertThat(command.destinationLat()).isEqualTo(37.45);
        assertThat(command.destinationLng()).isEqualTo(127.02);
        assertThat(command.theme()).isEqualTo("coastal");
        assertThat(command.durationMinutes()).isEqualTo(180);
        assertThat(command.maxStops()).isEqualTo(3);
        assertThat(command.departureTime()).isEqualTo(departure);
        assertThat(command.weatherAware()).isTrue();
    }

    @Test
    void driveCourseRecommendation_returnsBadRequest_forInvalidOriginLat() throws Exception {
        mockMvc.perform(get(BASE_URL)
                .param("originLat", "120")
                .param("originLng", "126.9780")
                .param("destinationLat", "37.45")
                .param("destinationLng", "127.02"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("CMN-001"))
            .andExpect(jsonPath("$.errors[0].field").value("originLat"));

        verifyNoInteractions(recommendationFacade, recommendationResponseMapper);
    }

    @Test
    void driveCourseRecommendation_returnsBadRequest_forInvalidOriginLng() throws Exception {
        mockMvc.perform(get(BASE_URL)
                .param("originLat", "37.5665")
                .param("originLng", "220")
                .param("destinationLat", "37.45")
                .param("destinationLng", "127.02"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("CMN-001"))
            .andExpect(jsonPath("$.errors[0].field").value("originLng"));

        verifyNoInteractions(recommendationFacade, recommendationResponseMapper);
    }

    @Test
    void driveCourseRecommendation_returnsBadRequest_forInvalidDurationMinutes() throws Exception {
        mockMvc.perform(get(BASE_URL)
                .param("originLat", "37.5665")
                .param("originLng", "126.9780")
                .param("destinationLat", "37.45")
                .param("destinationLng", "127.02")
                .param("durationMinutes", "10"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.code").value("CMN-001"))
            .andExpect(jsonPath("$.message").value("유효하지 않은 입력입니다."))
            .andExpect(jsonPath("$.path").value(BASE_URL))
            .andExpect(jsonPath("$.errors[0].field").value("durationMinutes"))
            .andExpect(jsonPath("$.errors[0].reason").value("durationMinutes는 30 이상이어야 합니다."))
            .andExpect(jsonPath("$.timestamp").exists());

        verifyNoInteractions(recommendationFacade, recommendationResponseMapper);
    }

    @Test
    void driveCourseRecommendation_returnsBadRequest_forInvalidMaxStops() throws Exception {
        mockMvc.perform(get(BASE_URL)
                .param("originLat", "37.5665")
                .param("originLng", "126.9780")
                .param("destinationLat", "37.45")
                .param("destinationLng", "127.02")
                .param("maxStops", "5"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("CMN-001"))
            .andExpect(jsonPath("$.errors[0].field").value("maxStops"))
            .andExpect(jsonPath("$.errors[0].reason").value("maxStops는 4 이하이어야 합니다."));

        verifyNoInteractions(recommendationFacade, recommendationResponseMapper);
    }

    private RecommendationResponse sampleResponse(LocalDateTime departure, LocalDateTime generated) {
        ScoreBreakdownResponse breakdown = new ScoreBreakdownResponse(
            32.0,
            20.0,
            12.0,
            8.0,
            0.0,
            72.0,
            List.of()
        );
        CourseStopResponse stop = new CourseStopResponse(
            0,
            "북한산 전망대",
            37.658,
            126.974,
            "전망대",
            List.of("전망대", "mountain", "osm"),
            60,
            0.9,
            0.7,
            0.0,
            0
        );
        CourseSummaryResponse course = new CourseSummaryResponse(
            null,
            "서울특별시 중구",
            "coastal",
            "야경 드라이브 추천 코스",
            "북한산 전망대을(를) 들러 이동합니다.",
            78.4,
            210,
            72.0,
            breakdown,
            List.of(stop)
        );

        return new RecommendationResponse(
            "req-20241001-001",
            37.5665,
            126.9780,
            departure,
            List.of(course),
            List.of(),
            generated
        );
    }
}
