package io.routepickapi.service;

import io.routepickapi.common.error.CustomException;
import io.routepickapi.common.error.ErrorType;
import io.routepickapi.dto.course.CourseRecommendationSaveRequest;
import io.routepickapi.dto.course.CourseRecommendationSaveResponse;
import io.routepickapi.dto.course.CourseStopRequest;
import io.routepickapi.entity.course.CourseRecommendationSave;
import io.routepickapi.entity.course.CourseRecommendationStop;
import io.routepickapi.entity.user.User;
import io.routepickapi.entity.user.UserStatus;
import io.routepickapi.repository.CourseRecommendationSaveRepository;
import io.routepickapi.repository.UserRepository;
import java.util.List;
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

        List<CourseRecommendationStop> stops = request.stops().stream()
            .map(this::toStop)
            .toList();

        CourseRecommendationSave save = new CourseRecommendationSave(
            user,
            request.origin(),
            request.destination(),
            sanitizeTheme(request.theme()),
            request.totalDurationMinutes(),
            request.routeSummary(),
            request.explanation(),
            stops
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

    private CourseRecommendationStop toStop(CourseStopRequest stop) {
        return new CourseRecommendationStop(
            stop.name(),
            stop.address(),
            stop.x(),
            stop.y(),
            stop.category()
        );
    }

    private String sanitizeTheme(String theme) {
        if (theme == null || theme.isBlank()) {
            return "서비스 추천";
        }
        String trimmed = theme.trim();
        return trimmed.length() > 20 ? trimmed.substring(0, 20) : trimmed;
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
