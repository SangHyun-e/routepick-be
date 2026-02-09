package io.routepickapi.service;

import io.routepickapi.entity.user.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LogEmailSender implements EmailSender {

    @Override
    public void sendVerificationCode(User user, String code, long ttlSeconds) {
        log.info("[EMAIL_VERIFY] code issued: userId={}, email={}, code={}, ttlSec={}",
            user.getId(), user.getEmail(), code, ttlSeconds);
    }
}
