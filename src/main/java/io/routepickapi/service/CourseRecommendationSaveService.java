package io.routepickapi.service;

import io.routepickapi.common.error.CustomException;
import io.routepickapi.common.error.ErrorType;
import io.routepickapi.dto.course.CourseRecommendationSaveRequest;
import io.routepickapi.dto.course.CourseRecommendationSaveResponse;
import io.routepickapi.dto.course.SavedCourseIncludeStopRequest;
import io.routepickapi.dto.course.SavedCourseStopRequest;
import io.routepickapi.entity.course.CourseRecommendationIncludeStop;
import io.routepickapi.entity.course.CourseRecommendationSave;
import io.routepickapi.entity.course.CourseRecommendationStop;
import io.routepickapi.entity.user.User;
import io.routepickapi.entity.user.UserStatus;
import io.routepickapi.repository.CourseRecommendationSaveRepository;
import io.routepickapi.repository.UserRepository;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CourseRecommendationSaveService {

    private final CourseRecommendationSaveRepository courseRecommendationSaveRepository;
    private final UserRepository userRepository;

    @Transactional
    public CourseRecommendationSaveResponse saveRecommendation(CourseRecommendationSaveRequest request,
        Long userId) {
        if (request == null) {
            throw new CustomException(ErrorType.COMMON_INVALID_INPUT, "요청값이 비어있습니다.");
        }

        User user = requireActiveUser(userId);

        List<CourseRecommendationStop> stops = request.selectedStops().stream()
            .map(this::toStop)
            .toList();
        List<CourseRecommendationIncludeStop> includeStops = request.includeStops() == null
            ? List.of()
            : request.includeStops().stream()
                .map(this::toIncludeStop)
                .toList();

        CourseRecommendationSave save = new CourseRecommendationSave(
            user,
            sanitizeTitle(request.title()),
            sanitizeTheme(request.theme()),
            request.originLat(),
            request.originLng(),
            request.destinationLat(),
            request.destinationLng(),
            request.durationMinutes(),
            request.maxStops(),
            request.totalDistanceKm(),
            request.totalDurationMinutes(),
            request.description(),
            request.explainText(),
            stops,
            includeStops
        );

        CourseRecommendationSave saved = courseRecommendationSaveRepository.save(save);
        return CourseRecommendationSaveResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public Page<CourseRecommendationSaveResponse> listSaved(Long userId, Pageable pageable) {
        User user = requireActiveUser(userId);

        return courseRecommendationSaveRepository.findByUserIdOrderByCreatedAtDesc(user.getId(),
            pageable).map(CourseRecommendationSaveResponse::from);
    }

    @Transactional
    public void deleteSaved(Long saveId, Long userId) {
        if (saveId == null) {
            throw new CustomException(ErrorType.COMMON_INVALID_INPUT, "삭제할 코스 ID가 필요합니다.");
        }

        User user = requireActiveUser(userId);

        CourseRecommendationSave saved = courseRecommendationSaveRepository
            .findByIdAndUserId(saveId, user.getId())
            .orElseThrow(() -> new CustomException(ErrorType.COMMON_NOT_FOUND,
                "저장된 추천 코스를 찾을 수 없습니다."));

        courseRecommendationSaveRepository.delete(saved);
    }

    private CourseRecommendationStop toStop(SavedCourseStopRequest stop) {
        return new CourseRecommendationStop(
            stop.name(),
            stop.lat(),
            stop.lng(),
            stop.type(),
            normalizeTags(stop.tags()),
            stop.stayMinutes(),
            stop.viewScore(),
            stop.driveSuitability(),
            stop.segmentDistanceKm(),
            stop.segmentDurationMinutes()
        );
    }

    private CourseRecommendationIncludeStop toIncludeStop(SavedCourseIncludeStopRequest stop) {
        return new CourseRecommendationIncludeStop(stop.name(), stop.lat(), stop.lng());
    }

    private String sanitizeTheme(String theme) {
        if (theme == null || theme.isBlank()) {
            return "서비스 추천";
        }
        String trimmed = theme.trim();
        return trimmed.length() > 30 ? trimmed.substring(0, 30) : trimmed;
    }

    private String sanitizeTitle(String title) {
        if (title == null || title.isBlank()) {
            return "저장된 코스";
        }
        String trimmed = title.trim();
        return trimmed.length() > 120 ? trimmed.substring(0, 120) : trimmed;
    }

    private List<String> normalizeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        return tags.stream()
            .filter(tag -> tag != null && !tag.isBlank())
            .map(String::trim)
            .collect(Collectors.toList());
    }

    private User requireActiveUser(Long userId) {
        if (userId == null) {
            throw new CustomException(ErrorType.COMMON_UNAUTHORIZED);
        }

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new CustomException(ErrorType.COMMON_UNAUTHORIZED));

        if (user.getStatus() == UserStatus.PENDING) {
            throw new CustomException(ErrorType.USER_EMAIL_NOT_VERIFIED);
        }
        if (user.getStatus() == UserStatus.BLOCKED) {
            throw new CustomException(ErrorType.USER_BLOCKED);
        }
        if (user.getStatus() == UserStatus.DELETED) {
            throw new CustomException(ErrorType.USER_NOT_FOUND);
        }

        return user;
    }
}
