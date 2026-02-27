package io.routepickapi.service;

import io.routepickapi.common.error.CustomException;
import io.routepickapi.common.error.ErrorType;
import io.routepickapi.dto.user.AdminUserDetailResponse;
import io.routepickapi.dto.user.AdminUserListItemResponse;
import io.routepickapi.dto.user.AdminUserStatusHistoryResponse;
import io.routepickapi.entity.user.User;
import io.routepickapi.entity.user.UserStatus;
import io.routepickapi.entity.user.UserStatusHistory;
import io.routepickapi.repository.UserRepository;
import io.routepickapi.repository.UserStatusHistoryRepository;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminUserService {

    private final UserRepository userRepository;
    private final UserStatusHistoryRepository historyRepository;
    private final RefreshTokenService refreshTokenService;
    private final UserRejoinRestrictionService rejoinRestrictionService;

    @Transactional(readOnly = true)
    public Page<AdminUserListItemResponse> list(String keyword, Pageable pageable) {
        String trimmed = keyword != null ? keyword.trim() : "";
        Page<User> users = userRepository.searchByKeyword(trimmed, pageable);
        return users.map(AdminUserListItemResponse::from);
    }

    @Transactional(readOnly = true)
    public AdminUserDetailResponse getDetail(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new CustomException(ErrorType.USER_NOT_FOUND));
        return AdminUserDetailResponse.from(user);
    }

    public void updateStatus(Long userId, UserStatus targetStatus, String reason,
        Long adminUserId) {
        validateStatusRequest(targetStatus);
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new CustomException(ErrorType.USER_NOT_FOUND));

        UserStatus currentStatus = user.getStatus();

        if (currentStatus == UserStatus.DELETED) {
            throw new CustomException(ErrorType.USER_STATUS_CHANGE_NOT_ALLOWED,
                "DELETED 상태는 복구할 수 없습니다.");
        }

        if (!isAllowedTransition(currentStatus, targetStatus)) {
            throw new CustomException(ErrorType.USER_STATUS_CHANGE_NOT_ALLOWED,
                "상태 변경이 허용되지 않습니다.");
        }

        if (currentStatus == targetStatus) {
            return;
        }

        applyStatus(user, targetStatus);

        historyRepository.save(new UserStatusHistory(
            user.getId(),
            currentStatus,
            targetStatus,
            reason,
            adminUserId
        ));

        if (targetStatus == UserStatus.BLOCKED || targetStatus == UserStatus.DELETED) {
            refreshTokenService.deleteAllForUser(user.getId());
        }
    }

    @Transactional(readOnly = true)
    public Page<AdminUserStatusHistoryResponse> getStatusHistory(Long userId, Pageable pageable) {
        if (!userRepository.existsById(userId)) {
            throw new CustomException(ErrorType.USER_NOT_FOUND);
        }
        return historyRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
            .map(AdminUserStatusHistoryResponse::from);
    }

    public void releaseRejoinRestriction(Long userId, Long adminUserId, String reason) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new CustomException(ErrorType.USER_NOT_FOUND));

        if (user.getStatus() != UserStatus.DELETED) {
            throw new CustomException(ErrorType.USER_STATUS_CHANGE_NOT_ALLOWED,
                "삭제된 사용자만 제한 해제가 가능합니다.");
        }

        if (user.getRejoinRestrictedUntil() == null
            || user.getRejoinRestrictionReleasedAt() != null) {
            return;
        }

        rejoinRestrictionService.releaseRestriction(user, adminUserId, reason);
    }

    public void releaseRejoinRestrictionByEmail(String email, Long adminUserId, String reason) {
        if (email == null || email.isBlank()) {
            throw new CustomException(ErrorType.COMMON_INVALID_INPUT, "email 값이 필요합니다.");
        }

        String emailHash = rejoinRestrictionService.toEmailHash(email);
        List<User> users = userRepository
            .findAllByDeletedEmailHashAndStatusAndRejoinRestrictionReleasedAtIsNull(
                emailHash,
                UserStatus.DELETED
            );

        if (users.isEmpty()) {
            throw new CustomException(ErrorType.USER_NOT_FOUND, "해당 이메일의 탈퇴 기록이 없습니다.");
        }

        for (User user : users) {
            rejoinRestrictionService.releaseRestriction(user, adminUserId, reason);
        }
    }

    private void validateStatusRequest(UserStatus status) {
        if (status == null) {
            throw new CustomException(ErrorType.COMMON_INVALID_INPUT, "status 값이 필요합니다.");
        }
        if (status == UserStatus.PENDING) {
            throw new CustomException(ErrorType.COMMON_INVALID_INPUT,
                "PENDING 상태로는 변경할 수 없습니다.");
        }
    }

    private boolean isAllowedTransition(UserStatus from, UserStatus to) {
        if (from == null || to == null) {
            return false;
        }
        if (from == UserStatus.PENDING && to == UserStatus.ACTIVE) {
            return true;
        }
        if (from == UserStatus.PENDING && to == UserStatus.DELETED) {
            return true;
        }
        if (from == UserStatus.ACTIVE && to == UserStatus.BLOCKED) {
            return true;
        }
        if (from == UserStatus.BLOCKED && to == UserStatus.ACTIVE) {
            return true;
        }
        if ((from == UserStatus.ACTIVE || from == UserStatus.BLOCKED)
            && to == UserStatus.DELETED) {
            return true;
        }
        return false;
    }

    private void applyStatus(User user, UserStatus status) {
        switch (Objects.requireNonNull(status)) {
            case ACTIVE -> user.activate();
            case BLOCKED -> user.block();
            case DELETED -> {
                String email = user.getEmail();
                user.delete();
                rejoinRestrictionService.applyRestriction(user, email);
            }
            case PENDING -> throw new CustomException(ErrorType.COMMON_INVALID_INPUT,
                "PENDING 상태로는 변경할 수 없습니다.");
        }
    }
}
