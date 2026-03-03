package io.routepickapi.service;

import io.routepickapi.common.error.CustomException;
import io.routepickapi.common.error.ErrorType;
import io.routepickapi.dto.course.CourseRecommendationSaveRequest;
import io.routepickapi.dto.course.CourseRecommendationSaveResponse;
import io.routepickapi.dto.course.CourseStopRequest;
import io.routepickapi.dto.course.CourseTheme;
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

        CourseTheme.from(request.theme());

        User user = requireActiveUser(userId);

        List<CourseRecommendationStop> stops = request.stops().stream()
            .map(this::toStop)
            .toList();

        CourseRecommendationSave save = new CourseRecommendationSave(
            user,
            request.origin(),
            request.destination(),
            request.theme(),
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

    private CourseRecommendationStop toStop(CourseStopRequest stop) {
        return new CourseRecommendationStop(
            stop.name(),
            stop.address(),
            stop.x(),
            stop.y(),
            stop.category()
        );
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
