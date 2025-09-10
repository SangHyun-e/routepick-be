package io.routepickapi.service;

import io.routepickapi.common.error.CustomException;
import io.routepickapi.common.error.ErrorType;
import io.routepickapi.dto.user.MeResponse;
import io.routepickapi.entity.user.User;
import io.routepickapi.entity.user.UserStatus;
import io.routepickapi.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    public MeResponse getMe(Long userId) {
        User user = userRepository.findByIdAndStatus(userId, UserStatus.ACTIVE)
            .orElseThrow(() -> new CustomException(ErrorType.USER_NOT_FOUND));
        return MeResponse.from(user);
    }

}
