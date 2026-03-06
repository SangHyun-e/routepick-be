package io.routepickapi.service;

import io.routepickapi.common.error.CustomException;
import io.routepickapi.common.error.ErrorType;
import io.routepickapi.dto.user.MeResponse;
import io.routepickapi.entity.user.User;
import io.routepickapi.entity.user.UserAuthProvider;
import io.routepickapi.entity.user.UserStatus;
import io.routepickapi.repository.UserRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserRejoinRestrictionService rejoinRestrictionService;
    private final RefreshTokenService refreshTokenService;

    public MeResponse getMe(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new CustomException(ErrorType.USER_NOT_FOUND));

        if (user.getStatus() == UserStatus.BLOCKED) {
            throw new CustomException(ErrorType.USER_BLOCKED);
        }

        if (user.getStatus() == UserStatus.DELETED) {
            throw new CustomException(ErrorType.USER_NOT_FOUND);
        }
        return MeResponse.from(user);
    }

    @Transactional
    public void withdraw(Long userId, String reason) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new CustomException(ErrorType.USER_NOT_FOUND));

        if (user.getStatus() == UserStatus.BLOCKED) {
            throw new CustomException(ErrorType.USER_BLOCKED);
        }

        if (user.getStatus() == UserStatus.DELETED) {
            return;
        }

        String email = user.getEmail();
        user.setWithdrawReason(reason);
        user.delete();
        rejoinRestrictionService.applyRestriction(user, email);
    }

    public void verifyPassword(Long userId, String rawPassword) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new CustomException(ErrorType.USER_NOT_FOUND));

        if (user.getStatus() == UserStatus.BLOCKED) {
            throw new CustomException(ErrorType.USER_BLOCKED);
        }

        if (user.getStatus() == UserStatus.DELETED) {
            throw new CustomException(ErrorType.USER_NOT_FOUND);
        }

        if (user.getPasswordHash() == null
            || !passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new CustomException(ErrorType.AUTH_INVALID_CREDENTIALS, "비밀번호가 올바르지 않습니다.");
        }
    }

    @Transactional
    public MeResponse updateNickname(Long userId, String nickname) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new CustomException(ErrorType.USER_NOT_FOUND));

        if (user.getStatus() == UserStatus.BLOCKED) {
            throw new CustomException(ErrorType.USER_BLOCKED);
        }

        if (user.getStatus() == UserStatus.DELETED) {
            throw new CustomException(ErrorType.USER_NOT_FOUND);
        }

        if (!user.getNickname().equals(nickname)) {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime lastUpdatedAt = user.getNicknameUpdatedAt();
            if (lastUpdatedAt != null && lastUpdatedAt.plusDays(7).isAfter(now)) {
                throw new CustomException(ErrorType.USER_NICKNAME_CHANGE_LIMIT);
            }
            if (userRepository.existsByNickname(nickname)) {
                throw new CustomException(ErrorType.USER_NICKNAME_EXISTS);
            }
            user.updateNickname(nickname, now, null);
        }
        user.markProfileComplete();

        return MeResponse.from(user);
    }

    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword,
        String confirmPassword) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new CustomException(ErrorType.USER_NOT_FOUND));

        if (user.getStatus() == UserStatus.BLOCKED) {
            throw new CustomException(ErrorType.USER_BLOCKED);
        }

        if (user.getStatus() == UserStatus.DELETED) {
            throw new CustomException(ErrorType.USER_NOT_FOUND);
        }

        if (user.getAuthProvider() != UserAuthProvider.LOCAL || user.getPasswordHash() == null) {
            throw new CustomException(ErrorType.USER_PASSWORD_NOT_SET);
        }

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new CustomException(ErrorType.AUTH_INVALID_CREDENTIALS, "비밀번호가 올바르지 않습니다.");
        }

        if (!newPassword.equals(confirmPassword)) {
            throw new CustomException(ErrorType.COMMON_INVALID_INPUT, "새 비밀번호가 일치하지 않습니다.");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        refreshTokenService.deleteAllForUser(userId);
    }

}
