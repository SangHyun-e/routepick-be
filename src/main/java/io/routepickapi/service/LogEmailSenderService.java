package io.routepickapi.service;

import io.routepickapi.entity.user.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile({"dev", "test"})
public class LogEmailSenderService implements EmailSenderService {

    @Override
    public void sendVerificationCode(User user, String code, long ttlSeconds) {
        log.info("[EMAIL_VERIFY] code issued: userId={}, email={}, code={}, ttlSec={}",
            user.getId(), user.getEmail(), code, ttlSeconds);
    }

    @Override
    public void sendPasswordResetCode(User user, String code, long ttlSeconds) {
        log.info("[PASSWORD_RESET] code issued: userId={}, email={}, code={}, ttlSec={}",
            user.getId(), user.getEmail(), code, ttlSeconds);
    }
}
