package io.routepickapi.service;

import io.routepickapi.entity.user.User;

public interface EmailSender {

    void sendVerificationCode(User user, String code, long ttlSeconds);
}
