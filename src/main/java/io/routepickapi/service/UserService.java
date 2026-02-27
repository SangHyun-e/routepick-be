package io.routepickapi.service;

import io.routepickapi.common.error.CustomException;
import io.routepickapi.common.error.ErrorType;
import io.routepickapi.dto.user.MeResponse;
import io.routepickapi.entity.user.User;
import io.routepickapi.entity.user.UserStatus;
import io.routepickapi.repository.UserRepository;
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
    public void withdraw(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new CustomException(ErrorType.USER_NOT_FOUND));

        if (user.getStatus() == UserStatus.BLOCKED) {
            throw new CustomException(ErrorType.USER_BLOCKED);
        }

        if (user.getStatus() == UserStatus.DELETED) {
            return;
        }

        String email = user.getEmail();
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

        if (!user.getNickname().equals(nickname) && userRepository.existsByNickname(nickname)) {
            throw new CustomException(ErrorType.USER_NICKNAME_EXISTS);
        }

        user.setNickname(nickname);
        user.markProfileComplete();

        return MeResponse.from(user);
    }

}
